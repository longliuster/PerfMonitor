package com.perfmonitor

import java.io.File

/**
 * Reads CPU frequency from sysfs.
 * Reports the max frequency among all online CPU cores (big core cluster).
 */
object CpuFreqReader {

    /**
     * Returns current CPU frequencies for all cores as a map of core index to frequency in MHz.
     */
    fun readAllCoreFreqs(): Map<Int, Int> {
        val freqs = mutableMapOf<Int, Int>()
        val cpuDir = File("/sys/devices/system/cpu/")
        val cpuDirs = cpuDir.listFiles { file ->
            file.isDirectory && file.name.matches(Regex("cpu\\d+"))
        } ?: return freqs

        for (dir in cpuDirs) {
            val coreIndex = dir.name.removePrefix("cpu").toIntOrNull() ?: continue
            val freqFile = File(dir, "cpufreq/scaling_cur_freq")
            if (freqFile.exists() && freqFile.canRead()) {
                try {
                    val khz = freqFile.readText().trim().toLongOrNull() ?: continue
                    freqs[coreIndex] = (khz / 1000).toInt() // Convert KHz to MHz
                } catch (_: Exception) {
                }
            }
        }
        return freqs
    }

    /**
     * Returns the max CPU frequency among all cores in MHz.
     */
    fun readMaxCoreFreqMHz(): Int {
        val freqs = readAllCoreFreqs()
        return freqs.values.maxOrNull() ?: 0
    }

    /**
     * Returns a formatted string showing big/mid/little core frequencies.
     * Tries to group cores by their max frequency capability.
     */
    fun readFormattedFreqs(): String {
        val freqs = readAllCoreFreqs()
        if (freqs.isEmpty()) return "N/A"

        val sorted = freqs.entries.sortedBy { it.key }
        val values = sorted.map { it.value }

        // Show max frequency
        val maxFreq = values.maxOrNull() ?: 0
        return "${maxFreq}MHz"
    }

    /**
     * Returns detailed per-cluster frequency info.
     * Groups cores by their current frequency similarity.
     */
    fun readClusterFreqs(): String {
        val freqs = readAllCoreFreqs()
        if (freqs.isEmpty()) return "CPU: N/A"

        val sorted = freqs.entries.sortedBy { it.key }

        // Group consecutive cores with same frequency
        val clusters = mutableListOf<Pair<List<Int>, Int>>()
        var currentCores = mutableListOf(sorted[0].key)
        var currentFreq = sorted[0].value

        for (i in 1 until sorted.size) {
            val (core, freq) = sorted[i]
            if (freq == currentFreq) {
                currentCores.add(core)
            } else {
                clusters.add(currentCores.toList() to currentFreq)
                currentCores = mutableListOf(core)
                currentFreq = freq
            }
        }
        clusters.add(currentCores.toList() to currentFreq)

        return clusters.joinToString(" | ") { (cores, freq) ->
            "${freq}M"
        }
    }
}
