/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.dashclock.phone

import android.annotation.TargetApi
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.text.TextUtils
import com.google.android.apps.dashclock.LogUtils
import com.google.android.apps.dashclock.LogUtils.LOGD
import com.google.android.apps.dashclock.LogUtils.LOGE
import com.google.android.apps.dashclock.LogUtils.LOGW
import com.google.android.apps.dashclock.api.DashClockExtension
import com.google.android.apps.dashclock.api.ExtensionData
import net.nurik.roman.dashclock.R

/**
 * Unread SMS and MMS's extension.
 */
class SmsExtension : DashClockExtension() {
    override fun onInitialize(isReconnect: Boolean) {
        super.onInitialize(isReconnect)
        if (!isReconnect) {
            addWatchContentUris(
                arrayOf(
                    TelephonyProviderConstants.MmsSms.CONTENT_URI.toString()
                )
            )
        }
    }

    override fun onUpdateData(reason: Int) {
        var lastUnreadThreadId: Long = 0
        val unreadThreadIds = HashSet<Long>()
        val unreadThreadParticipantNames = HashSet<String>()
        var showingAllConversationParticipants = false

        var cursor = tryOpenSimpleThreadsCursor()
        if (cursor != null) {
            while (cursor.moveToNext()) {
                if (cursor.getInt(SimpleThreadsQuery.READ) == 0) {
                    val threadId = cursor.getLong(SimpleThreadsQuery._ID)
                    unreadThreadIds.add(threadId)
                    lastUnreadThreadId = threadId

                    // Some devices will fail on tryOpenMmsSmsCursor below, so
                    // store a list of participants on unread threads as a fallback.
                    val recipientIdsStr = cursor.getString(SimpleThreadsQuery.RECIPIENT_IDS)
                    if (!TextUtils.isEmpty(recipientIdsStr)) {
                        val recipientIds = TextUtils.split(recipientIdsStr, " ")
                        for (recipientId in recipientIds) {
                            val canonAddrCursor = tryOpenCanonicalAddressCursorById(
                                recipientId.toLong()
                            ) ?: continue
                            if (canonAddrCursor.moveToFirst()) {
                                val address = canonAddrCursor.getString(
                                    CanonicalAddressQuery.ADDRESS
                                )
                                val displayName = getDisplayNameForContact(0, address)
                                if (!TextUtils.isEmpty(displayName) && displayName != null) {
                                    unreadThreadParticipantNames.add(displayName)
                                }
                            }
                            canonAddrCursor.close()
                        }
                    }
                }
            }
            cursor.close()

            LOGD(TAG, "Unread thread IDs: [" + TextUtils.join(", ", unreadThreadIds) + "]")
        }

        var unreadConversations = 0
        val names = StringBuilder()
        cursor = tryOpenMmsSmsCursor()
        if (cursor != null) {
            // Most devices will hit this code path.
            while (cursor.moveToNext()) {
                // Get display name. SMS's are easy; MMS's not so much.
                val id = cursor.getLong(MmsSmsQuery._ID)
                var contactId = cursor.getLong(MmsSmsQuery.PERSON)
                var address = cursor.getString(MmsSmsQuery.ADDRESS)
                val threadId = cursor.getLong(MmsSmsQuery.THREAD_ID)
                if (!unreadThreadIds.contains(threadId)) {
                    // We have the list of all thread IDs (same as what the messaging app uses), and
                    // this supposedly unread message's thread isn't in the list. This message is
                    // likely an orphaned message whose thread was deleted. Not skipping it is
                    // likely the cause of http://code.google.com/p/dashclock/issues/detail?id=8
                    LOGD(
                        TAG,
                        "Skipping probably orphaned message $id with thread ID $threadId"
                    )
                    continue
                }

                ++unreadConversations
                lastUnreadThreadId = threadId

                if (contactId == 0L && TextUtils.isEmpty(address) && id != 0L) {
                    // Try MMS addr query
                    val addrCursor = tryOpenMmsAddrCursor(id)
                    if (addrCursor != null) {
                        if (addrCursor.moveToFirst()) {
                            contactId = addrCursor.getLong(MmsAddrQuery.CONTACT_ID)
                            address = addrCursor.getString(MmsAddrQuery.ADDRESS)
                        }
                        addrCursor.close()
                    }
                }

                val displayName = getDisplayNameForContact(contactId, address)

                if (names.isNotEmpty()) {
                    names.append(", ")
                }
                names.append(displayName)
            }
            cursor.close()
        } else {
            // In case the cursor is null (some Samsung devices like the Galaxy S4), use the
            // fall back on the list of participants in unread threads.
            unreadConversations = unreadThreadIds.size
            names.append(TextUtils.join(", ", unreadThreadParticipantNames))
            showingAllConversationParticipants = true
        }

        val pm = packageManager
        var clickIntent: Intent? = null
        if (unreadConversations == 1 && lastUnreadThreadId > 0) {
            clickIntent = Intent(
                Intent.ACTION_VIEW,
                TelephonyProviderConstants.MmsSms.CONTENT_CONVERSATIONS_URI.buildUpon()
                    .appendPath(lastUnreadThreadId.toString()).build()
            )
        }

        // If the default SMS app doesn't support ACTION_VIEW on the conversation URI,
        // or if there are multiple unread conversations, try opening the app landing screen
        // by implicit intent.
        if (clickIntent == null || pm.resolveActivity(clickIntent, 0) == null) {
            clickIntent = Intent.makeMainSelectorActivity(
                Intent.ACTION_MAIN,
                Intent.CATEGORY_APP_MESSAGING
            )

            // If the default SMS app doesn't support CATEGORY_APP_MESSAGING, try KitKat's
            // new API to get the default package (if the API is available).
            if (pm.resolveActivity(clickIntent, 0) == null) {
                clickIntent = tryGetKitKatDefaultSmsActivity()
            }
        }

        publishUpdate(
            ExtensionData()
                .visible(unreadConversations > 0)
                .icon(R.drawable.ic_extension_sms)
                .status(unreadConversations.toString())
                .expandedTitle(
                    resources.getQuantityString(
                        R.plurals.sms_title_template,
                        unreadConversations,
                        unreadConversations
                    )
                )
                .expandedBody(
                    getString(
                        if (showingAllConversationParticipants)
                            R.string.sms_body_all_participants_template
                        else
                            R.string.sms_body_template,
                        names.toString()
                    )
                )
                .clickIntent(clickIntent)
        )
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private fun tryGetKitKatDefaultSmsActivity(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val smsPackage = Telephony.Sms.getDefaultSmsPackage(this)
            if (TextUtils.isEmpty(smsPackage)) {
                return null
            }

            return Intent()
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(smsPackage)
        }
        return null
    }

