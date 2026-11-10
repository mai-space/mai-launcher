package space.mai.mai_launcher.data

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.mai.mai_launcher.domain.Note
import space.mai.mai_launcher.domain.NotesCodec
import java.io.File

/**
 * Notizen der Befehlszeile. Verschlüsselt (SecureFile, eigener Schlüssel) in
 * noBackupFilesDir; nie exportiert, nie im Netz. Dateiname und Alias nie ändern.
 */
class NotesStore(context: Context, private val scope: CoroutineScope) {
    private val store = SecureFile(File(context.noBackupFilesDir, FILE_NAME), KEY_ALIAS)
    private val mutex = Mutex()
    private val writeMutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    fun load() {
        scope.launch(Dispatchers.IO) {
            try {
                val loaded = store.read()?.toString(Charsets.UTF_8)?.let(NotesCodec::decode).orEmpty()
                mutex.withLock { _notes.value = (loaded + _notes.value).distinctBy { it.id } }
            } finally {
                ready.complete(Unit)
            }
        }
    }

    fun add(text: String, now: Long = System.currentTimeMillis()) {
        val clean = NotesCodec.clean(text) ?: return
        update { list -> list + Note(maxOf(now, (list.maxOfOrNull { it.id } ?: 0) + 1), clean, now) }
    }

    fun remove(id: Long) = update { list -> list.filterNot { it.id == id } }

    /** Rückgängig: Notiz an alter Stelle wieder einfügen. */
    fun restore(note: Note) = update { list -> if (list.any { it.id == note.id }) list else (list + note).sortedBy { it.id } }

    private fun update(change: (List<Note>) -> List<Note>) {
        scope.launch {
            mutex.withLock { _notes.value = change(_notes.value).takeLast(NotesCodec.MAX_NOTES) }
            // Immer den neuesten Stand schreiben, nie zwei Schreibvorgänge gleichzeitig.
            launch(Dispatchers.IO) {
                // Erst nach dem Laden schreiben, sonst gingen alte Notizen verloren.
                ready.await()
                writeMutex.withLock {
                    val text = mutex.withLock { NotesCodec.encode(_notes.value) }
                    runCatching { store.write(text.toByteArray(Charsets.UTF_8)) }
                }
            }
        }
    }

    private companion object {
        const val FILE_NAME = "notes.bin"
        const val KEY_ALIAS = "mai_launcher_notes_key"
    }
}
