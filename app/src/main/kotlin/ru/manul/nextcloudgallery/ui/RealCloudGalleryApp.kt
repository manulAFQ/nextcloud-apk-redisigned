@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package ru.manul.nextcloudgallery.ui

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.manul.nextcloudgallery.GalleryApplication
import ru.manul.nextcloudgallery.core.model.ActivityEvent
import ru.manul.nextcloudgallery.core.model.LocalMediaCandidate
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.RemoteAlbum
import ru.manul.nextcloudgallery.core.model.TrashItem
import ru.manul.nextcloudgallery.core.model.UploadJob
import ru.manul.nextcloudgallery.core.model.UploadState
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.ui.theme.CloudBlue
import ru.manul.nextcloudgallery.ui.theme.CloudGalleryTheme

private enum class Section(val title: String, val icon: ImageVector) {
    MEDIA("Медиа", Icons.Outlined.Home),
    ALBUMS("Альбомы", Icons.Outlined.Collections),
    EVENTS("События", Icons.Outlined.Event),
    TRANSFERS("Состояние передачи", Icons.Outlined.SwapVert),
    SYNC("Синхронизация", Icons.Outlined.Sync),
    DEVICE("На устройстве", Icons.Outlined.Devices),
    TRASH("Удалённые файлы", Icons.Outlined.DeleteOutline),
    SETTINGS("Настройки", Icons.Outlined.Settings),
}

