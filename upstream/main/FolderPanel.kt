package org.duofold.live

import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch

@Composable
internal fun FolderPanel(
    folder: FolderEntry, apps: Map<String, AppEntry>, drag: HomeDragState, page: Int,
    homeDestinations: List<Int>, dockVacancies: List<Int>, onDismiss: () -> Unit,
    onRename: (String) -> Unit, onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onMoveOut: (String, DropTarget) -> Unit,
    color: Long? = null, onColor: (Long?) -> Unit = {},
) {
    var title by rememberSaveable(folder.id) { mutableStateOf(folder.title) }
    // Zoom in from the folder's tile on Home and back into it on close, like iPhone folders.
    val appear = remember(folder.id) { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember(folder.id) { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closing) { closing = true; scope.launch {
            appear.animateTo(0f, MotionSpeed.spring(1f, 700f)); onDismiss()
        } }
    }
    val tile = remember(folder.id) { IconBounds.of(folder.id) }
    var panelBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    BackHandler { close() }
    DisposableEffect(folder.id) { onDispose { if (title.isNotBlank() && title != folder.title) onRename(title) } }
    DisposableEffect(drag, folder.id) {
        drag.activeSourceScope = folder.id
        onDispose { if (drag.activeSourceScope == folder.id) drag.activeSourceScope = null }
    }
    LaunchedEffect(folder.id) { appear.animateTo(1f, MotionSpeed.spring(.78f, androidx.compose.animation.core.Spring.StiffnessMediumLow)) }
    val folderLook = LocalFolderLook.current
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }.background(FolioGlass.scrim)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClickLabel = "Close folder",
            onClick = close,
        )
        .imePadding().testTag("folder-panel"),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
            .onGloballyPositioned { panelBounds = it.boundsInWindow() }
            .graphicsLayer {
                val p = appear.value
                if (tile != null && panelBounds.width > 0f) {
                    val start = (tile.width() / panelBounds.width).coerceIn(.08f, 1f)
                    val s = start + (1f - start) * p; scaleX = s; scaleY = s
                    translationX = (tile.exactCenterX() - panelBounds.center.x) * (1f - p)
                    translationY = (tile.exactCenterY() - panelBounds.center.y) * (1f - p)
                    alpha = (p * 1.8f).coerceIn(0f, 1f)
                } else { val s = .86f + .14f * p; scaleX = s; scaleY = s }
            }) {
        androidx.compose.foundation.text.BasicTextField(title, { title = it },
            Modifier.widthIn(max = 420.dp).padding(bottom = 18.dp).testTag("folder-name"), singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (title.isNotBlank()) onRename(title) }))
        // Folder tint: none + a few iOS-like colors.
        androidx.compose.foundation.layout.Row(Modifier.padding(bottom = 14.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            (listOf<Long?>(null) + FolderSwatches).forEach { swatch ->
                val selected = swatch == color
                Box(Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(swatch?.let { Color(it) } ?: Color.White.copy(alpha = .18f))
                    .then(if (selected) Modifier.border(2.5.dp, Color.White, androidx.compose.foundation.shape.CircleShape) else Modifier)
                    .clickable(onClickLabel = if (swatch == null) "No folder color" else "Folder color") { onColor(swatch) })
            }
        }
        Surface(Modifier.fillMaxWidth(.86f).widthIn(max = 520.dp).fillMaxHeight(.7f).heightIn(min = 240.dp, max = 560.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .testTag("folder-panel-content"),
            color = when (folderLook.background) {
                FolderBackground.GLASS -> FolioGlass.card
                FolderBackground.SOLID -> Color(0xFF1C1C1E)
                FolderBackground.CLEAR -> Color.Transparent
            }, contentColor = Color.White, shape = RoundedCornerShape(38.dp),
            border = if (folderLook.background == FolderBackground.CLEAR) null else FolioGlass.edge) {
            Column(Modifier.padding(20.dp)) {
                val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                LazyVerticalGrid(if (folderLook.columns > 0) FolderColumns(folderLook.columns) else GridCells.Adaptive(84.dp), Modifier.fillMaxWidth().weight(1f).edgeFade(gridState), state = gridState,
                    contentPadding = PaddingValues(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(folder.appIds, key = { it }) { appId ->
                        apps[appId]?.let { app -> FolderChild(app, folder.id, drag, page, homeDestinations, dockVacancies,
                            onLaunch = onLaunch, onMoveOut = onMoveOut) }
                    }
                }
            }
        }
        }
    }
}

/** The chosen number of columns, but never cells too narrow for an icon and its name (small cover screens). */
private data class FolderColumns(val columns: Int) : GridCells {
    override fun androidx.compose.ui.unit.Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val count = columns.coerceAtMost(((availableSize + spacing) / (76.dp.roundToPx() + spacing)).coerceAtLeast(1))
        val cells = availableSize - spacing * (count - 1)
        return List(count) { cells / count + if (it < cells % count) 1 else 0 }
    }
}

@Composable
private fun FolderChild(
    app: AppEntry, folderId: String, drag: HomeDragState, page: Int,
    homeDestinations: List<Int>, dockVacancies: List<Int>,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit, onMoveOut: (String, DropTarget) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().testTag("folder-child-${app.id}"), color = Color.Transparent, contentColor = Color.White,
        shape = RoundedCornerShape(18.dp)) {
        Box {
            Column(Modifier.fillMaxWidth().dropRegion(drag, DropTarget.Library(app.id), app.id, page,
                folderId = folderId, scope = folderId).clickable(enabled = app.available) { onLaunch(app, null) }
                .padding(horizontal = 6.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AppIcon(app, null, Modifier.size(58.dp), shape = RoundedCornerShape(14.dp))
                Text(app.label, Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium)
                if (app.isWork || !app.available) Text(if (app.available) app.profileLabel else "${app.profileLabel} unavailable",
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { menu = true }, Modifier.align(Alignment.TopEnd).size(36.dp)
                .testTag("folder-options-${app.id}")) { Icon(Icons.Rounded.MoreVert, "Move ${app.label}") }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                homeDestinations.distinctBy(::homeCellPage).forEach { destination ->
                    val destinationPage = homeCellPage(destination)
                    val label = if (destinationPage == -1) "Move to Unfolded-only page" else "Move to page ${destinationPage + 1}"
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        menu = false; onMoveOut(app.id, DropTarget.Home(destination))
                    }, modifier = Modifier.testTag("folder-move-${app.id}-page-$destinationPage"))
                }
                dockVacancies.firstOrNull()?.let { dock ->
                    DropdownMenuItem(text = { Text(stringResource(R.string.move_to_dock)) }, onClick = {
                        menu = false; onMoveOut(app.id, DropTarget.Dock(dock))
                    }, modifier = Modifier.testTag("folder-move-${app.id}-dock"))
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.remove_shortcut)) }, onClick = {
                    menu = false; onMoveOut(app.id, DropTarget.Remove)
                }, modifier = Modifier.testTag("folder-remove-${app.id}"))
            }
        }
    }
}

private val FolderSwatches = listOf(0xFFFF6B63, 0xFFFFA94D, 0xFFFFD84D, 0xFF63D98B, 0xFF4DB8FF, 0xFF8E7CFF, 0xFFFF7EB9)
