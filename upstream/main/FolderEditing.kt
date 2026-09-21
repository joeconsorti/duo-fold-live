package com.mccal.folio

import java.util.UUID

private const val FOLDER_PREFIX = "folder:"

data class FolderEntry(val id: String, val title: String, val appIds: List<String>)

fun newFolderId(): String = FOLDER_PREFIX + UUID.randomUUID()
fun isReservedFolderId(id: String) = id.startsWith(FOLDER_PREFIX)
fun isFolderId(id: String) = id.startsWith(FOLDER_PREFIX) &&
    runCatching { UUID.fromString(id.removePrefix(FOLDER_PREFIX)) }.isSuccess

private fun HomeLayout.withoutShortcut(id: String) = copy(
    slots = slots.map { it?.takeUnless(id::equals) }.dropLastWhile { it == null },
    leadingSlots = leadingSlots.map { it?.takeUnless(id::equals) },
    dock = dock.map { it?.takeUnless(id::equals) },
)

fun createFolder(
    layout: HomeLayout,
    folder: FolderEntry,
    firstAppId: String,
    secondAppId: String,
    targetIndex: Int,
): HomeLayout {
    if (!isFolderId(folder.id) || layout.indexOfShortcut(folder.id) != null || layout.folder(folder.id) != null ||
        folder.title.isBlank() || firstAppId == secondAppId || listOf(firstAppId, secondAppId).any { it.isBlank() || isReservedFolderId(it) } ||
        layout.folders.any { existing -> firstAppId in existing.appIds || secondAppId in existing.appIds } ||
        homeCellPage(targetIndex) !in -1..layout.pageCount || targetIndex in widgetCellsForFolders(layout)) return layout
    val target = layout.slotAt(targetIndex)
    if (target != null && target != firstAppId && target != secondAppId) return layout
    var next = layout.withoutShortcut(firstAppId).withoutShortcut(secondAppId)
    next = next.withSlot(targetIndex, folder.id)
    return next.copy(
        folders = next.folders + folder.copy(appIds = listOf(firstAppId, secondAppId)))
}

fun renameFolder(layout: HomeLayout, folderId: String, title: String): HomeLayout {
    if (title.isBlank() || layout.folder(folderId) == null) return layout
    return layout.copy(folders = layout.folders.map { if (it.id == folderId) it.copy(title = title.trim()) else it })
}

fun addAppToFolder(layout: HomeLayout, folderId: String, appId: String, index: Int? = null): HomeLayout {
    if (appId.isBlank() || isReservedFolderId(appId)) return layout
    val sourceFolder = layout.folders.firstOrNull { appId in it.appIds }
    if (sourceFolder?.id == folderId) return layout
    if (layout.folder(folderId) == null) return layout
    val cleared = if (sourceFolder != null) removeAppFromFolder(layout, sourceFolder.id, appId, DropTarget.Remove)
        else layout.withoutShortcut(appId)
    val folder = cleared.folder(folderId) ?: return layout
    val insertion = (index ?: folder.appIds.size).coerceIn(0, folder.appIds.size)
    val members = folder.appIds.toMutableList().apply { add(insertion, appId) }
    return cleared.copy(folders = cleared.folders.map { if (it.id == folderId) it.copy(appIds = members) else it })
}

fun moveFolderApp(layout: HomeLayout, folderId: String, appId: String, index: Int): HomeLayout {
    val folder = layout.folder(folderId) ?: return layout
    val from = folder.appIds.indexOf(appId)
    if (from < 0 || index !in folder.appIds.indices || from == index) return layout
    val members = folder.appIds.toMutableList().apply { add(index, removeAt(from)) }
    return layout.copy(folders = layout.folders.map { if (it.id == folderId) it.copy(appIds = members) else it })
}

fun removeAppFromFolder(layout: HomeLayout, folderId: String, appId: String, target: DropTarget): HomeLayout {
    val folder = layout.folder(folderId) ?: return layout
    if (appId !in folder.appIds || target is DropTarget.Widget || target is DropTarget.Library) return layout
    val folderCell = layout.indexOfShortcut(folderId)
    val remaining = folder.appIds.filterNot(appId::equals)
    var next = when (remaining.size) {
        0 -> layout.withoutShortcut(folderId).copy(folders = layout.folders.filterNot { it.id == folderId })
        1 -> (folderCell?.let { layout.withSlot(it, remaining.single()) } ?: layout)
            .copy(folders = layout.folders.filterNot { it.id == folderId })
        else -> layout.copy(folders = layout.folders.map { if (it.id == folderId) it.copy(appIds = remaining) else it })
    }
    if (target == DropTarget.Remove) return next
    if (target is DropTarget.Home && target.index == folderCell) return layout
    val placed = dropApp(next, appId, target)
    return if (placed == next) layout else placed
}

fun reconcileFolders(layout: HomeLayout, removedAppIds: Set<String>): HomeLayout {
    var next = layout
    removedAppIds.forEach { appId ->
        next.folders.firstOrNull { appId in it.appIds }?.let { folder ->
            next = removeAppFromFolder(next, folder.id, appId, DropTarget.Remove)
        }
    }
    // Dissolving a folder can promote its final child back into the folder's cell. When
    // several children disappear in the same refresh, remove every authoritative ID once
    // more after all folder transitions so iteration order cannot resurrect a shortcut.
    return removedAppIds.fold(next) { current, appId -> current.withoutShortcut(appId) }
}

private fun widgetCellsForFolders(layout: HomeLayout) = layout.widgetPlacements
    .flatMapTo(mutableSetOf()) { it.coveredIndices() }