@Composable
fun RealCloudGalleryApp(vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.openUrl.collect { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    LaunchedEffect(Unit) {
        vm.shareRequest.collect { request ->
            val intent = Intent(if (request.uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                type = request.mimeType
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (request.uris.size == 1) {
                    putExtra(Intent.EXTRA_STREAM, request.uris.first())
                } else {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(request.uris))
                }
                clipData = ClipData.newUri(context.contentResolver, "Файлы", request.uris.first()).apply {
                    request.uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
            }
            context.startActivity(Intent.createChooser(intent, "Поделиться"))
        }
    }
    val darkTheme = when (state.themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    CloudGalleryTheme(darkTheme = darkTheme) {
        when {
            state.booting -> LoadingScreen()
            state.session == null -> LoginScreen(state, vm)
            else -> SignedInApp(state, vm)
        }
    }
}

@Composable
private fun LoadingScreen() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(color = CloudBlue)
}

@Composable
private fun LoginScreen(state: AppUiState, vm: AppViewModel) {
    val loginInk = MaterialTheme.colorScheme.onBackground
    val loginMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val loginOutline = Color(0xFF6C9FEF)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = CloudBlue.copy(alpha = .12f)) {
            Icon(Icons.Rounded.Cloud, null, tint = CloudBlue, modifier = Modifier.padding(22.dp).size(54.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Вход в облако", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = loginInk)
        Text("Откроется привычная страница Nextcloud. Пароль в приложение не передаётся — оно получит отдельный app-password.", color = loginMuted)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.serverAddress,
            onValueChange = vm::setServerAddress,
            label = { Text("Адрес Nextcloud") },
            singleLine = true,
            isError = state.message != null,
            supportingText = { Text("Укажите адрес самого Nextcloud, а не CRM или сайт", color = loginMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = loginInk,
                unfocusedTextColor = loginInk,
                focusedLabelColor = Color(0xFF275FAF),
                unfocusedLabelColor = loginMuted,
                focusedBorderColor = loginOutline,
                unfocusedBorderColor = Color(0xFF8290A8),
                cursorColor = CloudBlue,
                errorTextColor = loginInk,
                errorLabelColor = Color(0xFF9B1C1C),
                errorBorderColor = Color(0xFFB42318),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = vm::login,
            enabled = !state.loginInProgress,
            colors = ButtonDefaults.buttonColors(containerColor = CloudBlue, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (state.loginInProgress) {
                CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("Жду подтверждения в браузере…")
            } else Text("Войти через Nextcloud")
        }
        state.message?.let { Text(it, color = Color(0xFF9B1C1C), fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignedInApp(state: AppUiState, vm: AppViewModel) {
    var tab by remember { mutableStateOf(AppTab.HOME) }
    var openedSection by remember { mutableStateOf<Section?>(null) }
    var settingsVisit by remember { mutableStateOf(0) }
    var bottomBarVisible by remember { mutableStateOf(true) }
    var scrollGeneration by remember { mutableIntStateOf(0) }
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && kotlin.math.abs(available.y) > 1f) {
                    bottomBarVisible = available.y > 0f
                    scrollGeneration++
                }
                return Offset.Zero
            }
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val activity = LocalActivity.current
    BackHandler {
        if (openedSection != null || tab != AppTab.HOME) {
            openedSection = null
            tab = AppTab.HOME
            bottomBarVisible = true
        } else {
            activity?.finish()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }
    LaunchedEffect(scrollGeneration) {
        if (scrollGeneration > 0) {
            delay(850)
            bottomBarVisible = true
        }
    }
    LaunchedEffect(tab, openedSection) { bottomBarVisible = true }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxSize().nestedScroll(scrollConnection)) {
                if (openedSection != null) {
                    SubSectionScreen(openedSection!!, state, vm, onBack = {
                        openedSection = null
                        if (tab == AppTab.SETTINGS) settingsVisit++
                    })
                } else {
                    AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(spring(dampingRatio = .84f, stiffness = 420f)) { it / 5 * direction } +
                            fadeIn() + scaleIn(initialScale = .985f)) togetherWith
                            (slideOutHorizontally(spring(dampingRatio = .9f, stiffness = 500f)) { -it / 7 * direction } + fadeOut())
                    },
                    label = "mainTab",
                    ) { selected ->
                        when (selected) {
                        AppTab.HOME -> RealMediaScreen(
                            session = state.session!!,
                            media = state.media,
                            albums = state.albums,
                            vm = vm,
                            refreshing = state.refreshing,
                            onRefresh = vm::refreshVisibleContent,
                            windowOffset = state.mediaWindowOffset,
                            estimatedTotal = state.mediaEstimatedTotal,
                            focusIndex = state.mediaFocusIndex,
                            loadingMore = state.mediaPageLoading,
                            onLoadMore = vm::loadNextMediaChunk,
                            onSeekAbsolute = vm::loadMediaChunkAt,
                        )
                        AppTab.ALBUMS -> RealAlbumsScreen(
                            session = state.session!!,
                            albums = state.albums,
                            albumFiles = state.albumFiles,
                            rootMedia = state.media,
                            vm = vm,
                            refreshing = state.refreshing,
                            onRefresh = vm::refreshVisibleContent,
                        )
                        AppTab.MARKS -> RealMediaScreen(
                            session = state.session!!,
                            media = state.favorites,
                            albums = state.albums,
                            vm = vm,
                            refreshing = state.refreshing,
                            onRefresh = vm::refreshVisibleContent,
                            title = "Избранное",
                        )
                        AppTab.SETTINGS -> SettingsHub(state, vm, settingsVisit) { section ->
                            when (section) {
                                Section.MEDIA -> tab = AppTab.HOME
                                Section.ALBUMS -> tab = AppTab.ALBUMS
                                Section.SETTINGS -> Unit
                                else -> openedSection = section
                            }
                        }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = openedSection == null && bottomBarVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(180)),
                exit = slideOutVertically(tween(190, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(140)),
            ) {
                FloatingBottomBar(selected = tab, onSelected = { next ->
                    if (next == AppTab.SETTINGS && tab != AppTab.SETTINGS) settingsVisit++
                    tab = next
                    bottomBarVisible = true
                })
            }
            AnimatedVisibility(
                visible = state.sharePreparing,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
                enter = slideInVertically(tween(230, easing = FastOutSlowInEasing)) { -it } + fadeIn(),
                exit = slideOutVertically(tween(170, easing = FastOutLinearInEasing)) { -it } + fadeOut(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(22.dp),
                    shadowElevation = 14.dp,
                    border = BorderStroke(1.dp, CloudBlue.copy(alpha = 0.28f)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(25.dp), strokeWidth = 2.5.dp, color = CloudBlue)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Скачиваю оригиналы для отправки", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${state.sharePrepared} из ${state.shareTotal} · меню откроется автоматически",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubSectionScreen(section: Section, state: AppUiState, vm: AppViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(onClick = onBack, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Icon(Icons.Rounded.ArrowBack, "Назад", modifier = Modifier.padding(11.dp).size(21.dp))
            }
            Spacer(Modifier.width(13.dp))
            Text(section.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.refreshAll() }) { Icon(Icons.Rounded.Refresh, "Обновить") }
        }
        Box(Modifier.weight(1f)) {
            when (section) {
                Section.EVENTS -> EventsScreen(state.events)
                Section.TRANSFERS -> TransfersScreen(state.uploads, vm)
                Section.SYNC -> SyncScreen(state, vm)
                Section.DEVICE -> DeviceScreen(state.localMedia, vm)
                Section.TRASH -> TrashScreen(state.trash, vm)
                else -> Unit
            }
        }
    }
}

@Composable
private fun LegacyRealMediaScreenUnused(session: UserSession, media: List<MediaItem>, vm: AppViewModel? = null) {
    var openIndex by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: uri.lastPathSegment ?: "photo"
        vm?.enqueueUpload(uri.toString(), name)
    }
    val groups = remember(media) {
        media.groupBy { item ->
            (item.capturedAt ?: item.modifiedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }.toSortedMap(compareByDescending { it })
    }
    Column(Modifier.fillMaxSize()) {
        LegacyHeader("Фотографии", if (media.isEmpty()) "Nextcloud · все папки" else "${media.size} файлов · Nextcloud")
        Box(Modifier.weight(1f)) {
            if (media.isEmpty()) {
                EmptyState(Icons.Rounded.Photo, "Фотографий пока нет", "Обновите ленту или загрузите фото")
            } else {
                LazyVerticalGrid(columns = GridCells.Adaptive(112.dp), contentPadding = PaddingValues(5.dp, 2.dp, 12.dp, 20.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    groups.forEach { (date, datedMedia) ->
                        item(key = "date-$date", span = { GridItemSpan(maxLineSpan) }) {
                            Column(Modifier.fillMaxWidth().padding(start = 8.dp, top = 15.dp, bottom = 8.dp)) {
                                Text(date.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(date.year.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        items(datedMedia, key = { it.remotePath }) { item -> RemoteThumbnail(session, item, Modifier.aspectRatio(1f).clip(RoundedCornerShape(7.dp)).clickable { openIndex = media.indexOf(item) }) }
                    }
                }
            }
            if (vm != null) {
                FloatingActionButton(
                    onClick = { picker.launch(arrayOf("image/*", "video/*")) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp),
                    containerColor = CloudBlue,
                ) { Icon(Icons.Rounded.Upload, "Загрузить фото", tint = Color.White) }
            }
        }
    }
    openIndex?.let { index -> RemoteViewer(session, media, index) { openIndex = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RealAlbumsScreen(
    session: UserSession,
    albums: List<RemoteAlbum>,
    albumFiles: Map<String, List<MediaItem>>,
    rootMedia: List<MediaItem>,
    vm: AppViewModel,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    var selected by remember { mutableStateOf<RemoteAlbum?>(null) }
    var createDialog by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf<RemoteAlbum?>(null) }
    selected?.let { album ->
        val files = albumFiles[album.remotePath].orEmpty()
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected = null }) { Icon(Icons.Rounded.ArrowBack, "Назад") }
                Column { Text(album.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("${files.size} файлов") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { addDialog = album }) { Icon(Icons.Rounded.Add, "Добавить фото") }
            }
            RealMediaScreen(session, files.take(10))
        }
        addDialog?.let { target ->
            MediaSelectionDialog(session, "Добавить в «${target.name}»", rootMedia, { addDialog = null }) { chosen ->
                vm.addToAlbum(target, chosen)
                addDialog = null
            }
        }
        return
    }
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
      Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LegacyHeader("Альбомы", "${albums.size} коллекций · Nextcloud")
            if (albums.isEmpty()) Box(Modifier.weight(1f)) { EmptyState(Icons.Rounded.Folder, "Альбомов пока нет", "Нажмите +, чтобы создать альбом Nextcloud Photos") }
            else LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 92.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)) {
                itemsIndexed(albums, key = { _, album -> album.remotePath }) { index, album ->
                    val files = albumFiles[album.remotePath].orEmpty()
                    RisingRemoteAlbumCard(session, album, files, index) { selected = album }
                }
            }
        }
        FloatingActionButton(onClick = { createDialog = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp), containerColor = CloudBlue) { Icon(Icons.Rounded.Add, "Создать альбом", tint = Color.White) }
      }
    }
    if (createDialog) CreateAlbumDialog(onDismiss = { createDialog = false }) { name -> vm.createAlbum(name); createDialog = false }
}

@Composable
private fun RisingRemoteAlbumCard(
    session: UserSession,
    album: RemoteAlbum,
    files: List<MediaItem>,
    index: Int,
    onClick: () -> Unit,
) {
    val offset = remember(album.remotePath) { Animatable(170f) }
    val opacity = remember(album.remotePath) { Animatable(0f) }
    val entryScale = remember(album.remotePath) { Animatable(.88f) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) .96f else 1f,
        animationSpec = spring(dampingRatio = .55f, stiffness = 600f),
        label = "remoteAlbumPress",
    )
    LaunchedEffect(album.remotePath) {
        delay(index * 48L)
        opacity.animateTo(1f, tween(220))
    }
    LaunchedEffect(album.remotePath) {
        delay(index * 48L)
        offset.animateTo(0f, spring(dampingRatio = .56f, stiffness = 270f))
    }
    LaunchedEffect(album.remotePath) {
        delay(index * 48L)
        entryScale.animateTo(1f, spring(dampingRatio = .58f, stiffness = 300f))
    }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(23.dp),
        shadowElevation = 5.dp,
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            translationY = offset.value
            alpha = opacity.value
            scaleX = entryScale.value * pressScale
            scaleY = entryScale.value * pressScale
            rotationZ = (if (index % 2 == 0) -1f else 1f) * (offset.value / 170f) * 2.6f
        },
    ) {
        Column {
            if (files.isNotEmpty()) {
                RemoteThumbnail(
                    session,
                    files.first(),
                    Modifier.fillMaxWidth().aspectRatio(1.03f).clip(RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp)),
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1.03f).background(CloudBlue.copy(alpha = .1f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Folder, null, tint = CloudBlue, modifier = Modifier.size(46.dp)) }
            }
            Row(Modifier.padding(start = 13.dp, end = 10.dp, top = 11.dp, bottom = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(album.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${album.itemCount.coerceAtLeast(files.size)} файлов", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun CreateAlbumDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Новый альбом", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена") }
                    Button(onClick = { onCreate(name) }, enabled = name.trim().isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Создать") }
                }
            }
        }
    }
}

@Composable
private fun MediaSelectionDialog(session: UserSession, title: String, media: List<MediaItem>, onDismiss: () -> Unit, onConfirm: (List<MediaItem>) -> Unit) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth().fillMaxHeight(.82f).padding(18.dp), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Выберите фото из облака", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(GridCells.Adaptive(92.dp), modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(media, key = { it.remotePath }) { item ->
                        Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable {
                            selected = if (item.remotePath in selected) selected - item.remotePath else selected + item.remotePath
                        }) {
                            RemoteThumbnail(session, item, Modifier.fillMaxSize())
                            Checkbox(item.remotePath in selected, onCheckedChange = null, modifier = Modifier.align(Alignment.TopEnd))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена") }
                    Button(onClick = { onConfirm(media.filter { it.remotePath in selected }) }, enabled = selected.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Добавить") }
                }
            }
        }
    }
}

