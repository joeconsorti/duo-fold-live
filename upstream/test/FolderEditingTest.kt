package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderEditingTest {
    private val folderId = "folder:123e4567-e89b-12d3-a456-426614174000"

    @Test fun createFolderUsesTargetCellWithoutMovingUnrelatedApps() {
        val before = HomeLayout(listOf("personal", "other", "work"), listOf(null, "dock", null, null))
        val next = createFolder(before, FolderEntry(folderId, "Daily", emptyList()), "personal", "work", 0)
        assertEquals(listOf(folderId, "other"), next.slots)
        assertEquals(listOf("personal", "work"), next.folder(folderId)?.appIds)
        assertEquals(before.dock, next.dock)
    }

    @Test fun createFolderRejectsWidgetCollisionAndNestedFolder() {
        val widget = WidgetPlacement(7, 100, 0, 0, 0, 2, 2)
        val before = HomeLayout(listOf("a", "b"), emptyList(), listOf(widget))
        assertSame(before, createFolder(before, FolderEntry(folderId, "Group", emptyList()), "a", "b", 0))
        assertSame(before, createFolder(before, FolderEntry(folderId, "Group", emptyList()), folderId, "b", 2))
    }

    @Test fun addingFromDockTransfersShortcutAndPreventsDuplicateMembership() {
        val folder = FolderEntry(folderId, "Group", listOf("a", "b"))
        val before = HomeLayout(listOf(folderId), listOf("work", null), folders = listOf(folder))
        val next = addAppToFolder(before, folderId, "work")
        assertEquals(listOf(null, null), next.dock)
        assertEquals(listOf("a", "b", "work"), next.folder(folderId)?.appIds)
        assertSame(next, addAppToFolder(next, folderId, "work"))
    }

    @Test fun extractingFromTwoItemFolderDissolvesAtSameCell() {
        val work = profileAppId("com.same/.Main", 42, 10)
        val before = HomeLayout(listOf(null, folderId), listOf(null, null), folders = listOf(
            FolderEntry(folderId, "Pair", listOf("personal", work))))
        val next = removeAppFromFolder(before, folderId, work, DropTarget.Dock(0))
        assertEquals(listOf(null, "personal"), next.slots)
        assertEquals(listOf(work, null), next.dock)
        assertTrue(next.folders.isEmpty())
    }

    @Test fun movingChildBetweenFoldersDissolvesSourceAndKeepsOneUniqueMembership() {
        val secondId = "folder:123e4567-e89b-12d3-a456-426614174001"
        val before = HomeLayout(listOf(folderId, secondId), emptyList(), folders = listOf(
            FolderEntry(folderId, "First", listOf("a", "moving")),
            FolderEntry(secondId, "Second", listOf("b", "c"))))
        val next = addAppToFolder(before, secondId, "moving", 1)
        assertEquals(listOf("a", secondId), next.slots)
        assertEquals(listOf("b", "moving", "c"), next.folder(secondId)?.appIds)
        assertEquals(1, next.folders.sumOf { folder -> folder.appIds.count("moving"::equals) })
    }

    @Test fun simultaneousRemovalCannotResurrectLastDissolvedChild() {
        val before = HomeLayout(listOf("outside", folderId), listOf("dock", null), folders = listOf(
            FolderEntry(folderId, "Pair", listOf("removed-a", "removed-b"))))
        val next = reconcileFolders(before, setOf("removed-a", "removed-b"))
        assertEquals(listOf("outside"), next.slots)
        assertEquals(listOf("dock", null), next.dock)
        assertTrue(next.folders.isEmpty())
    }

    @Test fun rejectedExtractionFromFullDockPreservesEveryFolderChild() {
        val folder = FolderEntry(folderId, "Pair", listOf("a", "b"))
        val before = HomeLayout(listOf(folderId), listOf("d0", "d1", "d2", "d3"), folders = listOf(folder))
        assertSame(before, removeAppFromFolder(before, folderId, "b", DropTarget.Dock(0)))
        assertEquals(setOf("a", "b", "d0", "d1", "d2", "d3"),
            (before.folders.flatMap { it.appIds } + before.dock.filterNotNull()).toSet())
    }

    @Test fun folderEditSnapshotsRoundTripWithoutChangingWidgetsOrGaps() {
        val widget = WidgetPlacement(9, 99, 0, 2, 2, 2, 2)
        val before = HomeLayout(listOf("a", null, "b", null, "other"), listOf(null, "dock"), listOf(widget))
        val after = createFolder(before, FolderEntry(folderId, "Group", emptyList()), "a", "b", 0)
        assertEquals(before, before.copy())
        assertEquals(widget, after.widgetPlacements.single())
        assertEquals("other", after.slots[4])
    }
    @Test fun aFolderKeepsTakingAppsPastTwo() {
        // Reported: only two apps could be put in a folder. The model must accept any number, from Home or anywhere.
        var layout = HomeLayout(listOf(folderId, "c", "d"), listOf(null), folders = listOf(FolderEntry(folderId, "Group", listOf("a", "b"))))
        layout = addAppToFolder(layout, folderId, "c")
        layout = addAppToFolder(layout, folderId, "d")
        layout = addAppToFolder(layout, folderId, "e")
        assertEquals(listOf("a", "b", "c", "d", "e"), layout.folder(folderId)?.appIds)
        assertEquals(listOf(folderId), layout.slots.filterNotNull())
    }
}
