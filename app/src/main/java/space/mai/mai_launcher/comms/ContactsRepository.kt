package space.mai.mai_launcher.comms

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import space.mai.mai_launcher.domain.CommsEntry
import space.mai.mai_launcher.domain.CommsLogCodec
import space.mai.mai_launcher.domain.Contact

/**
 * Kontakte für Comms (nur mit READ_CONTACTS, nur lesen). Anrufen läuft immer
 * über die Telefon-App (`ACTION_DIAL`) – keine Telefon-Berechtigung.
 * Eigener Verlauf unter Schlüssel `comms_log` (nur Kontakt-Schlüssel, Anzahl, Zeit).
 */
class ContactsRepository(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _log = MutableStateFlow(CommsLogCodec.decode(prefs.getString(KEY_LOG, null)))
    val log: StateFlow<Map<String, CommsEntry>> = _log.asStateFlow()

    private var observing = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    fun hasAccess(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (!hasAccess()) return
        if (!observing) {
            runCatching {
                context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
                observing = true
            }
        }
        refresh()
    }

    fun stop() {
        if (observing) runCatching { context.contentResolver.unregisterContentObserver(observer) }
        observing = false
    }

    fun refresh() {
        if (!hasAccess()) return
        scope.launch(Dispatchers.IO) { _contacts.value = runCatching { query() }.getOrDefault(emptyList()) }
    }

    private fun query(): List<Contact> {
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
        )
        data class Row(val key: String, val name: String, val starred: Boolean, val number: String, val primary: Boolean)
        val rows = mutableListOf<Row>()
        context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val key = c.getString(0) ?: continue
                val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                val number = c.getString(3)?.takeIf { it.isNotBlank() } ?: continue
                rows += Row(key, name, c.getInt(2) == 1, number.trim(), c.getInt(4) == 1)
            }
        }
        return rows.groupBy { it.key }.map { (key, list) ->
            val numbers = list.sortedByDescending { it.primary }.map { it.number }
                .distinctBy { n -> n.filter { it.isDigit() || it == '+' } }
            Contact(key, list.first().name, list.any { it.starred }, numbers)
        }.sortedBy { it.name.lowercase() }
    }

    // --- Aktionen ---

    private fun start(intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)

    private fun remember(contact: Contact?) {
        contact ?: return
        val next = CommsLogCodec.record(_log.value, contact.lookupKey, System.currentTimeMillis())
        prefs.edit { putString(KEY_LOG, CommsLogCodec.encode(next)) }
        _log.value = next
    }

    /** Nummer in der Telefon-App vorwählen; der Anruf startet dort. */
    fun dial(number: String, contact: Contact? = null) {
        if (start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))) remember(contact)
    }

    fun text(number: String, contact: Contact? = null) {
        if (start(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))) remember(contact)
    }

    fun openContact(contact: Contact) {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, contact.lookupKey)
        start(Intent(Intent.ACTION_VIEW, uri))
    }

    /** Ruf durch (Mai-App für den täglichen Anruf), falls installiert. */
    fun rufDurchInstalled(): Boolean = context.packageManager.getLaunchIntentForPackage(RUF_DURCH) != null

    fun openRufDurch() {
        context.packageManager.getLaunchIntentForPackage(RUF_DURCH)?.let(::start)
    }

    companion object {
        private const val KEY_LOG = "comms_log"
        const val RUF_DURCH = "space.mai.mai_stay_in_touch"
    }
}