@Composable
private fun EventsScreen(events: List<ActivityEvent>) {
    var selected by remember { mutableStateOf<ActivityEvent?>(null) }
    if (events.isEmpty()) EmptyState(Icons.Outlined.Event, "Событий нет", "Здесь появится активность с вашего Nextcloud")
    else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        items(events, key = { it.id }) { event ->
            Card(onClick = { selected = event }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Surface(shape = CircleShape, color = CloudBlue.copy(alpha = .12f)) { Icon(Icons.Outlined.Event, null, tint = CloudBlue, modifier = Modifier.padding(10.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(event.subject, fontWeight = FontWeight.Medium); event.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text(formatTime(event.occurredAt.toEpochMilli()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
    selected?.let { event ->
        val context = LocalContext.current
        Dialog(onDismissRequest = { selected = null }) {
            Card { Column(Modifier.padding(20.dp)) {
                Text(event.subject, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                event.message?.let { Spacer(Modifier.height(8.dp)); Text(it) }
                event.objectName?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(8.dp)); Text(formatTime(event.occurredAt.toEpochMilli()), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(18.dp))
                if (event.link != null) Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.link))); selected = null }, modifier = Modifier.fillMaxWidth()) { Text("Открыть в Nextcloud") }
                else OutlinedButton(onClick = { selected = null }, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
            } }
        }
    }
}

@Composable
private fun TransfersScreen(jobs: List<UploadJob>, vm: AppViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: uri.lastPathSegment ?: "file"
        vm.enqueueUpload(uri.toString(), name)
    }
    Box(Modifier.fillMaxSize()) {
        if (jobs.isEmpty()) EmptyState(Icons.Outlined.SwapVert, "Передач пока нет", "Добавьте файл — здесь будет настоящий прогресс загрузки")
        else LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(jobs, key = { it.id }) { job -> UploadCard(job, vm) }
        }
        FloatingActionButton(onClick = { picker.launch(arrayOf("image/*", "video/*")) }, modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp), containerColor = CloudBlue) { Icon(Icons.Rounded.Add, "Добавить файл", tint = Color.White) }
    }
}

