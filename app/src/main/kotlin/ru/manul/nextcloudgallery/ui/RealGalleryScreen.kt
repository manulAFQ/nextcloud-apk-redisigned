@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package ru.manul.nextcloudgallery.ui

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.RemoteAlbum
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.ui.theme.CloudBlue

private enum class RealTimeDetail { DAY, MONTH, YEAR }

private const val VIEWER_EXIT_MILLIS = 190

@Composable
internal fun RealMediaScreen(
    session: UserSession,
    media: List<MediaItem>,
    albums: List<RemoteAlbum> = emptyList(),
    vm: AppViewModel? = null,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    windowOffset: Int = 0,
    estimatedTotal: Int = media.size,
    focusIndex: Int? = null,
    loadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    onSeekAbsolute: ((Int) -> Unit)? = null,
    title: String = "Мои фото",
) {
    val context = LocalContext.current
    var pendingDownload by remember { mutableStateOf<MediaItem?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment
            ?: "photo"
        vm?.enqueueUpload(uri.toString(), name)
    }
    val downloadPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val item = pendingDownload
        pendingDownload = null
        if (uri != null && item != null) vm?.downloadMedia(item, uri)
    }
    val content: @Composable () -> Unit = {
        RealGalleryContent(
            session = session,
            media = media,
            albums = albums,
            vm = vm,
            onAdd = vm?.let { { picker.launch(arrayOf("image/*", "video/*")) } },
            onDownload = { item -> pendingDownload = item; downloadPicker.launch(item.displayName) },
            windowOffset = windowOffset,
            estimatedTotal = estimatedTotal,
            focusIndex = focusIndex,
            loadingMore = loadingMore,
            onLoadMore = onLoadMore,
            onSeekAbsolute = onSeekAbsolute,
            title = title,
        )
    }
    if (onRefresh != null) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            content = { content() },
        )
    } else {
        content()
    }
}

