package com.example.lottery.util

import com.example.lottery.data.model.DrawResult

/** Keeps recognized dates intact while excluding published draws from future choices. */
object DrawDateOptions {
    data class Item(val value: String, val drawn: Boolean?)
    data class Result(val items: List<Item>, val selected: String)

    fun build(
        upcoming: List<String>, draws: List<DrawResult>, current: String,
        keep: Boolean, issueAtRequest: String = current
    ): Result {
        val history = draws.map { it.draw_date.trim() }.filter { it.isNotEmpty() }
            .distinct().sortedDescending()
        val historySet = history.toSet()
        val future = upcoming.distinct().filterNot { it in historySet }
        val preserve = keep || current != issueAtRequest
        val preserved = current.trim().takeIf { preserve && it.isNotEmpty() }?.let { value ->
            draws.firstOrNull { it.issue == value && it.draw_date.isNotBlank() }?.draw_date?.trim() ?: value
        }
        val selected = preserved ?: future.firstOrNull() ?: history.firstOrNull().orEmpty()
        val items = future.map { Item(it, false) } + history.map { Item(it, true) }
        return Result(
            if (selected.isNotEmpty() && items.none { it.value == selected })
                listOf(Item(selected, null)) + items
            else items,
            selected
        )
    }
}
