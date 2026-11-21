package org.r3akt.uas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsTest {
    @Test
    fun streamUrlValidation() {
        assertTrue(Prefs.validStreamUrl("rtmp://10.0.0.5:1935/uas/aircraft-1"))
        assertTrue(Prefs.validStreamUrl("rtmps://video.example/uas/a?user=u&pass=p"))
        assertFalse(Prefs.validStreamUrl("http://video.example/uas/a"))
        assertFalse(Prefs.validStreamUrl("rtmp://"))
        assertFalse(Prefs.validStreamUrl("rtmp://host/with space"))
    }

    @Test
    fun hubValidation() {
        assertTrue(Prefs.validHubAddress("hub.example:4242"))
        assertFalse(Prefs.validHubAddress("hub.example"))
        assertFalse(Prefs.validHubAddress("hub example:4242"))
        assertTrue(Prefs.validHex32("0123456789abcdef0123456789ABCDEF"))
        assertFalse(Prefs.validHex32("0123456789abcdef"))
        assertFalse(Prefs.validHex32("0123456789abcdef0123456789abcdeg"))
    }

    @Test
    fun redactRemovesCredentials() {
        assertEquals("rtmp://host:1935/uas/a (credentials hidden)", Prefs.redact("rtmp://host:1935/uas/a?user=pilot&pass=secret"))
        assertEquals("rtmp://host/uas/a (credentials hidden)", Prefs.redact("rtmp://pilot:secret@host/uas/a"))
        assertEquals("rtmp://host/uas/a", Prefs.redact("rtmp://host/uas/a"))
    }
}

class StreamQualityTest {
    @Test
    fun lowIsTheCheapestReEncodedPreset() {
        val encoded = StreamQuality.entries.filter { it.kbps != null }
        assertEquals(StreamQuality.LOW, encoded.minBy { it.kbps!! })
        assertEquals(135, StreamQuality.LOW.mbPerHour)
        assertEquals(null, StreamQuality.ORIGINAL.mbPerHour)
    }
}