@Composable
private fun RealGalleryContent(
    session: UserSession,
    media: List<MediaItem>,
    albums: List<RemoteAlbum>,
    vm: AppViewModel?,
    onAdd: (() -> Unit)?,
    onDownload: (MediaItem) -> Unit,
    windowOffset: Int,
    estimatedTotal: Int,
    focusIndex: Int?,
    loadingMore: Boolean,
    onLoadMore: (() -> Unit)?,
    onSeekAbsolute: ((Int) -> Unit)?,
    title: String,
) {
    val gridState = rememberLazyGridState()
    val viewerScope = rememberCoroutineScope()
    var cellSize by remember { mutableFloatStateOf(118f) }
    var isPinching by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var viewerVisible by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var chooseAlbum by remember { mutableStateOf(false) }
    val currentSelection by rememberUpdatedState(selectedPaths)
    val haptic = LocalHapticFeedback.current
    val detail = when {
        cellSize >= 145f -> RealTimeDetail.DAY
        cellSize >= 92f -> RealTimeDetail.MONTH
        else -> RealTimeDetail.YEAR
    }
    val zone = remember { ZoneId.systemDefault() }
    val groups = remember(media, zone) {
        media.groupBy { item -> (item.capturedAt ?: item.modifiedAt).atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
    }
    val indexByPath = remember(media) { media.mapIndexed { index, item -> item.remotePath to index }.toMap() }
    val selectedItems = remember(media, selectedPaths) { media.filter { it.remotePath in selectedPaths } }
    LaunchedEffect(media) {
        val available = media.mapTo(hashSetOf(), MediaItem::remotePath)
        selectedPaths = selectedPaths.filterTo(hashSetOf()) { it in available }
    }
    LaunchedEffect(gridState, media.size, windowOffset, onLoadMore) {
        if (onLoadMore == null || media.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 12) onLoadMore()
        }
    }
    LaunchedEffect(windowOffset, focusIndex, media.size) {
        val focus = focusIndex ?: return@LaunchedEffect
        if (focus !in windowOffset until (windowOffset + media.size) || media.isEmpty()) return@LaunchedEffect
        delay(20)
        val totalGridItems = gridState.layoutInfo.totalItemsCount.coerceAtLeast(1)
        val localFraction = (focus - windowOffset).toFloat() / media.lastIndex.coerceAtLeast(1)
        gridState.scrollToItem((localFraction * (totalGridItems - 1)).roundToInt())
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var event: PointerEvent
                    do {
                        event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                isPinching = true
                                cellSize = (cellSize * zoom).coerceIn(64f, 186f)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    isPinching = false
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            RealGalleryHeader(title, estimatedTotal.coerceAtLeast(media.size), cellSize, onAdd)
            if (media.isEmpty()) {
                RealGalleryEmpty(Modifier.weight(1f), onAdd)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(cellSize.dp),
                    state = gridState,
                    contentPadding = PaddingValues(start = 5.dp, end = 12.dp, bottom = 112.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxSize().pointerInput(media) {
                        val visited = hashSetOf<String>()
                        var selecting = true
                        var gestureSelection = emptySet<String>()
                        fun pathAt(position: Offset): String? {
                            val key = gridState.layoutInfo.visibleItemsInfo.firstOrNull { visible ->
                                position.x >= visible.offset.x &&
                                    position.x <= visible.offset.x + visible.size.width &&
                                    position.y >= visible.offset.y &&
                                    position.y <= visible.offset.y + visible.size.height
                            }?.key as? String
                            return key?.takeIf(indexByPath::containsKey)
                        }
                        fun visit(position: Offset) {
                            val path = pathAt(position) ?: return
                            if (!visited.add(path)) return
                            gestureSelection = if (selecting) gestureSelection + path else gestureSelection - path
                            selectedPaths = gestureSelection
                        }
                        detectDragGesturesAfterLongPress(
                            onDragStart = { position ->
                                visited.clear()
                                pathAt(position)?.let { path ->
                                    gestureSelection = currentSelection
                                    selecting = path !in currentSelection
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    visit(position)
                                }
                            },
                            onDragEnd = { visited.clear() },
                            onDragCancel = { visited.clear() },
                            onDrag = { change, _ ->
                                change.consume()
                                visit(change.position)
                            },
                        )
                    },
                ) {
                    groups.forEach { (date, datedMedia) ->
                        item(key = "header-$date", span = { GridItemSpan(maxLineSpan) }) {
                            RealPhotoSectionHeader(date)
                        }
                        items(datedMedia, key = { it.remotePath }) { item ->
                            val index = indexByPath[item.remotePath] ?: 0
                            RealPhotoTile(
                                session = session,
                                item = item,
                                expanded = cellSize >= 145f,
                                modifier = Modifier.animateItem(),
                                selected = item.remotePath in selectedPaths,
                                onOpen = {
                                    if (selectedPaths.isNotEmpty()) {
                                        selectedPaths = if (item.remotePath in selectedPaths) {
                                            selectedPaths - item.remotePath
                                        } else {
                                            selectedPaths + item.remotePath
                                        }
                                    } else {
                                        viewerIndex = index
                                        viewerVisible = true
                                    }
                                },
                            )
                        }
                    }
                    if (loadingMore) {
                        item(key = "page-loading", span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp, color = CloudBlue)
                            }
                        }
                    }
                }
            }
        }

        if (media.isNotEmpty()) {
            RealFastScroller(
                gridState = gridState,
                detail = detail,
                media = media,
                windowOffset = windowOffset,
                estimatedTotal = estimatedTotal.coerceAtLeast(media.size),
                onSeekAbsolute = onSeekAbsolute,
                modifier = Modifier.align(Alignment.CenterEnd).padding(top = 112.dp, bottom = 96.dp),
            )
        }

        AnimatedVisibility(
            visible = isPinching,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 118.dp),
            enter = fadeIn() + scaleIn(initialScale = 0.82f),
            exit = fadeOut() + scaleOut(targetScale = 0.84f),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
                contentColor = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 8.dp,
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.GridView, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        when (detail) {
                            RealTimeDetail.DAY -> "По дням"
                            RealTimeDetail.MONTH -> "По месяцам"
                            RealTimeDetail.YEAR -> "По годам"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selectedPaths.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 82.dp),
            enter = slideInVertically(tween(240, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(),
            exit = slideOutVertically(tween(150, easing = FastOutLinearInEasing)) { it / 2 } + fadeOut(),
        ) {
            SelectionActionBar(
                count = selectedItems.size,
                onClose = { selectedPaths = emptySet() },
                onShare = { vm?.shareMedia(selectedItems) },
                onDelete = { confirmDelete = true },
                onMove = { chooseAlbum = true },
            )
        }
    }

    viewerIndex?.takeIf { it in media.indices }?.let { initialIndex ->
        val dismiss = {
            viewerVisible = false
            viewerScope.launch {
                delay(VIEWER_EXIT_MILLIS + 30L)
                viewerIndex = null
            }
            Unit
        }
        Dialog(
            onDismissRequest = dismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            AnimatedVisibility(
                visible = viewerVisible,
                enter = fadeIn(tween(320, easing = LinearOutSlowInEasing)) +
                    scaleIn(initialScale = 0.955f, animationSpec = tween(430, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(430, easing = FastOutSlowInEasing)) { it / 24 },
                exit = fadeOut(tween(145, easing = FastOutLinearInEasing)) +
                    scaleOut(targetScale = 0.975f, animationSpec = tween(VIEWER_EXIT_MILLIS, easing = FastOutLinearInEasing)) +
                    slideOutVertically(tween(VIEWER_EXIT_MILLIS, easing = FastOutLinearInEasing)) { it / 30 },
            ) {
                RealRemoteViewer(
                    session = session,
                    media = media,
                    initial = initialIndex,
                    albums = albums,
                    vm = vm,
                    onDownload = onDownload,
                    close = dismiss,
                )
            }
        }
    }

    if (confirmDelete && selectedItems.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить ${selectedItems.size} файл(а)?") },
            text = { Text("Файлы будут перемещены в корзину Nextcloud.") },
            confirmButton = {
                TextButton(onClick = {
                    vm?.deleteMedia(selectedItems)
                    selectedPaths = emptySet()
                    confirmDelete = false
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
    if (chooseAlbum) {
        AlbumPickerDialog(
            albums = albums,
            onDismiss = { chooseAlbum = false },
            onSelected = { album ->
                vm?.addToAlbum(album, selectedItems)
                selectedPaths = emptySet()
                chooseAlbum = false
            },
        )
    }
}

@Composable
private fun RealGalleryHeader(title: String, itemCount: Int, cellSize: Float, onAdd: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.displaySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudDone, null, tint = CloudBlue, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "Синхронизировано · $itemCount объектов",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            onAdd?.let {
                IconButton(onClick = it) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 5.dp) {
                        Icon(Icons.Rounded.Add, "Добавить", tint = CloudBlue, modifier = Modifier.padding(9.dp).size(21.dp))
                    }
                }
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Воспоминания",
                style = MaterialTheme.typography.labelMedium,
                color = CloudBlue,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(CloudBlue.copy(alpha = 0.1f)).padding(horizontal = 11.dp, vertical = 7.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Сведите или разведите два пальца",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("${(392f / cellSize).roundToInt().coerceIn(2, 6)} ×", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RealPhotoSectionHeader(date: LocalDate) {
    val today = LocalDate.now()
    val title = when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> date.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 11.dp, end = 7.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(7.dp))
        Text(date.year.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
private fun RealPhotoTile(
    session: UserSession,
    item: MediaItem,
    expanded: Boolean,
    modifier: Modifier,
    selected: Boolean,
    onOpen: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "realPhotoPress",
    )
    Surface(
        onClick = onOpen,
        interactionSource = interaction,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(if (expanded) 16.dp else 6.dp),
        modifier = modifier.aspectRatio(1f).scale(scale),
    ) {
        Box {
            RemoteThumbnail(session, item, Modifier.fillMaxSize())
            if (expanded) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.56f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.48f))))
                Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                    Text(item.displayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (item.capturedAt ?: item.modifiedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM")),
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 10.sp,
                    )
                }
            }
            if (selected) {
                Box(
                    Modifier.fillMaxSize()
                        .background(CloudBlue.copy(alpha = 0.24f))
                        .border(3.dp, CloudBlue, RoundedCornerShape(if (expanded) 16.dp else 6.dp)),
                )
            }
            AnimatedVisibility(
                visible = selected,
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.55f),
                exit = fadeOut(tween(90)) + scaleOut(targetScale = 0.55f),
            ) {
                Surface(shape = CircleShape, color = CloudBlue, contentColor = Color.White, shadowElevation = 4.dp) {
                    Icon(Icons.Rounded.Check, "Выбрано", modifier = Modifier.padding(4.dp).size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    count: Int,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(26.dp),
        shadowElevation = 16.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Выбрано: $count", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.Close, "Снять выделение", modifier = Modifier.size(20.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SelectionAction(Icons.Rounded.Share, "Поделиться", if (count > 20) "до 20 файлов" else null, onShare)
                SelectionAction(Icons.Rounded.DeleteOutline, "Удалить", null, onDelete, MaterialTheme.colorScheme.error)
                SelectionAction(Icons.Rounded.Folder, "В альбом", null, onMove)
            }
        }
    }
}

