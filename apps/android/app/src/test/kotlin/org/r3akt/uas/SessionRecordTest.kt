package org.r3akt.uas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SessionRecordTest {
    @Test
    fun simulatorFixture() {
        val r = SessionRecord.parse(File("../../../fixtures/flights/sim-session-v1.jsonl").readLines())!!
        assertEquals(true, r.simulated)
        assertEquals("simulator", r.variant)
        assertEquals(1, r.flights)
        // The simulator reports MSL: no take-off-relative maximum is made up from it.
        assertNull(r.maxTakeoffAltM)
        assertEquals(33_120L, r.endMs!! - r.startMs)
        assertEquals((1L..12L).toList(), r.events.map { it.seq })
        assertTrue(r.events.any { it.text == "FIX 45.00180, 7.00000 · 400 m MSL" })
    }

    @Test
    fun flightsActionsAndAltitude() {
        val r = SessionRecord.parse(listOf(
            """{"type":"session","seq":0,"time_ms":1000,"format":"r3akt-uas-session/1","session_id":"x","variant":"DJI SDK 4","simulated":false}""",
            """{"type":"airborne","seq":1,"time_ms":2000,"airborne":true}""",
            """{"type":"fix","seq":2,"time_ms":2000,"lat":45,"lon":7,"alt_m":31.5,"alt_ref":"TAKEOFF_RELATIVE"}""",
            """{"type":"airborne","seq":3,"time_ms":3000,"airborne":null}""",
            """{"type":"airborne","seq":4,"time_ms":4000,"airborne":true}""",
            """{"type":"fix","seq":5,"time_ms":4000,"lat":45,"lon":7,"alt_m":90,"alt_ref":"MSL"}""",
            """{"type":"action","seq":6,"time_ms":5000,"action":"LAND","error":null}""",
            """{"type":"airborne","seq":7,"time_ms":6000,"airborne":false}""",
            """{"type":"action","seq":8,"time_ms":7000,"action":"TAKEOFF","error":"Motors are locked"}""",
            """{"type":"airborne","seq":9,"time_ms":8000,"airborne":true}""",
            "{\"type\":\"fix\",\"seq\":10,", // a line cut off by a crash is skipped
        ))!!
        assertEquals(false, r.simulated)
        assertEquals(2, r.flights) // the unknown in between does not start a new flight
        assertEquals(31.5, r.maxTakeoffAltM!!, 0.0)
        assertEquals(2, r.actions)
        assertEquals(1, r.rejections)
        assertNull(r.endMs)
        assertEquals("TAKEOFF REJECTED · DJI: Motors are locked", r.events.single { it.seq == 8L }.text)
    }

    @Test
    fun otherFormatIsNotRead() {
        assertNull(SessionRecord.parse(listOf("""{"type":"session","format":"other"}""")))
    }
}
