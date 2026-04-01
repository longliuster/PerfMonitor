package com.perfmonitor

import java.io.File

/**
 * Reads DDR (memory bus) frequency from sysfs devfreq nodes.
 */
object DdrFreqReader {

    // Common DDR devfreq paths on various SoCs
    private val DDR_PATHS = listOf(
        "/sys/class/devfreq/soc:qcom,cpu-llcc-ddr-bw/cur_freq",
        "/sys/class/devfreq/soc:qcom,cpu-cpu-llcc-bw/cur_freq",
        "/sys/class/devfreq/ddrfreq/cur_freq",
        "/sys/class/devfreq/mtk-dvfsrc-devfreq/cur_freq",
        "/sys/class/devfreq/1c00000.qcom,bwmon-ddr/cur_freq",
        "/sys/class/devfreq/soc:qcom,memlat-cpu0/cur_freq",
        "/sys/class/devfreq/18321110.dvfsrc-devfreq/cur_freq",
        "/sys/class/devfreq/10012000.dvfsrc-devfreq/cur_freq",
    )

    private var cachedPath: String? = null

    /**
     * Finds the DDR devfreq path by scanning /sys/class/devfreq/
     */
    private fun findDdrPath(): String? {
        // Try cached path first
        cachedPath?.let {
            if (File(it).exists()) return it
        }

        // Try known paths
        for (path in DDR_PATHS) {
            if (File(path).exists() && File(path).canRead()) {
                cachedPath = path
                return path
            }
        }

        // Scan devfreq directory for DDR-related nodes
        val devfreqDir = File("/sys/class/devfreq/")
        if (devfreqDir.exists()) {
            val ddrNode = devfreqDir.listFiles()?.firstOrNull { dir ->
                val name = dir.name.lowercase()
                name.contains("ddr") || name.contains("dvfsrc") || name.contains("bw")
            }
            if (ddrNode != null) {
                val freqFile = File(ddrNode, "cur_freq")
                if (freqFile.exists() && freqFile.canRead()) {
                    cachedPath = freqFile.absolutePath
                    return cachedPath
                }
            }
        }

        return null
    }

    /**
     * Returns DDR frequency in MHz, or 0 if unavailable.
     */
    fun readDdrFreqMHz(): Int {
        val path = findDdrPath() ?: return 0
        return try {
            val hz = File(path).readText().trim().toLongOrNull() ?: return 0
            // devfreq reports in Hz or KHz depending on SoC
            when {
                hz > 1_000_000_000 -> (hz / 1_000_000).toInt()  // Hz -> MHz
                hz > 1_000_000 -> (hz / 1_000).toInt()           // KHz -> MHz
                hz > 1_000 -> hz.toInt()                          // Already MHz
                else -> hz.toInt()
            }
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Returns formatted DDR frequency string.
     */
    fun readFormatted(): String {
        val mhz = readDdrFreqMHz()
        return if (mhz > 0) "${mhz}MHz" else "N/A"
    }
}