@Composable
private fun SelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    hint: String?,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(onClick = onClick, color = Color.Transparent, contentColor = tint, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.width(102.dp).padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, modifier = Modifier.size(23.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            hint?.let { Text(it, fontSize = 9.sp, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun AlbumPickerDialog(
    albums: List<RemoteAlbum>,
    onDismiss: () -> Unit,
    onSelected: (RemoteAlbum) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(28.dp), shadowElevation = 18.dp) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Переместить в альбом", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Закрыть") }
                }
                if (albums.isEmpty()) {
                    Text(
                        "Сначала создайте альбом во вкладке «Альбомы».",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp)) {
                        lazyListItems(albums, key = RemoteAlbum::remotePath) { album ->
                            Surface(onClick = { onSelected(album) }, color = Color.Transparent, shape = RoundedCornerShape(16.dp)) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Folder, null, tint = CloudBlue)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(album.name, style = MaterialTheme.typography.titleMedium)
                                        Text("${album.itemCount} объектов", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RealRemoteViewer(
    session: UserSession,
    media: List<MediaItem>,
    initial: Int,
    albums: List<RemoteAlbum>,
    vm: AppViewModel?,
    onDownload: (MediaItem) -> Unit,
    close: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = initial) { media.size }
    val item = media[pager.currentPage]
    var albumTarget by remember { mutableStateOf<MediaItem?>(null) }
    var deleteTarget by remember { mutableStateOf<MediaItem?>(null) }
    val favorite = item.favorite
    Box(Modifier.fillMaxSize().background(Color(0xFF050608))) {
        HorizontalPager(state = pager, beyondViewportPageCount = 1, key = { media[it].remotePath }, modifier = Modifier.fillMaxSize()) { page ->
            RemoteThumbnail(session, media[page], Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Box(Modifier.fillMaxWidth().height(130.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent))))
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RealViewerRoundButton(Icons.Rounded.ArrowBackIosNew, "Закрыть", close)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item.displayName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (item.capturedAt ?: item.modifiedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMMM · HH:mm", Locale("ru"))),
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 10.sp,
                )
            }
            RealViewerRoundButton(Icons.Rounded.MoreHoriz, "Дополнительно") { }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.94f))))
                .horizontalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(start = 8.dp, end = 8.dp, top = 38.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            RealViewerAction(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Избранное", if (favorite) Color(0xFFFFBD38) else Color.White) {
                vm?.setFavorite(item, !favorite)
            }
            RealViewerAction(Icons.Rounded.Folder, "В альбом") { albumTarget = item }
            RealViewerAction(Icons.Rounded.Share, "Поделиться") { vm?.shareMedia(listOf(item)) }
            RealViewerAction(Icons.Rounded.Download, "Скачать") { onDownload(item) }
            RealViewerAction(Icons.Rounded.DeleteOutline, "Удалить", Color(0xFFFF686D)) { deleteTarget = item }
        }
    }
    albumTarget?.let { target ->
        AlbumPickerDialog(
            albums = albums,
            onDismiss = { albumTarget = null },
            onSelected = { album ->
                vm?.addToAlbum(album, listOf(target))
                albumTarget = null
            },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить файл?") },
            text = { Text("«${target.displayName}» будет перемещён в корзину Nextcloud.") },
            confirmButton = {
                TextButton(onClick = {
                    vm?.deleteMedia(listOf(target))
                    deleteTarget = null
                    close()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun RealViewerRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.Black.copy(alpha = 0.42f), contentColor = Color.White) {
        Icon(icon, description, modifier = Modifier.padding(10.dp).size(20.dp))
    }
}

@Composable
private fun RealViewerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit = {},
) {
    Surface(onClick = onClick, color = Color.Transparent, contentColor = tint, modifier = Modifier.width(64.dp)) {
        Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, modifier = Modifier.size(23.dp))
            Spacer(Modifier.height(5.dp))
            Text(label, color = tint, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun RealFastScroller(
    gridState: LazyGridState,
    detail: RealTimeDetail,
    media: List<MediaItem>,
    windowOffset: Int,
    estimatedTotal: Int,
    onSeekAbsolute: ((Int) -> Unit)?,
    modifier: Modifier,
) {
    var trackHeight by remember { mutableIntStateOf(1) }
    var fraction by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var targetAbsolute by remember { mutableIntStateOf(windowOffset) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val thumbHeightPx = with(density) { 38.dp.roundToPx() }
    LaunchedEffect(gridState, dragging) {
        snapshotFlow {
            val gridTotal = gridState.layoutInfo.totalItemsCount.coerceAtLeast(1)
            val localFraction = gridState.firstVisibleItemIndex.toFloat() / (gridTotal - 1).coerceAtLeast(1)
            val absolute = windowOffset + localFraction * media.lastIndex.coerceAtLeast(0)
            absolute / (estimatedTotal - 1).coerceAtLeast(1)
        }.collect { if (!dragging) fraction = it.coerceIn(0f, 1f) }
    }
    fun seek(localY: Float) {
        fraction = (localY / trackHeight).coerceIn(0f, 1f)
        targetAbsolute = (fraction * (estimatedTotal - 1).coerceAtLeast(0)).roundToInt()
        if (targetAbsolute in windowOffset until (windowOffset + media.size)) {
            val localFraction = (targetAbsolute - windowOffset).toFloat() / media.lastIndex.coerceAtLeast(1)
            val gridTotal = gridState.layoutInfo.totalItemsCount.coerceAtLeast(1)
            scope.launch { gridState.scrollToItem((localFraction * (gridTotal - 1)).roundToInt()) }
        }
    }
    Box(
        modifier.width(64.dp).fillMaxHeight().onSizeChanged { trackHeight = it.height.coerceAtLeast(1) }
            .pointerInput(detail) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragging = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress); seek(it.y) },
                    onDragEnd = {
                        dragging = false
                        if (targetAbsolute !in windowOffset until (windowOffset + media.size)) {
                            onSeekAbsolute?.invoke(targetAbsolute)
                        }
                    },
                    onDragCancel = { dragging = false },
                    onDrag = { change, _ -> change.consume(); seek(change.position.y) },
                )
            },
    ) {
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 7.dp).width(3.dp).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))
        Box(
            Modifier.align(Alignment.TopEnd).padding(end = 4.dp)
                .offset { IntOffset(0, (fraction * (trackHeight - thumbHeightPx).coerceAtLeast(0)).roundToInt()) }
                .size(width = if (dragging) 10.dp else 8.dp, height = 38.dp).clip(CircleShape)
                .background(if (dragging) CloudBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)),
        )
        AnimatedVisibility(
            visible = dragging,
            modifier = Modifier.align(Alignment.TopEnd).offset {
                IntOffset(
                    with(density) { (-48).dp.roundToPx() },
                    (fraction * (trackHeight - thumbHeightPx).coerceAtLeast(0)).roundToInt() - with(density) { 7.dp.roundToPx() },
                )
            },
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut() + scaleOut(targetScale = 0.7f),
        ) {
            val localIndex = targetAbsolute - windowOffset
            val label = if (localIndex in media.indices) {
                val date = (media[localIndex].capturedAt ?: media[localIndex].modifiedAt).atZone(ZoneId.systemDefault())
                when (detail) {
                    RealTimeDetail.DAY -> date.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))
                    RealTimeDetail.MONTH -> date.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))).replaceFirstChar { it.uppercase(Locale("ru")) }
                    RealTimeDetail.YEAR -> date.year.toString()
                }
            } else {
                "${targetAbsolute + 1} из $estimatedTotal"
            }
            Surface(color = CloudBlue, contentColor = Color.White, shape = RoundedCornerShape(17.dp), shadowElevation = 10.dp) {
                Text(label, modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RealGalleryEmpty(modifier: Modifier, onAdd: (() -> Unit)?) {
    Box(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = RoundedCornerShape(28.dp), color = CloudBlue.copy(alpha = 0.12f)) {
                Icon(Icons.Rounded.Photo, null, tint = CloudBlue, modifier = Modifier.padding(24.dp).size(42.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Фотографий пока нет", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Потяните вниз для обновления или загрузите фото", color = MaterialTheme.colorScheme.onSurfaceVariant)
            onAdd?.let {
                Spacer(Modifier.height(16.dp))
                Surface(onClick = it, color = CloudBlue, contentColor = Color.White, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Upload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Загрузить")
                    }
                }
            }
        }
    }
}