    /**
     * Returns the display name for the contact with the given ID and/or the given address
     * (phone number). One or both parameters should be provided.
     */
    private fun getDisplayNameForContact(contactId: Long, address: String?): String? {
        var resolvedContactId = contactId
        var displayName = address

        if (resolvedContactId > 0) {
            val contactCursor = tryOpenContactsCursorById(resolvedContactId)
            if (contactCursor != null) {
                if (contactCursor.moveToFirst()) {
                    displayName = contactCursor.getString(RawContactsQuery.DISPLAY_NAME)
                } else {
                    resolvedContactId = 0
                }
                contactCursor.close()
            }
        }

        if (resolvedContactId <= 0) {
            val contactCursor = tryOpenContactsCursorByAddress(address)
            if (contactCursor != null) {
                if (contactCursor.moveToFirst()) {
                    displayName = contactCursor.getString(ContactsQuery.DISPLAY_NAME)
                }
                contactCursor.close()
            }
        }

        return displayName
    }

    private fun tryOpenMmsSmsCursor(): Cursor? {
        return try {
            contentResolver.query(
                TelephonyProviderConstants.MmsSms.CONTENT_CONVERSATIONS_URI,
                MmsSmsQuery.PROJECTION,
                TelephonyProviderConstants.Mms.READ + "=0 AND "
                        + TelephonyProviderConstants.Mms.THREAD_ID + "!=0 AND ("
                        + TelephonyProviderConstants.Mms.MESSAGE_BOX + "="
                        + TelephonyProviderConstants.Mms.MESSAGE_BOX_INBOX + " OR "
                        + TelephonyProviderConstants.Sms.TYPE + "="
                        + TelephonyProviderConstants.Sms.MESSAGE_TYPE_INBOX + ")",
                null,
                null
            )
        } catch (e: Exception) {
            // Catch all exceptions because the SMS provider is crashy
            // From developer console: "SQLiteException: table spam_filter already exists"
            LOGE(TAG, "Error accessing conversations cursor in SMS/MMS provider", e)
            null
        }
    }

