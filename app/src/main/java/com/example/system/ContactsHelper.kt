package com.example.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

private const val TAG = "ContactsHelper"

data class ContactMatch(
    val displayName: String,
    val phoneNumber: String? = null,
    val email: String? = null
)

class ContactsHelper(private val context: Context) {

    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun findPhoneByName(nameQuery: String): ContactMatch? {
        if (!hasContactsPermission() || nameQuery.isBlank()) return null
        return try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("%${nameQuery.trim()}%")
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                args,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx =
                        cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx =
                        cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else nameQuery
                    val number = if (numIdx >= 0) cursor.getString(numIdx) else null
                    ContactMatch(displayName = name, phoneNumber = normalizePhone(number))
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed querying phone contacts", e)
            null
        }
    }

    fun findEmailByName(nameQuery: String): ContactMatch? {
        if (!hasContactsPermission() || nameQuery.isBlank()) return null
        return try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Email.ADDRESS
            )
            val selection = "${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("%${nameQuery.trim()}%")
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                projection,
                selection,
                args,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx =
                        cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME)
                    val addrIdx =
                        cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else nameQuery
                    val address = if (addrIdx >= 0) cursor.getString(addrIdx) else null
                    ContactMatch(displayName = name, email = address)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed querying email contacts", e)
            null
        }
    }

    fun extractDirectPhoneDigits(raw: String): String? {
        val cleaned = raw.replace(Regex("[^+0-9]"), "")
        return if (cleaned.count { it.isDigit() } >= 6) cleaned else null
    }

    private fun normalizePhone(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.replace(Regex("[\\s\\-()]"), "")
    }
}
