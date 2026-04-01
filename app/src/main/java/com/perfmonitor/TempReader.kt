package com.perfmonitor

import java.io.File

/**
 * Reads CPU and device temperatures from thermal zones and HardwarePropertiesManager.
 */
object TempReader {

    /**
     * Try reading thermal zones directly from sysfs.
     * Returns map of zone name to temperature in Celsius.
     */
    private fun readThermalZones(): Map<String, Float> {
        val temps = mutableMapOf<String, Float>()
        val thermalDir = File("/sys/class/thermal/")
        if (!thermalDir.exists()) return temps

        val zones = thermalDir.listFiles { f -> f.name.startsWith("thermal_zone") } ?: return temps
        for (zone in zones) {
            try {
                val typeFile = File(zone, "type")
                val tempFile = File(zone, "temp")
                if (!tempFile.canRead()) continue

                val name = if (typeFile.canRead()) typeFile.readText().trim() else zone.name
                val rawTemp = tempFile.readText().trim().toLongOrNull() ?: continue

                // Temperature is usually in millidegrees Celsius
                val celsius = if (rawTemp > 1000) rawTemp / 1000f else rawTemp.toFloat()
                temps[name] = celsius
            } catch (_: Exception) {
            }
        }
        return temps
    }

    /**
     * Returns CPU temperature in Celsius.
     * Tries multiple sources: thermal zones, then /proc/cpuinfo.
     */
    fun readCpuTemp(): Float {
        val zones = readThermalZones()

        // Look for CPU-related thermal zones
        val cpuKeys = listOf("cpu-0-0", "cpu-1-0", "cpu_therm", "soc_thermal",
            "mtktscpu", "tsens_tz_sensor0", "cpu-0-0-usr")
        for (key in cpuKeys) {
            zones[key]?.let { return it }
        }

        // Fallback: find any zone with "cpu" in name
        zones.entries.firstOrNull { it.key.contains("cpu", ignoreCase = true) }
            ?.let { return it.value }

        // Fallback: find soc thermal
        zones.entries.firstOrNull { it.key.contains("soc", ignoreCase = true) }
            ?.let { return it.value }

        return 0f
    }

    /**
     * Returns battery temperature in Celsius.
     */
    fun readBatteryTemp(): Float {
        val zones = readThermalZones()
        zones.entries.firstOrNull { it.key.contains("battery", ignoreCase = true) }
            ?.let { return it.value }
        return 0f
    }

    /**
     * Returns formatted CPU temperature string.
     */
    fun readCpuTempFormatted(): String {
        val temp = readCpuTemp()
        return if (temp > 0) String.format("%.1f°C", temp) else "N/A"
    }

    /**
     * Returns formatted battery temperature string.
     */
    fun readBatteryTempFormatted(): String {
        val temp = readBatteryTemp()
        return if (temp > 0) String.format("%.1f°C", temp) else "N/A"
    }
}
