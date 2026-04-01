package com.perfmonitor

import java.io.File
import java.io.RandomAccessFile

/**
 * Reads CPU usage by parsing /proc/stat.
 * Calculates per-core and total CPU loading percentage.
 */
object CpuLoadReader {

    private var prevTotal = LongArray(0)
    private var prevIdle = LongArray(0)
    private var prevTotalAll = 0L
    private var prevIdleAll = 0L

    /**
     * Reads /proc/stat and returns CPU usage percentages.
     * First call returns 0 (needs two samples to calculate delta).
     * Returns: Pair(totalCpuPercent, perCorePercents)
     */
    fun readCpuLoad(): Pair<Int, List<Int>> {
        try {
            val lines = File("/proc/stat").readLines()
            val coreLoads = mutableListOf<Int>()

            // Parse total CPU line (first line: "cpu  ...")
            val totalLine = lines.firstOrNull { it.startsWith("cpu ") } ?: return 0 to emptyList()
            val totalParts = totalLine.trim().split("\\s+".toRegex()).drop(1).map { it.toLong() }
            val totalAll = totalParts.sum()
            val idleAll = if (totalParts.size > 3) totalParts[3] else 0L

            val totalPercent = if (prevTotalAll > 0) {
                val dt = totalAll - prevTotalAll
                val di = idleAll - prevIdleAll
                if (dt > 0) ((dt - di) * 100 / dt).toInt().coerceIn(0, 100) else 0
            } else 0

            // Parse per-core lines ("cpu0 ...", "cpu1 ...", etc.)
            val coreLines = lines.filter { it.matches(Regex("cpu\\d+\\s+.*")) }
            val newTotal = LongArray(coreLines.size)
            val newIdle = LongArray(coreLines.size)

            for ((i, line) in coreLines.withIndex()) {
                val parts = line.trim().split("\\s+".toRegex()).drop(1).map { it.toLong() }
                val t = parts.sum()
                val idle = if (parts.size > 3) parts[3] else 0L
                newTotal[i] = t
                newIdle[i] = idle

                val load = if (i < prevTotal.size && prevTotal[i] > 0) {
                    val dt = t - prevTotal[i]
                    val di = idle - prevIdle[i]
                    if (dt > 0) ((dt - di) * 100 / dt).toInt().coerceIn(0, 100) else 0
                } else 0
                coreLoads.add(load)
            }

            prevTotalAll = totalAll
            prevIdleAll = idleAll
            prevTotal = newTotal
            prevIdle = newIdle

            return totalPercent to coreLoads
        } catch (_: Exception) {
            return 0 to emptyList()
        }
    }

    /**
     * Returns formatted CPU load string.
     */
    fun readFormatted(): String {
        val (total, cores) = readCpuLoad()
        if (cores.isEmpty()) return "N/A"
        return "${total}%"
    }

    /**
     * Returns formatted per-core load string.
     */
    fun readPerCoreFormatted(): String {
        val (_, cores) = readCpuLoad()
        if (cores.isEmpty()) return "N/A"
        return cores.mapIndexed { i, load -> "C$i:${load}%" }.joinToString(" ")
    }
}
