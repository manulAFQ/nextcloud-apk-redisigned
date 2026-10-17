package ru.manul.nextcloudgallery.data.autoupload

import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import ru.manul.nextcloudgallery.core.model.AutoUploadPolicy
import ru.manul.nextcloudgallery.core.model.LocalMediaCandidate
import ru.manul.nextcloudgallery.core.model.LocalMediaKind
import ru.manul.nextcloudgallery.core.model.RemotePath
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.local.AutoUploadPolicyStore
import ru.manul.nextcloudgallery.data.local.AutoUploadRegistry
import ru.manul.nextcloudgallery.data.local.LocalMediaSource
import ru.manul.nextcloudgallery.data.local.SessionStore
import ru.manul.nextcloudgallery.domain.repository.UploadQueueRepository

sealed interface AutoUploadRunDecision {
    data class Success(val discovered: Int, val enqueued: Int) : AutoUploadRunDecision
    data object Disabled : AutoUploadRunDecision
    data class Retry(val failure: AppFailure) : AutoUploadRunDecision
    data class Failure(val failure: AppFailure) : AutoUploadRunDecision
}

class AutoUploadRunner(
    private val sessionStore: SessionStore,
    private val policyStore: AutoUploadPolicyStore,
    private val registry: AutoUploadRegistry,
    private val mediaSource: LocalMediaSource,
    private val environment: AutoUploadEnvironment,
    private val uploads: UploadQueueRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(expectedAccountId: String): AutoUploadRunDecision {
        val session = when (val loaded = sessionStore.load()) {
            is AppResult.Failure -> return loaded.error.toDecision()
            is AppResult.Success -> loaded.value
        } ?: return AutoUploadRunDecision.Failure(AppFailure.Unauthorized)
        if (session.account.id != expectedAccountId) return AutoUploadRunDecision.Failure(AppFailure.Forbidden)
        val policy = when (val loaded = policyStore.get(expectedAccountId)) {
            is AppResult.Failure -> return loaded.error.toDecision()
            is AppResult.Success -> loaded.value
        } ?: return AutoUploadRunDecision.Disabled
        if (!policy.enabled) return AutoUploadRunDecision.Disabled
        policy.blockedBy(environment.current())?.let { return AutoUploadRunDecision.Retry(it) }

        when (val cleared = registry.clearStaleClaims(expectedAccountId, clock() - STALE_CLAIM_MILLIS)) {
            is AppResult.Failure -> return cleared.error.toDecision()
            is AppResult.Success -> Unit
        }
        val candidates = when (val scanned = mediaSource.scan(policy.includeVideos)) {
            is AppResult.Failure -> return scanned.error.toDecision()
            is AppResult.Success -> scanned.value.filter { policy.includes(it) }
        }
        var enqueued = 0
        for (candidate in candidates) {
            val claimed = when (val result = registry.claim(expectedAccountId, candidate, clock())) {
                is AppResult.Failure -> return result.error.toDecision()
                is AppResult.Success -> result.value
            }
            if (!claimed) continue
            val remotePath = remotePath(policy, candidate)
            if (remotePath == null) {
                registry.releaseClaim(expectedAccountId, candidate.localKey)
                continue
            }
            val jobId = UUID.randomUUID().toString()
            when (val linked = registry.linkJob(expectedAccountId, candidate.localKey, jobId, clock())) {
                is AppResult.Failure -> return linked.error.toDecision()
                is AppResult.Success -> Unit
            }
            when (val queued = uploads.enqueue(
                session,
                candidate.contentUri,
                remotePath,
                scheduleImmediately = false,
                requestedJobId = jobId,
            )) {
                is AppResult.Failure -> {
                    registry.removeForJob(jobId)
                    if (queued.error.isRetryable()) return AutoUploadRunDecision.Retry(queued.error)
                }
                is AppResult.Success -> {
                    when (val confirmed = registry.confirmQueued(queued.value.id, clock())) {
                        is AppResult.Failure -> return confirmed.error.toDecision()
                        is AppResult.Success -> when (val started = uploads.start(queued.value.id)) {
                            is AppResult.Failure -> return started.error.toDecision()
                            is AppResult.Success -> enqueued += 1
                        }
                    }
                }
            }
        }
        return AutoUploadRunDecision.Success(candidates.size, enqueued)
    }

    private fun AutoUploadPolicy.blockedBy(conditions: DeviceConditions): AppFailure? = when {
        wifiOnly && !conditions.wifiConnected -> AppFailure.NoNetwork
        requiredSsid != null && conditions.ssid == null -> AppFailure.WifiIdentityUnavailable
        requiredSsid != null && conditions.ssid != requiredSsid -> AppFailure.NoNetwork
        requireCharging && !conditions.charging -> AppFailure.AutoUploadConditionsNotMet
        minimumBatteryPercent > 0 && conditions.batteryPercent == null -> AppFailure.AutoUploadConditionsNotMet
        conditions.batteryPercent != null && conditions.batteryPercent < minimumBatteryPercent ->
            AppFailure.AutoUploadConditionsNotMet
        else -> null
    }

    private fun AutoUploadPolicy.includes(candidate: LocalMediaCandidate): Boolean {
        if (candidate.kind == LocalMediaKind.VIDEO && !includeVideos) return false
        val normalized = candidate.relativePath.normalizedFolder()
        return selectedFolders.any { it.normalizedFolder() == normalized }
    }

    private fun String.normalizedFolder() = replace('\\', '/').trim('/').lowercase()

    private fun remotePath(policy: AutoUploadPolicy, candidate: LocalMediaCandidate): String? {
        val date = Instant.ofEpochMilli(candidate.capturedAtEpochMs).atZone(ZoneId.systemDefault())
        val name = candidate.displayName.replace('/', '_').replace('\\', '_').replace('\u0000', '_')
        val raw = listOf(
            policy.remoteBasePath.trim('/'),
            policy.deviceFolder.trim().replace('/', '_').replace('\\', '_'),
            date.year.toString(),
            date.monthValue.toString().padStart(2, '0'),
            name,
        ).filter(String::isNotBlank).joinToString("/")
        return RemotePath.parse(raw).getOrNull()?.value
    }

    private fun AppFailure.toDecision() = if (isRetryable()) {
        AutoUploadRunDecision.Retry(this)
    } else {
        AutoUploadRunDecision.Failure(this)
    }

    private fun AppFailure.isRetryable() = when (this) {
        AppFailure.NoNetwork, AppFailure.ServerUnavailable, AppFailure.LocalStorageUnavailable,
        AppFailure.SecureStorageUnavailable, AppFailure.WifiIdentityUnavailable,
        AppFailure.AutoUploadConditionsNotMet -> true
        is AppFailure.Http -> statusCode == 408 || statusCode == 429 || statusCode >= 500
        else -> false
    }

    private companion object {
        const val STALE_CLAIM_MILLIS = 60L * 60 * 1_000
    }
}
