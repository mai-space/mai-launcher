package space.mai.mai_launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesTest {
    @Test fun roundTripKeepsTabsNewlinesBackslashes() {
        val notes = listOf(
            Note(1, "Milch kaufen", 1_000),
            Note(2, "Pfad C:\\temp\tund\nzweite Zeile", 2_000),
        )
        val text = NotesCodec.encode(notes)
        assertTrue(text.startsWith(NotesCodec.HEADER))
        assertEquals(notes, NotesCodec.decode(text))
    }

    @Test fun readsTolerantly() {
        val text = "notes v2\n5\t10\tok\textra-feld\nkaputt\nx\t1\tfalsch\n6\t11\t   \n"
        assertEquals(listOf(Note(5, "ok", 10)), NotesCodec.decode(text))
        assertEquals(emptyList<Note>(), NotesCodec.decode("etwas anderes"))
    }

    @Test fun limits() {
        assertNull(NotesCodec.clean("   "))
        assertEquals(NotesCodec.MAX_LENGTH, NotesCodec.clean("x".repeat(2000))!!.length)
        val many = (1L..300L).map { Note(it, "n$it", it) }
        val back = NotesCodec.decode(NotesCodec.encode(many))
        assertEquals(NotesCodec.MAX_NOTES, back.size)
        assertEquals(300L, back.last().id)
    }

    @Test fun noteCommand() {
        assertEquals(listOf(Command.Note("Milch kaufen")), CommandParser.parse("note Milch kaufen"))
        assertEquals(listOf(Command.Note("Sam anrufen")), CommandParser.parse("Notiz Sam anrufen"))
        assertEquals(listOf(Command.Note("Pass verlängern")), CommandParser.parse("merke Pass verlängern"))
        // Ohne Text keine Notiz; „notes“ ist eine normale Suche.
        assertTrue(CommandParser.parse("note ").none { it is Command.Note })
        assertTrue(CommandParser.parse("notes").none { it is Command.Note })
        assertTrue(CommandParser.isExplicit(Command.Note("x")))
    }
}
