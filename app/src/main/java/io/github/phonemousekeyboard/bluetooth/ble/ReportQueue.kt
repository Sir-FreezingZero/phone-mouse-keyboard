package io.github.phonemousekeyboard.bluetooth.ble

import io.github.phonemousekeyboard.bluetooth.HidDescriptor

/** One input report waiting to be sent. [data] excludes the report ID. */
internal class QueuedReport(val reportId: Int, val data: ByteArray)

/**
 * Holds input reports until Bluetooth is ready for the next one.
 *
 * Bluetooth Low Energy sends one notification at a time, while a finger on the trackpad
 * produces movement faster than that. Consecutive mouse movements are therefore merged into
 * one report, so the pointer ends up in the same place without a growing backlog. Likewise a
 * gamepad report waiting to be sent is replaced by a newer one if only the sticks changed,
 * since each holds the whole gamepad state. Button and key presses are never merged or
 * dropped, since every press matters.
 */
internal class ReportQueue {

    private val reports = ArrayDeque<QueuedReport>()

    val isEmpty: Boolean get() = reports.isEmpty()

    fun add(report: QueuedReport) {
        val last = reports.lastOrNull()
        if (last != null && canMerge(last, report)) {
            reports[reports.lastIndex] = merge(last, report)
            return
        }
        if (last != null && isNewerStickPosition(last, report)) {
            reports[reports.lastIndex] = report
            return
        }
        if (reports.size >= MAX_QUEUED_REPORTS) {
            dropOldestMouseReport()
        }
        reports.addLast(report)
    }

    fun poll(): QueuedReport? = reports.removeFirstOrNull()

    /** Puts a report that couldn't be sent back at the front, to retry it first. */
    fun pushBack(report: QueuedReport) = reports.addFirst(report)

    fun clear() = reports.clear()

    /**
     * Two mouse reports merge when the buttons are the same (so clicks and drags keep their
     * order) and the combined movement still fits in one report.
     */
    private fun canMerge(first: QueuedReport, second: QueuedReport): Boolean {
        if (first.reportId != HidDescriptor.MOUSE_REPORT_ID) return false
        if (second.reportId != HidDescriptor.MOUSE_REPORT_ID) return false
        if (first.data[BUTTONS] != second.data[BUTTONS]) return false
        return MOVEMENT_BYTES.all { index ->
            (first.data[index] + second.data[index]) in -MAX_DELTA..MAX_DELTA
        }
    }

    private fun merge(first: QueuedReport, second: QueuedReport): QueuedReport {
        val merged = first.data.copyOf()
        for (index in MOVEMENT_BYTES) {
            merged[index] = (first.data[index] + second.data[index]).toByte()
        }
        return QueuedReport(first.reportId, merged)
    }

    /**
     * Whether [second] only moves the gamepad's sticks compared to [first], so [first] can be
     * skipped: buttons and D-pad are the same, and the stick positions are absolute.
     */
    private fun isNewerStickPosition(first: QueuedReport, second: QueuedReport): Boolean {
        if (first.reportId != HidDescriptor.GAMEPAD_REPORT_ID) return false
        if (second.reportId != HidDescriptor.GAMEPAD_REPORT_ID) return false
        return GAMEPAD_BUTTON_BYTES.all { index -> first.data[index] == second.data[index] }
    }

    /** Last resort if the link stalls: lose some pointer movement rather than key presses. */
    private fun dropOldestMouseReport() {
        val index = reports.indexOfFirst { it.reportId == HidDescriptor.MOUSE_REPORT_ID }
        if (index >= 0) reports.removeAt(index)
    }

    private companion object {
        const val MAX_QUEUED_REPORTS = 64
        const val MAX_DELTA = 127

        /** Byte positions in a mouse report, see [HidDescriptor]. */
        const val BUTTONS = 0
        val MOVEMENT_BYTES = 1..3

        /** Byte positions of the buttons and D-pad in a gamepad report, see [HidDescriptor]. */
        val GAMEPAD_BUTTON_BYTES = 0..HidDescriptor.GAMEPAD_HAT_BYTE
    }
}
