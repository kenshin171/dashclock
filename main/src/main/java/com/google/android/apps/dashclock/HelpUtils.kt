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

package com.google.android.apps.dashclock

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.TextView
import net.nurik.roman.dashclock.R

/**
 * Helper class for showing about dialogs and what not.
 */
object HelpUtils {
    @JvmStatic
    fun showAboutDialog(activity: Activity) {
        val fm = activity.fragmentManager
        val ft = fm.beginTransaction()
        val prev = fm.findFragmentByTag("dialog_about")
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)

        AboutDialog().show(ft, "dialog_about")
    }

    class AboutDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            // Get app version
            val pm = activity.packageManager
            val packageName = activity.packageName
            val versionName: String = try {
                val info = pm.getPackageInfo(packageName, 0)
                info.versionName ?: VERSION_UNAVAILABLE
            } catch (e: PackageManager.NameNotFoundException) {
                VERSION_UNAVAILABLE
            }

            // Build the about body view and append the link to see OSS licenses
            val themedContext = ContextThemeWrapper(activity, R.style.Theme_Dialog)
            val layoutInflater = LayoutInflater.from(themedContext)
            val rootView = layoutInflater.inflate(R.layout.dialog_about, null)
            val nameAndVersionView = rootView.findViewById<TextView>(R.id.app_name_and_version)
            @Suppress("DEPRECATION")
            nameAndVersionView.text = Html.fromHtml(
                getString(R.string.app_name_and_version, versionName)
            )

            val aboutBodyView = rootView.findViewById<TextView>(R.id.about_body)
            @Suppress("DEPRECATION")
            aboutBodyView.text = Html.fromHtml(getString(R.string.about_body))
            aboutBodyView.movementMethod = LinkMovementMethod()

            return AlertDialog.Builder(activity, R.style.Theme_Dialog)
                .setView(rootView)
                .setPositiveButton(R.string.close) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
        }

        companion object {
            private const val VERSION_UNAVAILABLE = "N/A"
        }
    }
}
