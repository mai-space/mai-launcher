package space.mai.mai_launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppKeyTest {
    @Test fun roundTrip() {
        val key = AppKey("com.example.mail", "com.example.mail.MainActivity", 10)
        assertEquals(key, AppKey.decode(key.encode()))
    }

    @Test fun formatIsStable() {
        // Gespeichertes Format darf sich nie ändern (Dock, Startverlauf).
        assertEquals("a.b/a.b.Main#0", AppKey("a.b", "a.b.Main").encode())
    }

    @Test fun tolerantDecode() {
        assertNull(AppKey.decode(null))
        assertNull(AppKey.decode(""))
        assertNull(AppKey.decode("garbage"))
        assertNull(AppKey.decode("a.b/"))
        assertNull(AppKey.decode("a.b/c#x"))
        assertEquals(AppKey("a.b", "c", 0), AppKey.decode("a.b/c"))
    }
}
