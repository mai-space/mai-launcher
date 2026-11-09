package space.mai.mai_launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandsTest {
    private fun first(q: String) = CommandParser.parse(q).first()
    private fun answer(q: String) = (CommandParser.parse(q).firstOrNull { it is Command.Answer } as? Command.Answer)?.value

    @Test fun calculator() {
        assertEquals("84", answer("= 12*7"))
        assertEquals("84", answer("12 x 7"))
        assertEquals("14", answer("2+3*4"))
        assertEquals("20", answer("(2+3)*4"))
        assertEquals("1024", answer("2^10"))
        assertEquals("2.5", answer("5/2"))
        assertEquals("3.75", answer("2,5 * 1,5"))
        assertEquals("16", answer("20% * 80"))
        assertEquals("0.3333333333", answer("1/3"))
        assertEquals("-3", answer("=-3"))
    }

    @Test fun calculatorNeedsOperatorWithoutEquals() {
        assertNull(answer("2024"))
        assertNull(answer("spotify"))
        assertNull(answer("=1/0"))
        assertNull(answer("= 2 +"))
    }

    @Test fun percentOf() {
        assertEquals("12.8", answer("20 % of 64"))
        assertEquals("12.8", answer("20% von 64"))
    }

    @Test fun units() {
        assertEquals("3.10686 mi", answer("5 km in mi"))
        assertEquals("68 °F", answer("20 c in f"))
        assertEquals("0 °C", answer("32 f to c"))
        assertEquals("2.20462 lb", answer("1 kg in lb"))
        assertEquals("62.1371 mph", answer("100 km/h in mph"))
        assertEquals("250 ml", answer("0,25 l in ml"))
        assertNull(answer("5 km in kg"))
    }

    @Test fun timerAlarmStopwatch() {
        assertEquals(Command.Timer(600), first("timer 10"))
        assertEquals(Command.Timer(90), first("timer 90s"))
        assertEquals(Command.Timer(5400), first("timer 1h 30m"))
        assertEquals(Command.Timer(5400), first("timer 1,5 h"))
        assertEquals(Command.Alarm(6, 30), first("alarm 6:30"))
        assertEquals(Command.Alarm(18, 0), first("wecker 6 pm"))
        assertEquals(Command.Alarm(7, 15), first("Wecker 7.15 Uhr"))
        assertEquals(Command.Stopwatch, first("stoppuhr"))
        assertTrue(CommandParser.parse("alarm 25:00").none { it is Command.Alarm })
        assertTrue(CommandParser.parse("timer abc").none { it is Command.Timer })
    }

    @Test fun settingsByKeyword() {
        assertEquals(Command.Setting(SettingPage.Wifi), first("wlan"))
        assertEquals(Command.Setting(SettingPage.Bluetooth), first("blue"))
        assertEquals(Command.Setting(SettingPage.Battery), first("Akku"))
        assertTrue(CommandParser.parse("ma").none { it is Command.Setting })
    }

    @Test fun phoneNumberDials() {
        assertEquals(Command.Dial("+491511234567"), first("+49 151 1234567"))
        assertEquals(Command.Dial("110"), first("110"))
        assertTrue(CommandParser.parse("12").none { it is Command.Dial })
    }

    @Test fun webSearchAlwaysLastAndNotForSingleChars() {
        val r = CommandParser.parse("wetter hamburg")
        assertEquals(Command.WebSearch("wetter hamburg"), r.last())
        assertTrue(CommandParser.parse("w").isEmpty())
        assertTrue(CommandParser.parse("= 3*3").none { it is Command.WebSearch })
    }

    @Test fun explicitCommandsBeatApps() {
        assertTrue(CommandParser.isExplicit(first("timer 5")))
        assertTrue(!CommandParser.isExplicit(Command.WebSearch("x")))
        assertTrue(!CommandParser.isExplicit(Command.Setting(SettingPage.Wifi)))
    }

    @Test fun durationAndClockHelpers() {
        assertEquals(30, CommandParser.duration("30 sek"))
        assertEquals(7200, CommandParser.duration("2 std"))
        assertNull(CommandParser.duration(""))
        assertEquals(0 to 30, CommandParser.clock("12:30 am"))
        assertNull(CommandParser.clock("24:00"))
    }
}