@Composable
private fun UploadCard(job: UploadJob, vm: AppViewModel) {
    val fraction = if (job.sizeBytes > 0) (job.transferredBytes.toFloat() / job.sizeBytes).coerceIn(0f, 1f) else 0f
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Upload, null, tint = CloudBlue)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(job.remotePath.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(
                    "Nextcloud · ${job.remotePath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CloudBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${job.state.russian()} · ${formatBytes(job.transferredBytes)} из ${formatBytes(job.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                job.failureMessage?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
                }
                androidx.compose.material3.LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            IconButton(onClick = { vm.uploadAction(job) }) { Icon(if (job.state == UploadState.RUNNING || job.state == UploadState.QUEUED) Icons.Rounded.Pause else if (job.state == UploadState.SUCCEEDED) Icons.Outlined.DeleteOutline else Icons.Rounded.PlayArrow, "Действие") }
        }
    }
}

@Composable
private fun SyncScreen(state: AppUiState, vm: AppViewModel) {
    val policy = state.autoUpload
    val folders = remember(state.localMedia) { state.localMedia.groupBy(LocalMediaCandidate::relativePath) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            FilledTonalButton(onClick = { vm.refreshAll(true) }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Icon(Icons.Rounded.Sync, null); Spacer(Modifier.width(8.dp)); Text(if (state.refreshing) "Синхронизация…" else "Синхронизировать сейчас") }
        }
        item { SettingSwitch("Автозагрузка", "Новые фото и видео попадут в Nextcloud", policy?.enabled == true) { enabled -> vm.updateAutoUpload { it.copy(enabled = enabled) } } }
        item { SettingSwitch("Только по Wi‑Fi", "Не расходовать мобильный интернет", policy?.wifiOnly != false) { enabled -> vm.updateAutoUpload { it.copy(wifiOnly = enabled) } } }
        item { SettingSwitch("Загружать видео", "Добавлять видео из галереи", policy?.includeVideos == true) { enabled -> vm.updateAutoUpload { it.copy(includeVideos = enabled) } } }
        item { SettingSwitch("Только при зарядке", "Полезно для большой первой загрузки", policy?.requireCharging == true) { enabled -> vm.updateAutoUpload { it.copy(requireCharging = enabled) } } }
        item { InfoCard("Последняя проверка", state.lastSyncEpochMs?.let(::formatTime) ?: "Ещё не выполнялась", "Медиа читаются из всех папок Nextcloud") }
        item { Text("Папки устройства", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        if (folders.isEmpty()) {
            item { InfoCard("Папки не прочитаны", "Откройте «На устройстве»", "Разрешите доступ к фото, после этого папки появятся здесь") }
        } else {
            items(folders.entries.toList(), key = { it.key }) { entry ->
                val checked = policy?.selectedFolders?.contains(entry.key) == true
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().clickable { vm.setFolderSynchronized(entry.key, !checked) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Folder, null, tint = CloudBlue)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.key.trim('/').substringAfterLast('/').ifBlank { "Камера" }, fontWeight = FontWeight.Medium)
                            Text("${entry.value.size} файлов", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = checked, onCheckedChange = { vm.setFolderSynchronized(entry.key, it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceScreen(items: List<LocalMediaCandidate>, vm: AppViewModel) {
    var selected by remember { mutableStateOf<LocalMediaCandidate?>(null) }
    var permissionRejected by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val requestedPermissions = remember { mediaPermissions() }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionRejected = result.values.none { it }
        vm.loadLocalMedia()
    }
    LaunchedEffect(Unit) {
        vm.loadLocalMedia()
        permissions.launch(requestedPermissions)
    }
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = RoundedCornerShape(28.dp), color = CloudBlue.copy(alpha = .12f)) { Icon(Icons.Outlined.Devices, null, tint = CloudBlue, modifier = Modifier.padding(24.dp).size(42.dp)) }
                Spacer(Modifier.height(18.dp)); Text("Нужен доступ к галерее", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Можно разрешить все фото либо только выбранные", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissions.launch(requestedPermissions) }) { Text("Разрешить доступ") }
                if (permissionRejected) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }) { Text("Открыть настройки приложения") }
                }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = vm::loadLocalMedia) { Text("Проверить снова") }
            }
        }
    }
    else LazyVerticalGrid(columns = GridCells.Adaptive(112.dp), contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items(items, key = { it.localKey }) { item -> LocalThumbnail(item, Modifier.aspectRatio(1f).clip(RoundedCornerShape(7.dp)).clickable { selected = item }) }
    }
    selected?.let { item ->
        Dialog(onDismissRequest = { selected = null }) {
            Card { Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) { LocalThumbnail(item, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)), previewEdge = 720); Spacer(Modifier.height(14.dp)); Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(12.dp)); Button(onClick = { vm.uploadLocal(item); selected = null }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Upload, null); Spacer(Modifier.width(8.dp)); Text("Загрузить в Nextcloud") } } }
        }
    }
}

private fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@Composable
private fun TrashScreen(items: List<TrashItem>, vm: AppViewModel) {
    if (items.isEmpty()) EmptyState(Icons.Outlined.DeleteOutline, "Корзина пуста", "Удалённые в Nextcloud файлы появятся здесь")
    else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.id }) { item ->
            Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (item.isFolder) Icons.Rounded.Folder else Icons.Rounded.Photo, null, tint = CloudBlue)
                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(item.originalLocation ?: formatBytes(item.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = { vm.restore(item) }) { Icon(Icons.Rounded.Restore, "Восстановить") }
                IconButton(onClick = { vm.deletePermanently(item) }) { Icon(Icons.Rounded.DeleteForever, "Удалить окончательно", tint = MaterialTheme.colorScheme.error) }
            } }
        }
    }
}

@Composable
private fun SettingsHub(state: AppUiState, vm: AppViewModel, animationKey: Int, onOpen: (Section) -> Unit) {
    val quota = state.profile?.quota
    val policy = state.autoUpload
    var showThemeDialog by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { LegacyHeader("Настройки", "Nextcloud · ${state.session?.account?.serverAddress?.value.orEmpty()}") }
        item {
            LegacyAccountCard(
                name = state.profile?.displayName ?: state.session?.account?.username.orEmpty(),
                used = quota?.usedBytes,
                total = quota?.totalBytes,
                animationKey = animationKey,
            )
        }
        item { LegacySectionTitle("Синхронизация") }
        item {
            LegacySettingsGroup {
                LegacySettingsToggle("Автозагрузка", "Фото и видео", Icons.Rounded.Upload, policy?.enabled == true) { enabled -> vm.updateAutoUpload { it.copy(enabled = enabled) } }
                LegacyDivider()
                LegacySettingsToggle("Только по Wi‑Fi", "Беречь мобильный трафик", Icons.Rounded.Sync, policy?.wifiOnly != false) { enabled -> vm.updateAutoUpload { it.copy(wifiOnly = enabled) } }
                LegacyDivider()
                LegacySettingsLink("Синхронизация", "Отдельный раздел", Icons.Outlined.Sync) { onOpen(Section.SYNC) }
            }
        }
        item { LegacySectionTitle("Разделы Nextcloud") }
        item {
            LegacySettingsGroup {
                listOf(Section.EVENTS, Section.MEDIA, Section.ALBUMS, Section.TRANSFERS, Section.DEVICE, Section.TRASH).forEachIndexed { index, section ->
                    LegacySettingsLink(section.title, when (section) {
                        Section.TRANSFERS -> "Очередь и прогресс"
                        Section.DEVICE -> "Локальная галерея"
                        Section.TRASH -> "Корзина Nextcloud"
                        else -> ""
                    }, section.icon) { onOpen(section) }
                    if (index != 5) LegacyDivider()
                }
            }
        }
        item { LegacySectionTitle("Папки для синхронизации") }
        if (state.localMedia.isNotEmpty()) {
            val folders = state.localMedia.groupBy(LocalMediaCandidate::relativePath).entries.take(8)
            folders.forEachIndexed { index, entry ->
                item(key = "sync-folder-${entry.key}") {
                    val checked = policy?.selectedFolders?.contains(entry.key) == true
                    val shape = when {
                        folders.size == 1 -> RoundedCornerShape(22.dp)
                        index == 0 -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                        index == folders.lastIndex -> RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
                        else -> RoundedCornerShape(0.dp)
                    }
                    Surface(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shape = shape,
                    ) {
                        Column {
                            LegacyLocalAlbumRow(
                                title = entry.key.trim('/').substringAfterLast('/').ifBlank { "Камера" },
                                media = entry.value.take(5),
                                checked = checked,
                            ) { vm.setFolderSynchronized(entry.key, !checked) }
                            if (index != folders.lastIndex) LegacyDivider()
                        }
                    }
                }
            }
        } else {
            item {
                LegacySettingsGroup {
                    LegacySettingsLink("Разрешить доступ к папкам", "Откройте локальную галерею", Icons.Outlined.Devices) { onOpen(Section.DEVICE) }
                }
            }
        }
        item { LegacySectionTitle("Аккаунт") }
        item { LegacySettingsGroup { LegacySettingsLink("Выйти из аккаунта", "App-password будет отозван", Icons.Outlined.Settings, vm::logout) } }
        item { LegacySectionTitle("Оформление") }
        item {
            LegacySettingsGroup {
                LegacySettingsLink("Тема", state.themeMode.russian(), Icons.Outlined.Settings) {
                    showThemeDialog = true
                }
            }
        }
        item { Text("Облако · версия 0.1.13", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth().padding(28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
    if (showThemeDialog) {
        Dialog(onDismissRequest = { showThemeDialog = false }) {
            Card(shape = RoundedCornerShape(25.dp)) {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text("Тема приложения", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                    AppThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                vm.setThemeMode(mode)
                                showThemeDialog = false
                            }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = state.themeMode == mode, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            Text(mode.russian(), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

private fun AppThemeMode.russian(): String = when (this) {
    AppThemeMode.SYSTEM -> "Как в системе"
    AppThemeMode.LIGHT -> "Светлая"
    AppThemeMode.DARK -> "Тёмная"
}

@Composable
private fun LegacyHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 13.dp, bottom = 13.dp)) {
        Text(title, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LegacyAccountCard(name: String, used: Long?, total: Long?, animationKey: Int) {
    val target = if (used != null && total != null && total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    val progress = remember { Animatable(0f) }
    LaunchedEffect(animationKey, target) {
        progress.snapTo(0f)
        progress.animateTo(target, tween(1100))
    }
    Surface(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(26.dp), shadowElevation = 7.dp) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { progress.value }, modifier = Modifier.fillMaxSize(), color = CloudBlue, trackColor = CloudBlue.copy(alpha = .12f), strokeWidth = 6.dp)
                Icon(Icons.Rounded.Cloud, null, tint = CloudBlue, modifier = Modifier.size(29.dp))
            }
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { "Личное облако" }, style = MaterialTheme.typography.titleLarge)
                Text(if (used != null && total != null) "${formatBytes(used)} из ${formatBytes(total)} занято" else "Квота загружается", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LegacySectionTitle(text: String) = Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 28.dp, top = 22.dp, bottom = 8.dp))

@Composable
private fun LegacySettingsGroup(content: @Composable () -> Unit) {
    Surface(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp), shadowElevation = 2.dp) { Column { content() } }
}

@Composable
private fun LegacySettingsLink(title: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        LegacySettingsIcon(icon); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge); if (value.isNotEmpty()) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .42f), modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun LegacySettingsToggle(title: String, value: String, icon: ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        LegacySettingsIcon(icon); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge); Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LegacySettingsIcon(icon: ImageVector) = Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(CloudBlue.copy(alpha = .1f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = CloudBlue, modifier = Modifier.size(20.dp)) }

@Composable
private fun LegacyDivider() = Spacer(Modifier.fillMaxWidth().padding(start = 62.dp).height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = .45f)))

@Composable
private fun SyncCloudOutline(enabled: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (enabled) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
        contentDescription = if (enabled) "Синхронизация включена" else "Синхронизация выключена",
        tint = if (enabled) CloudBlue else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun LegacyLocalAlbumRow(title: String, media: List<LocalMediaCandidate>, checked: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(7.dp)); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { media.forEach { LocalThumbnail(it, Modifier.size(34.dp, 27.dp).clip(RoundedCornerShape(8.dp)), previewEdge = 64) } } }
        SyncCloudOutline(enabled = checked, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked, onCheckedChange = onChange) } }
}

