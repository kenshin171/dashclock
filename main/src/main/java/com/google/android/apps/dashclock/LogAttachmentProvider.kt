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

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.android.apps.dashclock.LogUtils.makeLogTag
import java.io.File
import java.io.FileNotFoundException

/**
 * Content provider for exposing log files as attachments.
 */
class LogAttachmentProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        orderBy: String?
    ): Cursor? {
        // Ensure logfile exists
        val pathSegments = uri.pathSegments
        val fileName = pathSegments[0]
        var logFile = context?.cacheDir
        if (logFile == null) {
            LogUtils.LOGE(TAG, "No cache dir.")
            return null
        }

        logFile = File(File(logFile, "logs"), fileName)
        if (!logFile.exists()) {
            LogUtils.LOGE(TAG, "Requested log file doesn't exist.")
            return null
        }

        // Create matrix cursor
        val resolvedProjection = projection ?: arrayOf(
            "_data",
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE
        )

        val matrixCursor = MatrixCursor(resolvedProjection, 1)
        val row = arrayOfNulls<Any>(resolvedProjection.size)
        for (col in resolvedProjection.indices) {
            when (resolvedProjection[col]) {
                "_data" -> row[col] = logFile.absolutePath
                OpenableColumns.DISPLAY_NAME -> row[col] = fileName
                OpenableColumns.SIZE -> row[col] = logFile.length()
            }
        }
        matrixCursor.addRow(row)
        return matrixCursor
    }

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        return openFileHelper(uri, "r")
    }

    override fun getType(uri: Uri): String {
        return "text/plain"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("insert not supported")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException("delete not supported")
    }

    override fun update(
        uri: Uri,
        contentValues: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        throw UnsupportedOperationException("update not supported")
    }

    companion object {
        private val TAG = makeLogTag(LogAttachmentProvider::class.java)

        const val AUTHORITY = "com.google.android.apps.dashclock.logs"
    }
}
