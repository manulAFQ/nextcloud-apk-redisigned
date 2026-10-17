package ru.manul.nextcloudgallery.data.remote

import android.content.Context
import android.net.Uri
import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.OwnCloudClientFactory
import com.owncloud.android.lib.common.OwnCloudCredentialsFactory
import ru.manul.nextcloudgallery.core.model.UserSession

internal fun interface NextcloudLibraryClientFactory {
    fun create(session: UserSession): NextcloudLibraryClient
}

internal class NextcloudLibraryClient(
    val delegate: OwnCloudClient,
)

internal class OfficialNextcloudClientFactory(
    context: Context,
) : NextcloudLibraryClientFactory {
    private val applicationContext = context.applicationContext

    override fun create(session: UserSession): NextcloudLibraryClient {
        val client = OwnCloudClientFactory.createOwnCloudClient(
            Uri.parse(session.account.serverAddress.value),
            applicationContext,
            true,
        )
        client.setFollowRedirects(false)
        client.setCredentials(
            OwnCloudCredentialsFactory.newBasicCredentials(
                session.account.username,
                session.appPassword.reveal(),
            ),
        )
        return NextcloudLibraryClient(client)
    }
}