@Composable
private fun InfoCard(title: String, value: String, detail: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(detail, style = MaterialTheme.typography.bodySmall) } }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, subtitle: String, action: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(Modifier.padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) { Surface(shape = RoundedCornerShape(28.dp), color = CloudBlue.copy(alpha = .12f)) { Icon(icon, null, tint = CloudBlue, modifier = Modifier.padding(24.dp).size(42.dp)) }; Spacer(Modifier.height(18.dp)); Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant); action?.let { Spacer(Modifier.height(16.dp)); Button(onClick = it) { Text("Разрешить доступ") } } } }
}

@Composable
internal fun RemoteThumbnail(
    session: UserSession,
    item: MediaItem,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val app = LocalContext.current.applicationContext as GalleryApplication
    val bitmap by produceState<android.graphics.Bitmap?>(null, session.account.id, item.remotePath, item.etag) {
        value = withContext(Dispatchers.IO) {
            when (val result = app.mediaContentRepository.preview(session, item, 640, 640)) {
                is AppResult.Success -> BitmapFactory.decodeFile(result.value.file.absolutePath)
                is AppResult.Failure -> null
            }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), item.displayName, Modifier.fillMaxSize(), contentScale = contentScale) }
            ?: CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun LocalThumbnail(item: LocalMediaCandidate, modifier: Modifier = Modifier, previewEdge: Int = 160) {
    val resolver = LocalContext.current.contentResolver
    val cacheKey = "${item.localKey}:$previewEdge"
    val bitmap by produceState<android.graphics.Bitmap?>(LocalThumbnailCache.get(cacheKey), cacheKey) {
        value = value ?: withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(item.contentUri)
                val loaded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.loadThumbnail(uri, Size(previewEdge, previewEdge), null)
                } else {
                    resolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                }
                loaded?.also { LocalThumbnailCache.put(cacheKey, it) }
            }.getOrNull()
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { bitmap?.let { Image(it.asImageBitmap(), item.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Icon(Icons.Rounded.Photo, null) }
}

private object LocalThumbnailCache {
    private val cache = object : LruCache<String, android.graphics.Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int = value.allocationByteCount
    }
    fun get(key: String): android.graphics.Bitmap? = synchronized(cache) { cache.get(key) }
    fun put(key: String, bitmap: android.graphics.Bitmap) = synchronized(cache) { cache.put(key, bitmap) }
}

