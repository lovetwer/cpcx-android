package com.example.lottery.util

import com.example.lottery.data.model.DrawResult
import org.junit.Assert.*
import org.junit.Test

class DrawDateOptionsTest {
    private val upcoming = listOf("2026-11-10", "2026-11-12", "2026-11-15")
    private val today = DrawResult(issue = "2026130", draw_date = "2026-11-10")

    @Test fun publishedTodayIsNotOfferedAsUpcoming() {
        val result = DrawDateOptions.build(upcoming, listOf(today, today), "", false)
        assertEquals("2026-11-12", result.selected)
        assertEquals(1, result.items.count { it.value == "2026-11-10" })
        assertEquals(true, result.items.first { it.value == "2026-11-10" }.drawn)
    }

    @Test fun unpublishedTodayRemainsSelectable() {
        val result = DrawDateOptions.build(upcoming, emptyList(), "", false)
        assertEquals("2026-11-10", result.selected)
        assertEquals(false, result.items.first().drawn)
    }

    @Test fun recognizedDateOutsideHistoryIsPreserved() {
        val result = DrawDateOptions.build(upcoming, listOf(today), "2025-01-02", true)
        assertEquals("2025-01-02", result.selected)
        assertEquals("2025-01-02", result.items.first().value)
        assertNull(result.items.first().drawn)
    }

    @Test fun recognizedIssueMapsToKnownDrawDate() {
        val result = DrawDateOptions.build(upcoming, listOf(today), "2026130", true)
        assertEquals("2026-11-10", result.selected)
    }

    @Test fun unknownIssueIsNotReplacedWithFutureDate() {
        val result = DrawDateOptions.build(upcoming, listOf(today), "2025001", true)
        assertEquals("2025001", result.selected)
        assertNull(result.items.first().drawn)
    }

    @Test fun recognizedValueSurvivesEmptyApiResults() {
        val result = DrawDateOptions.build(upcoming, emptyList(), "2025-01-02", true)
        assertEquals("2025-01-02", result.selected)
    }

    @Test fun inputEditedDuringRequestSurvivesEmptyResponse() {
        val result = DrawDateOptions.build(
            upcoming, emptyList(), "2025-01-02", false, issueAtRequest = "2026-11-10"
        )
        assertEquals("2025-01-02", result.selected)
    }

    @Test fun switchingTypeDoesNotKeepOldSelection() {
        val result = DrawDateOptions.build(upcoming, listOf(today), "2025-01-02", false)
        assertEquals("2026-11-12", result.selected)
        assertFalse(result.items.any { it.value == "2025-01-02" })
    }
}
