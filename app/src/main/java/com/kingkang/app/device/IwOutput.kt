package com.kingkang.app.device

/**
 * Parsers for `iw dev` / `iw phy … info` output. They are pure so the capability display
 * is unit-tested against real iw text instead of a hand-written boolean.
 */
object IwOutput {

    data class Interface(val name: String, val phy: String, val type: String)

    private val PHY = Regex("^\\s*phy#(\\d+)\\s*$")
    private val INTERFACE = Regex("^\\s*Interface\\s+(\\S+)\\s*$")
    private val TYPE = Regex("^\\s*type\\s+(\\S+)\\s*$")

    fun parseDev(text: String): List<Interface> {
        val result = mutableListOf<Interface>()
        var phy = ""
        var currentIndex = -1
        for (line in text.lineSequence()) {
            PHY.find(line)?.let {
                phy = "phy${it.groupValues[1]}"
                currentIndex = -1
                return@let
            }
            INTERFACE.find(line)?.let {
                result += Interface(it.groupValues[1], phy, "unknown")
                currentIndex = result.lastIndex
                return@let
            }
            TYPE.find(line)?.let { match ->
                if (currentIndex >= 0) {
                    result[currentIndex] = result[currentIndex].copy(type = match.groupValues[1])
                }
            }
        }
        return result
    }

    /** `iw phy` lists the modes under "Supported interface modes"; monitor must be one. */
    fun supportsMonitor(text: String): Boolean {
        var inModes = false
        for (line in text.lineSequence()) {
            if (line.contains("Supported interface modes", ignoreCase = true)) {
                inModes = true
                continue
            }
            if (inModes) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                if (!trimmed.startsWith("*")) return false
                if (trimmed.removePrefix("*").trim() == "monitor") return true
            }
        }
        return false
    }

    /** Only `phyN` reaches a shell command; anything else is rejected by the caller. */
    fun isPhyName(value: String): Boolean = Regex("^phy\\d+$").matches(value)
}