@Composable
private fun RemoteViewer(session: UserSession, media: List<MediaItem>, initial: Int, close: () -> Unit) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val pager = rememberPagerState(initialPage = initial, pageCount = { media.size })
            HorizontalPager(pager, Modifier.fillMaxSize()) { page -> RemoteThumbnail(session, media[page], Modifier.fillMaxSize()) }
            IconButton(onClick = close, modifier = Modifier.statusBarsPadding().padding(12.dp).align(Alignment.TopStart).background(Color.Black.copy(alpha = .45f), CircleShape)) { Icon(Icons.Rounded.Close, "Закрыть", tint = Color.White) }
            Text(media.getOrNull(pager.currentPage)?.displayName.orEmpty(), color = Color.White, modifier = Modifier.navigationBarsPadding().align(Alignment.BottomCenter).background(Color.Black.copy(alpha = .55f)).fillMaxWidth().padding(18.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatBytes(bytes: Long): String { if (bytes < 0) return "без лимита"; val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ"); var value = bytes.toDouble(); var unit = 0; while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }; return if (unit == 0) "$bytes ${units[unit]}" else "%.1f %s".format(value, units[unit]) }
private fun formatTime(epochMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
private fun UploadState.russian(): String = when (this) { UploadState.QUEUED -> "В очереди"; UploadState.RUNNING -> "Передаётся"; UploadState.PAUSED -> "На паузе"; UploadState.SUCCEEDED -> "Готово"; UploadState.FAILED -> "Ошибка" }
