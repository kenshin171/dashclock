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

package com.google.android.apps.dashclock.gmail

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.preference.PreferenceManager
import android.text.TextUtils
import android.util.Pair
import com.google.android.apps.dashclock.LogUtils
import com.google.android.apps.dashclock.LogUtils.LOGD
import com.google.android.apps.dashclock.LogUtils.LOGE
import com.google.android.apps.dashclock.LogUtils.LOGW
import com.google.android.apps.dashclock.api.DashClockExtension
import com.google.android.apps.dashclock.api.ExtensionData
import net.nurik.roman.dashclock.R

/**
 * Gmail unread count extension.
 */
class GmailExtension : DashClockExtension() {
    private val selectedAccounts: Set<String>
        get() {
            val sp = PreferenceManager.getDefaultSharedPreferences(this)
            val accounts = getAllAccountNames(this)
            val allAccountsSet = HashSet<String>(accounts.toList())
            return sp.getStringSet(PREF_ACCOUNTS, allAccountsSet) ?: allAccountsSet
        }

    override fun onInitialize(isReconnect: Boolean) {
        super.onInitialize(isReconnect)
        if (!isReconnect) {
            val selectedAccounts = selectedAccounts
            val uris = Array(selectedAccounts.size) { "" }

            var i = 0
            for (account in selectedAccounts) {
                uris[i++] = GmailContract.Labels.getLabelsUri(account).toString()
                // TODO: only watch the individual label's URI (GmailContract.Labels.URI)
            }

            addWatchContentUris(uris)
        }
    }

    override fun onUpdateData(reason: Int) {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        var labelCanonical = sp.getString(PREF_LABEL, "i")
        val selectedAccounts = selectedAccounts

        if ("i" == labelCanonical) {
            labelCanonical = GmailContract.Labels.LabelCanonicalNames.CANONICAL_NAME_INBOX
        } else if ("p" == labelCanonical) {
            labelCanonical = GmailContract.Labels.LabelCanonicalNames.CANONICAL_NAME_PRIORITY_INBOX
        }

        var unread = 0
        val unreadPerAccount = ArrayList<Pair<String, Int>>()
        var lastUnreadLabelUri: String? = null

        for (account in selectedAccounts) {
            val cursor = tryOpenLabelsCursor(account)
            if (cursor == null || cursor.isAfterLast) {
                LOGD(TAG, "No Gmail inbox information found for account.")
                cursor?.close()
                continue
            }

            var accountUnread = 0

            while (cursor.moveToNext()) {
                val thisUnread = cursor.getInt(LabelsQuery.NUM_UNREAD_CONVERSATIONS)
                val thisCanonicalName = cursor.getString(LabelsQuery.CANONICAL_NAME)
                if (labelCanonical == thisCanonicalName) {
                    accountUnread = thisUnread
                    if (thisUnread > 0) {
                        lastUnreadLabelUri = cursor.getString(LabelsQuery.URI)
                    }
                    break
                } else if (!TextUtils.isEmpty(thisCanonicalName)
                    && thisCanonicalName.startsWith(SECTIONED_INBOX_CANONICAL_NAME_PREFIX)
                ) {
                    accountUnread += thisUnread
                    if (thisUnread > 0
                        && SECTIONED_INBOX_CANONICAL_NAME_PERSONAL == thisCanonicalName
                    ) {
                        lastUnreadLabelUri = cursor.getString(LabelsQuery.URI)
                    }
                }
            }

            if (accountUnread > 0) {
                unreadPerAccount.add(Pair(account, accountUnread))
                unread += accountUnread
            }

            cursor.close()
        }

        val body = StringBuilder()
        for (pair in unreadPerAccount) {
            if (pair.second == 0) {
                continue
            }

            if (body.isNotEmpty()) {
                body.append("\n")
            }
            body.append(pair.first).append(" (").append(pair.second).append(")")
        }

        var clickIntent: Intent? = null
        if (lastUnreadLabelUri != null) {
            try {
                clickIntent = Intent(Intent.ACTION_VIEW, Uri.parse(lastUnreadLabelUri))
                if (packageManager.resolveActivity(clickIntent, 0) == null) {
                    throw IllegalStateException("Gmail can't open this label directly.")
                }
            } catch (e: Exception) {
                LOGW(TAG, "Can't open Gmail label directly.", e)
                clickIntent = null
            }
        }

        if (clickIntent == null) {
            clickIntent = Intent(Intent.ACTION_MAIN)
                .setPackage("com.google.android.gm")
                .addCategory(Intent.CATEGORY_LAUNCHER)
        }

        publishUpdate(
            ExtensionData()
                .visible(unread > 0)
                .status(unread.toString())
                .expandedTitle(
                    resources.getQuantityString(
                        R.plurals.gmail_title_template, unread, unread
                    )
                )
                .icon(R.drawable.ic_extension_gmail)
                .expandedBody(body.toString())
                .clickIntent(clickIntent)
        )
    }

    private fun tryOpenLabelsCursor(account: String): Cursor? {
        return try {
            contentResolver.query(
                GmailContract.Labels.getLabelsUri(account),
                LabelsQuery.PROJECTION,
                null, // NOTE: the Labels API doesn't allow selections here
                null,
                null
            )
        } catch (e: Exception) {
            // From developer console: "Permission Denial: opening provider com.google.android.gsf..
            // From developer console: "SQLiteException: no such table: labels"
            // From developer console: "NullPointerException"
            LOGE(TAG, "Error opening Gmail labels", e)
            null
        }
    }

    private object LabelsQuery {
        val PROJECTION = arrayOf(
            GmailContract.Labels.NUM_UNREAD_CONVERSATIONS,
            GmailContract.Labels.URI,
            GmailContract.Labels.CANONICAL_NAME
        )

        const val NUM_UNREAD_CONVERSATIONS = 0
        const val URI = 1
        const val CANONICAL_NAME = 2
    }

    companion object {
        private val TAG = LogUtils.makeLogTag(GmailExtension::class.java)

        const val PREF_ACCOUNTS = "pref_gmail_accounts"
        const val PREF_LABEL = "pref_gmail_label"

        private const val ACCOUNT_TYPE_GOOGLE = "com.google"

        private const val SECTIONED_INBOX_CANONICAL_NAME_PREFIX = "^sq_ig_i_"
        private const val SECTIONED_INBOX_CANONICAL_NAME_PERSONAL = "^sq_ig_i_personal"

        //private static final String[] FEATURES_MAIL = {"service_mail"};

        @JvmStatic
        internal fun getAllAccountNames(context: Context): Array<String> {
            val accounts = AccountManager.get(context).getAccountsByType(ACCOUNT_TYPE_GOOGLE)
            return Array(accounts.size) { i -> accounts[i].name }
        }
    }
}
