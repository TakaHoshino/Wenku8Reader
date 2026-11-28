package com.hoshino.wenku8reader.ui.navigation

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowLayoutTest {

    @Test
    fun windowWidthClass_usesMaterialBreakpoints() {
        assertEquals(WindowWidthClass.COMPACT, windowWidthClass(599.dp))
        assertEquals(WindowWidthClass.MEDIUM, windowWidthClass(600.dp))
        assertEquals(WindowWidthClass.MEDIUM, windowWidthClass(839.dp))
        assertEquals(WindowWidthClass.EXPANDED, windowWidthClass(840.dp))
    }

    @Test
    fun onlyCompactWindowsUseBottomNavigation() {
        assertFalse(WindowWidthClass.COMPACT.usesSideNavigation)
        assertTrue(WindowWidthClass.MEDIUM.usesSideNavigation)
        assertTrue(WindowWidthClass.EXPANDED.usesSideNavigation)
    }

    @Test
    fun readerGutter_centersTextOnWideWindows() {
        assertEquals(0.dp, readerHorizontalGutter(760.dp))
        assertEquals(120.dp, readerHorizontalGutter(1000.dp))
        assertEquals(220.dp, readerHorizontalGutter(1200.dp))
    }

    @Test
    fun reader_usesTwoColumnsOnlyAtExpandedWidth() {
        assertEquals(1, readerColumnCount(839.dp))
        assertEquals(2, readerColumnCount(840.dp))
        assertEquals(2, readerColumnCount(1280.dp))
    }
}
