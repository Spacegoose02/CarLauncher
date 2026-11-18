package com.spacegoose.carlauncher.phone

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QuickContact(val id: Long, val name: String, val number: String, val favourite: Boolean)

/**
 * Contacts for the phone module. On a head unit these are usually the phone's contacts, copied over
 * Bluetooth by the head unit's own phone app. Favourites and frequent contacts come first, then everyone
 * else alphabetically.
 */
class ContactsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _contacts = MutableStateFlow<List<QuickContact>>(emptyList())
    val contacts: StateFlow<List<QuickContact>> = _contacts.asStateFlow()
    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private var observing = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    fun start() {
        refresh()
        if (!observing && _hasPermission.value) {
            appContext.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            observing = true
        }
    }

    fun stop() {
        if (observing) appContext.contentResolver.unregisterContentObserver(observer)
        observing = false
    }

    fun refresh() {
        val granted = granted(Manifest.permission.READ_CONTACTS)
        _hasPermission.value = granted
        if (!granted) {
            _contacts.value = emptyList()
            return
        }
        scope.launch {
            _contacts.value = withContext(Dispatchers.IO) { runCatching { load() }.getOrDefault(emptyList()) }
        }
    }

    private fun load(): List<QuickContact> {
        val resolver = appContext.contentResolver
        // Starred first, then frequently contacted (the provider orders them that way).
        val frequent = LinkedHashMap<Long, Boolean>()
        resolver.query(
            ContactsContract.Contacts.CONTENT_STREQUENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.STARRED, ContactsContract.Contacts.HAS_PHONE_NUMBER),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(2) != 0) frequent.putIfAbsent(c.getLong(0), c.getInt(1) != 0)
            }
        }
        // One number per contact: the default (super-primary) one if set, otherwise the first.
        val numbers = LinkedHashMap<Long, QuickContact>()
        resolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.IS_SUPER_PRIMARY, Phone.STARRED),
            null, null,
            "${Phone.DISPLAY_NAME} COLLATE NOCASE ASC, ${Phone.IS_SUPER_PRIMARY} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val number = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                if (id in numbers) continue
                val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: number
                numbers[id] = QuickContact(id, name, number, c.getInt(4) != 0)
            }
        }
        val first = frequent.keys.mapNotNull { numbers[it]?.copy(favourite = true) }
        val rest = numbers.values.filter { it.id !in frequent }
        return (first + rest).take(MAX_CONTACTS)
    }

    /** Calls [number] directly when allowed, otherwise opens it in the dialer to confirm. */
    fun call(context: Context, number: String) {
        val uri = Uri.fromParts("tel", number, null)
        val direct = granted(Manifest.permission.CALL_PHONE)
        if (direct && start(context, Intent(Intent.ACTION_CALL, uri))) return
        if (start(context, Intent(Intent.ACTION_DIAL, uri))) return
        openPhoneApp(context)
    }

    /** Opens the keypad: the dialer, or else the head unit's Bluetooth phone app. */
    fun openPhoneApp(context: Context): Boolean {
        if (start(context, Intent(Intent.ACTION_DIAL))) return true
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val apps = pm.queryIntentActivities(launcher, 0).map { it to it.loadLabel(pm).toString() }
        // The best-matching word wins: a "Phone" app over a "Bluetooth" one.
        val match = PHONE_APP_WORDS.firstNotNullOfOrNull { word ->
            apps.firstOrNull { (_, label) -> label.contains(word, ignoreCase = true) }?.first
        } ?: return false
        return start(context, Intent(launcher).setClassName(match.activityInfo.packageName, match.activityInfo.name))
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val MAX_CONTACTS = 300
        val PHONE_APP_WORDS = listOf("Phone", "Dialer", "Telephone", "Bluetooth")
    }
}
