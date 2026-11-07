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

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import net.nurik.roman.dashclock.BuildConfig
import net.nurik.roman.dashclock.R
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Helper methods that make logging more consistent throughout the app.
 */
object LogUtils {
    private const val LOG_PREFIX = "dashclock_"
    private const val LOG_PREFIX_LENGTH = LOG_PREFIX.length
    private const val MAX_LOG_TAG_LENGTH = 23

    private val TAG = makeLogTag(LogUtils::class.java)

    @JvmStatic
    fun makeLogTag(str: String): String {
        if (str.length > MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH) {
            return LOG_PREFIX + str.substring(0, MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH - 1)
        }

        return LOG_PREFIX + str
    }

    /**
     * WARNING: Don't use this when obfuscating class names with Proguard!
     */
    @JvmStatic
    fun makeLogTag(cls: Class<*>): String {
        return makeLogTag(cls.simpleName)
    }

    @JvmStatic
    fun LOGD(tag: String, message: String) {
        if (BuildConfig.DEBUG || Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message)
        }
    }

    @JvmStatic
    fun LOGD(tag: String, message: String, cause: Throwable?) {
        if (BuildConfig.DEBUG || Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message, cause)
        }
    }

    @JvmStatic
    fun LOGV(tag: String, message: String) {
        if (BuildConfig.DEBUG && Log.isLoggable(tag, Log.VERBOSE)) {
            Log.v(tag, message)
        }
    }

    @JvmStatic
    fun LOGV(tag: String, message: String, cause: Throwable?) {
        if (BuildConfig.DEBUG && Log.isLoggable(tag, Log.VERBOSE)) {
            Log.v(tag, message, cause)
        }
    }

    @JvmStatic
    fun LOGI(tag: String, message: String) {
        Log.i(tag, message)
    }

    @JvmStatic
    fun LOGI(tag: String, message: String, cause: Throwable?) {
        Log.i(tag, message, cause)
    }

    @JvmStatic
    fun LOGW(tag: String, message: String) {
        Log.w(tag, message)
    }

    @JvmStatic
    fun LOGW(tag: String, message: String, cause: Throwable?) {
        Log.w(tag, message, cause)
    }

    @JvmStatic
    fun LOGE(tag: String, message: String) {
        Log.e(tag, message)
    }

    @JvmStatic
    fun LOGE(tag: String, message: String, cause: Throwable?) {
        Log.e(tag, message, cause)
    }

    /**
     * Only for use with debug versions of the app!
     */
    @JvmStatic
    fun sendDebugLog(context: Context) {
        if (BuildConfig.DEBUG) {
            val log = StringBuilder()

            // Append app version name
            val pm = context.packageManager
            val packageName = context.packageName
            val versionName: String = try {
                val info = pm.getPackageInfo(packageName, 0)
                info.versionName ?: "??"
            } catch (e: PackageManager.NameNotFoundException) {
                "??"
            }
            log.append("App version:\n").append(versionName).append("\n\n")

            // Append device build fingerprint
            log.append("Device fingerprint:\n").append(Build.FINGERPRINT).append("\n\n")

            try {
                // Append app's logs
                val logcatCmd = arrayOf("logcat", "-v", "threadtime", "-d")
                val process = Runtime.getRuntime().exec(logcatCmd)
                val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (bufferedReader.readLine().also { line = it } != null) {
                    log.append(line)
                    log.append("\n")
                }

                // Write everything to a file
                val cacheDir = context.cacheDir ?: throw IOException("Cache directory inaccessible")
                val logsDir = File(cacheDir, "logs")
                deleteRecursive(logsDir)
                logsDir.mkdirs()

                val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val fileName = "DashClock_log_${sdf.format(Date())}.txt"
                val logFile = File(logsDir, fileName)

                val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(logFile)))
                writer.write(log.toString())
                writer.close()

                // Send the file
                val sendIntent = Intent(Intent.ACTION_SENDTO)
                    .setData(Uri.parse("mailto:dashclock+support@gmail.com"))
                    .putExtra(Intent.EXTRA_SUBJECT, "DashClock debug log")
                    .putExtra(
                        Intent.EXTRA_STREAM,
                        Uri.parse("content://${LogAttachmentProvider.AUTHORITY}/$fileName")
                    )
                context.startActivity(
                    Intent.createChooser(
                        sendIntent,
                        context.getString(R.string.send_logs_chooser_title)
                    )
                )
            } catch (e: IOException) {
                LOGE(TAG, "Error accessing or sending app's logs.", e)
                Toast.makeText(
                    context,
                    "Error accessing or sending app's logs.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun deleteRecursive(file: File?) {
        if (file != null) {
            val children = file.listFiles()
            if (!children.isNullOrEmpty()) {
                for (child in children) {
                    deleteRecursive(child)
                }
            } else {
                file.delete()
            }
        }
    }
}
