package com.spacegoose.carlauncher.vehicle.obd

/** What the car reported over OBD-II. Null fields are ones the car hasn't answered (or doesn't support). */
data class ObdReading(
    val rpm: Int? = null,
    val speedKmh: Int? = null,
    val coolantC: Int? = null,
    val fuelPercent: Int? = null,
    val batteryVolts: Float? = null,
    val intakeC: Int? = null,
    val engineLoadPercent: Int? = null,
)

/** One mode 01 request, and how to read its answer. */
enum class ObdPid(val command: String, val bytes: Int) {
    RPM("010C", 2),
    SPEED("010D", 1),
    COOLANT("0105", 1),
    FUEL("012F", 1),
    INTAKE("010F", 1),
    LOAD("0104", 1),
}

/**
 * ELM327 conversation: the setup commands and parsing of its text answers. Pure, so it can be unit tested
 * without an adapter.
 */
object ObdProtocol {
    /** Reset, then echo off, linefeeds off, spaces off, headers off, automatic protocol. */
    val SETUP = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")
    const val VOLTAGE = "ATRV"

    /** The data bytes answering [pid], or null for NO DATA, errors, or an answer to something else. */
    fun dataBytes(response: String, pid: ObdPid): IntArray? {
        val expected = "4" + pid.command.substring(1) // "010C" is answered by "410C"
        // The answer can carry "SEARCHING...", line breaks, the prompt and (if the adapter ignored ATS0) spaces.
        for (line in response.uppercase().split('\r', '\n')) {
            val hex = line.filter { it.isLetterOrDigit() }
            val at = hex.indexOf(expected)
            if (at < 0) continue
            val data = hex.substring(at + expected.length)
            if (data.length < pid.bytes * 2) continue
            return IntArray(pid.bytes) { i -> data.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null }
        }
        return null
    }

    /** Applies one PID's answer to [reading]; unchanged when the answer can't be read. */
    fun apply(reading: ObdReading, pid: ObdPid, response: String): ObdReading {
        val b = dataBytes(response, pid) ?: return reading
        return when (pid) {
            ObdPid.RPM -> reading.copy(rpm = (b[0] * 256 + b[1]) / 4)
            ObdPid.SPEED -> reading.copy(speedKmh = b[0])
            ObdPid.COOLANT -> reading.copy(coolantC = b[0] - 40)
            ObdPid.FUEL -> reading.copy(fuelPercent = Math.round(b[0] * 100f / 255f))
            ObdPid.INTAKE -> reading.copy(intakeC = b[0] - 40)
            ObdPid.LOAD -> reading.copy(engineLoadPercent = Math.round(b[0] * 100f / 255f))
        }
    }

    /** "12.6V" → 12.6, or null. */
    fun parseVoltage(response: String): Float? =
        Regex("""(\d{1,2}(?:\.\d+)?)\s*V""", RegexOption.IGNORE_CASE).find(response)?.groupValues?.get(1)?.toFloatOrNull()

    /** True when the answer means the car isn't talking (ignition off, wrong protocol, adapter not on the port). */
    fun isNoConnection(response: String): Boolean {
        val r = response.uppercase()
        return "UNABLE TO CONNECT" in r || "CAN ERROR" in r || "BUS INIT" in r && "ERROR" in r
    }
}