    private fun tryOpenSimpleThreadsCursor(): Cursor? {
        return try {
            contentResolver.query(
                TelephonyProviderConstants.Threads.CONTENT_URI
                    .buildUpon()
                    .appendQueryParameter("simple", "true")
                    .build(),
                SimpleThreadsQuery.PROJECTION,
                null,
                null,
                null
            )
        } catch (e: Exception) {
            LOGW(TAG, "Error accessing simple SMS threads cursor", e)
            null
        }
    }

    private fun tryOpenCanonicalAddressCursorById(id: Long): Cursor? {
        return try {
            contentResolver.query(
                TelephonyProviderConstants.CanonicalAddresses.CONTENT_URI.buildUpon()
                    .build(),
                CanonicalAddressQuery.PROJECTION,
                "${TelephonyProviderConstants.CanonicalAddresses._ID}=?",
                arrayOf(id.toString()),
                null
            )
        } catch (e: Exception) {
            LOGE(TAG, "Error accessing canonical addresses cursor", e)
            null
        }
    }

    private fun tryOpenMmsAddrCursor(mmsMsgId: Long): Cursor? {
        return try {
            contentResolver.query(
                TelephonyProviderConstants.Mms.CONTENT_URI.buildUpon()
                    .appendPath(mmsMsgId.toString())
                    .appendPath("addr")
                    .build(),
                MmsAddrQuery.PROJECTION,
                "${TelephonyProviderConstants.Mms.Addr.MSG_ID}=?",
                arrayOf(mmsMsgId.toString()),
                null
            )
        } catch (e: Exception) {
            // Catch all exceptions because the SMS provider is crashy
            // From developer console: "SQLiteException: table spam_filter already exists"
            LOGE(TAG, "Error accessing MMS addresses cursor", e)
            null
        }
    }

    private fun tryOpenContactsCursorById(contactId: Long): Cursor? {
        return try {
            contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                    .appendPath(contactId.toString())
                    .build(),
                RawContactsQuery.PROJECTION,
                null,
                null,
                null
            )
        } catch (e: Exception) {
            LOGE(TAG, "Error accessing contacts provider", e)
            null
        }
    }

    private fun tryOpenContactsCursorByAddress(phoneNumber: String?): Cursor? {
        return try {
            contentResolver.query(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                    .appendPath(Uri.encode(phoneNumber)).build(),
                ContactsQuery.PROJECTION,
                null,
                null,
                null
            )
        } catch (e: Exception) {
            // Can be called by the content provider (from Google Play crash/ANR console)
            // java.lang.IllegalArgumentException: URI: content://com.android.contacts/phone_lookup/
            LOGW(TAG, "Error looking up contact name", e)
            null
        }
    }

    private object SimpleThreadsQuery {
        val PROJECTION = arrayOf(
            TelephonyProviderConstants.Threads._ID,
            TelephonyProviderConstants.Threads.READ,
            TelephonyProviderConstants.Threads.RECIPIENT_IDS
        )

        const val _ID = 0
        const val READ = 1
        const val RECIPIENT_IDS = 2
    }

    private object CanonicalAddressQuery {
        val PROJECTION = arrayOf(
            TelephonyProviderConstants.CanonicalAddresses._ID,
            TelephonyProviderConstants.CanonicalAddresses.ADDRESS
        )

        const val _ID = 0
        const val ADDRESS = 1
    }

    private object MmsSmsQuery {
        val PROJECTION = arrayOf(
            TelephonyProviderConstants.Sms._ID,
            TelephonyProviderConstants.Sms.ADDRESS,
            TelephonyProviderConstants.Sms.PERSON,
            TelephonyProviderConstants.Sms.THREAD_ID
        )

        const val _ID = 0
        const val ADDRESS = 1
        const val PERSON = 2
        const val THREAD_ID = 3
    }

    private object MmsAddrQuery {
        val PROJECTION = arrayOf(
            TelephonyProviderConstants.Mms.Addr.ADDRESS,
            TelephonyProviderConstants.Mms.Addr.CONTACT_ID
        )

        const val ADDRESS = 0
        const val CONTACT_ID = 1
    }

    private object RawContactsQuery {
        val PROJECTION = arrayOf(
            ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY
        )

        const val DISPLAY_NAME = 0
    }

    private object ContactsQuery {
        val PROJECTION = arrayOf(
            ContactsContract.Contacts.DISPLAY_NAME
        )

        const val DISPLAY_NAME = 0
    }

    companion object {
        private val TAG = LogUtils.makeLogTag(SmsExtension::class.java)
    }
}
