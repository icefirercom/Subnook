package com.subnook.app

import com.subnook.app.data.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test fun normalize() {
        assertEquals("1.2.0", UpdateChecker.normalize("v1.2.0"))
        assertEquals("1.1.0", UpdateChecker.normalize("1.1.0-debug"))
    }

    @Test fun compare() {
        assertTrue(UpdateChecker.compare("1.2.0", "1.1.0") > 0)
        assertTrue(UpdateChecker.compare("1.10.0", "1.9.3") > 0)
        assertEquals(0, UpdateChecker.compare("1.1", "1.1.0"))
        assertTrue(UpdateChecker.compare("1.1.0", "1.1.1") < 0)
    }
}
