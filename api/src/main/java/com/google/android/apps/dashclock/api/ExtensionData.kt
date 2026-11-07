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

package com.google.android.apps.dashclock.api

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import org.json.JSONException
import org.json.JSONObject
import java.net.URISyntaxException

/**
 * A parcelable, serializable object representing data related to a [DashClockExtension] that
 * should be shown to the user.
 *
 * This class follows the fluent interface style, using method chaining to provide for more
 * readable code.
 *
 * @see DashClockExtension.publishUpdate
 */
class ExtensionData : Parcelable {
    private var mVisible = false
    private var mIcon = 0
    private var mIconUri: Uri? = null
    private var mStatus: String? = null
    private var mExpandedTitle: String? = null
    private var mExpandedBody: String? = null
    private var mClickIntent: Intent? = null
    private var mContentDescription: String? = null

    constructor()

    /**
     * Returns whether or not the relevant extension should be visible (whether or not there is
     * relevant information to show to the user about the extension). Default false.
     */
    fun visible(): Boolean = mVisible

    /**
     * Sets whether or not the relevant extension should be visible (whether or not there is
     * relevant information to show to the user about the extension). Default false.
     */
    fun visible(visible: Boolean): ExtensionData {
        mVisible = visible
        return this
    }

    /**
     * Returns the ID of the drawable resource within the extension's package that represents this
     * data. Default 0.
     */
    fun icon(): Int = mIcon

    /**
     * Sets the ID of the drawable resource within the extension's package that represents this
     * data.
     */
    fun icon(icon: Int): ExtensionData {
        mIcon = icon
        return this
    }

    /**
     * Returns the content:// URI of a bitmap representing this data. Default null.
     *
     * @since Protocol Version 2 (API r2.x)
     */
    fun iconUri(): Uri? = mIconUri

    /**
     * Sets the content:// URI of the bitmap representing this data. This takes precedence over
     * the regular [icon] resource ID if set.
     *
     * @since Protocol Version 2 (API r2.x)
     */
    fun iconUri(iconUri: Uri?): ExtensionData {
        mIconUri = iconUri
        return this
    }

    /**
     * Returns the short string representing this data, to be shown in DashClock's collapsed form.
     * Default null.
     */
    fun status(): String? = mStatus

    /**
     * Sets the short string representing this data, to be shown in DashClock's collapsed form.
     */
    fun status(status: String?): ExtensionData {
        mStatus = status
        return this
    }

    /**
     * Returns the expanded title representing this data. Generally a longer form of
     * [status]. Default null.
     */
    fun expandedTitle(): String? = mExpandedTitle

    /**
     * Sets the expanded title representing this data. Generally a longer form of
     * [status].
     */
    fun expandedTitle(expandedTitle: String?): ExtensionData {
        mExpandedTitle = expandedTitle
        return this
    }

    /**
     * Returns the expanded body text representing this data. Default null.
     */
    fun expandedBody(): String? = mExpandedBody

    /**
     * Sets the expanded body text (below the expanded title), representing this data.
     */
    fun expandedBody(expandedBody: String?): ExtensionData {
        mExpandedBody = expandedBody
        return this
    }

    /**
     * Returns the click intent to start when the user clicks the status in DashClock. Default null.
     */
    fun clickIntent(): Intent? = mClickIntent

    /**
     * Sets the click intent to start when the user clicks the status in DashClock.
     */
    fun clickIntent(clickIntent: Intent?): ExtensionData {
        mClickIntent = clickIntent
        return this
    }

    /**
     * Returns the content description for this data, used for accessibility purposes.
     *
     * @since Protocol Version 2 (API r2.x)
     */
    fun contentDescription(): String? = mContentDescription

    /**
     * Sets the content description for this data.
     *
     * @since Protocol Version 2 (API v2.x)
     */
    fun contentDescription(contentDescription: String?): ExtensionData {
        mContentDescription = contentDescription
        return this
    }

    /**
     * Serializes the contents of this object to JSON.
     */
    @Throws(JSONException::class)
    fun serialize(): JSONObject {
        val data = JSONObject()
        data.put(KEY_VISIBLE, mVisible)
        data.put(KEY_ICON, mIcon)
        data.put(KEY_ICON_URI, mIconUri?.toString())
        data.put(KEY_STATUS, mStatus)
        data.put(KEY_EXPANDED_TITLE, mExpandedTitle)
        data.put(KEY_EXPANDED_BODY, mExpandedBody)
        data.put(KEY_CLICK_INTENT, mClickIntent?.toUri(0))
        data.put(KEY_CONTENT_DESCRIPTION, mContentDescription)
        return data
    }

    /**
     * Deserializes the given JSON representation of extension data, populating this
     * object.
     */
    @Throws(JSONException::class)
    fun deserialize(data: JSONObject) {
        this.mVisible = data.optBoolean(KEY_VISIBLE)
        this.mIcon = data.optInt(KEY_ICON)
        val iconUriString = data.optString(KEY_ICON_URI)
        this.mIconUri = if (TextUtils.isEmpty(iconUriString)) null else Uri.parse(iconUriString)
        this.mStatus = data.optString(KEY_STATUS)
        this.mExpandedTitle = data.optString(KEY_EXPANDED_TITLE)
        this.mExpandedBody = data.optString(KEY_EXPANDED_BODY)
        try {
            val clickIntentStr = data.optString(KEY_CLICK_INTENT)
            this.mClickIntent = if (TextUtils.isEmpty(clickIntentStr)) null else Intent.parseUri(clickIntentStr, 0)
        } catch (ignored: URISyntaxException) {
        }
        this.mContentDescription = data.optString(KEY_CONTENT_DESCRIPTION)
    }

    /**
     * Serializes the contents of this object to a [Bundle].
     */
    fun toBundle(): Bundle {
        val data = Bundle()
        data.putBoolean(KEY_VISIBLE, mVisible)
        data.putInt(KEY_ICON, mIcon)
        data.putString(KEY_ICON_URI, mIconUri?.toString())
        data.putString(KEY_STATUS, mStatus)
        data.putString(KEY_EXPANDED_TITLE, mExpandedTitle)
        data.putString(KEY_EXPANDED_BODY, mExpandedBody)
        data.putString(KEY_CLICK_INTENT, mClickIntent?.toUri(0))
        data.putString(KEY_CONTENT_DESCRIPTION, mContentDescription)
        return data
    }

    /**
     * Deserializes the given [Bundle] representation of extension data, populating this
     * object.
     */
    fun fromBundle(src: Bundle) {
        this.mVisible = src.getBoolean(KEY_VISIBLE, true)
        this.mIcon = src.getInt(KEY_ICON)
        val iconUriString = src.getString(KEY_ICON_URI)
        this.mIconUri = if (TextUtils.isEmpty(iconUriString)) null else Uri.parse(iconUriString)
        this.mStatus = src.getString(KEY_STATUS)
        this.mExpandedTitle = src.getString(KEY_EXPANDED_TITLE)
        this.mExpandedBody = src.getString(KEY_EXPANDED_BODY)
        try {
            val clickIntentStr = src.getString(KEY_CLICK_INTENT)
            this.mClickIntent = if (TextUtils.isEmpty(clickIntentStr)) null else Intent.parseUri(clickIntentStr, 0)
        } catch (ignored: URISyntaxException) {
        }
        this.mContentDescription = src.getString(KEY_CONTENT_DESCRIPTION)
    }

    private constructor(parcel: Parcel) {
        val parcelableVersion = parcel.readInt()
        val parcelableSize = parcel.readInt()
        val startPosition = parcel.dataPosition()
        if (parcelableVersion >= 1) {
            this.mVisible = parcel.readInt() != 0
            this.mIcon = parcel.readInt()
            this.mStatus = parcel.readString()
            if (TextUtils.isEmpty(this.mStatus)) {
                this.mStatus = null
            }
            this.mExpandedTitle = parcel.readString()
            if (TextUtils.isEmpty(this.mExpandedTitle)) {
                this.mExpandedTitle = null
            }
            this.mExpandedBody = parcel.readString()
            if (TextUtils.isEmpty(this.mExpandedBody)) {
                this.mExpandedBody = null
            }
            try {
                val clickIntentStr = parcel.readString()
                this.mClickIntent = if (TextUtils.isEmpty(clickIntentStr)) null else Intent.parseUri(clickIntentStr, 0)
            } catch (ignored: URISyntaxException) {
            }
        }
        if (parcelableVersion >= 2) {
            this.mContentDescription = parcel.readString()
            if (TextUtils.isEmpty(this.mContentDescription)) {
                this.mContentDescription = null
            }
            val iconUriString = parcel.readString()
            this.mIconUri = if (TextUtils.isEmpty(iconUriString)) null else Uri.parse(iconUriString)
        }
        if (parcelableVersion >= 2) {
            parcel.setDataPosition(startPosition + parcelableSize)
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(PARCELABLE_VERSION)
        val sizePosition = parcel.dataPosition()
        parcel.writeInt(0)
        val startPosition = parcel.dataPosition()
        // Version 1 below
        parcel.writeInt(if (mVisible) 1 else 0)
        parcel.writeInt(mIcon)
        parcel.writeString(if (TextUtils.isEmpty(mStatus)) "" else mStatus)
        parcel.writeString(if (TextUtils.isEmpty(mExpandedTitle)) "" else mExpandedTitle)
        parcel.writeString(if (TextUtils.isEmpty(mExpandedBody)) "" else mExpandedBody)
        parcel.writeString(mClickIntent?.toUri(0) ?: "")
        // Version 2 below
        parcel.writeString(if (TextUtils.isEmpty(mContentDescription)) "" else mContentDescription)
        parcel.writeString(mIconUri?.toString() ?: "")
        // Go back and write the size
        val parcelableSize = parcel.dataPosition() - startPosition
        parcel.setDataPosition(sizePosition)
        parcel.writeInt(parcelableSize)
        parcel.setDataPosition(startPosition + parcelableSize)
    }

    override fun describeContents(): Int = 0

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        return try {
            val otherData = other as ExtensionData
            otherData.mVisible == mVisible &&
                otherData.mIcon == mIcon &&
                objectEquals(otherData.mIconUri, mIconUri) &&
                TextUtils.equals(otherData.mStatus, mStatus) &&
                TextUtils.equals(otherData.mExpandedTitle, mExpandedTitle) &&
                TextUtils.equals(otherData.mExpandedBody, mExpandedBody) &&
                objectEquals(otherData.mClickIntent, mClickIntent) &&
                TextUtils.equals(otherData.mContentDescription, mContentDescription)
        } catch (e: ClassCastException) {
            false
        }
    }

    override fun toString(): String {
        return try {
            serialize().toString()
        } catch (e: JSONException) {
            super.toString()
        }
    }

    override fun hashCode(): Int {
        throw UnsupportedOperationException()
    }

    /**
     * Cleans up this object's data according to the size limits described by
     * [MAX_STATUS_LENGTH], [MAX_EXPANDED_TITLE_LENGTH], etc.
     */
    fun clean() {
        val status = mStatus
        if (!TextUtils.isEmpty(status) && status!!.length > MAX_STATUS_LENGTH) {
            mStatus = status.substring(0, MAX_STATUS_LENGTH)
        }
        val title = mExpandedTitle
        if (!TextUtils.isEmpty(title) && title!!.length > MAX_EXPANDED_TITLE_LENGTH) {
            mExpandedTitle = title.substring(0, MAX_EXPANDED_TITLE_LENGTH)
        }
        val body = mExpandedBody
        if (!TextUtils.isEmpty(body) && body!!.length > MAX_EXPANDED_BODY_LENGTH) {
            mExpandedBody = body.substring(0, MAX_EXPANDED_BODY_LENGTH)
        }
        val desc = mContentDescription
        if (!TextUtils.isEmpty(desc) && desc!!.length > MAX_EXPANDED_BODY_LENGTH) {
            mContentDescription = desc.substring(0, MAX_CONTENT_DESCRIPTION_LENGTH)
        }
    }

    companion object {
        const val PARCELABLE_VERSION = 2

        private const val KEY_VISIBLE = "visible"
        private const val KEY_ICON = "icon"
        private const val KEY_ICON_URI = "icon_uri"
        private const val KEY_STATUS = "status"
        private const val KEY_EXPANDED_TITLE = "title"
        private const val KEY_EXPANDED_BODY = "body"
        private const val KEY_CLICK_INTENT = "click_intent"
        private const val KEY_CONTENT_DESCRIPTION = "content_description"

        const val MAX_STATUS_LENGTH = 32
        const val MAX_EXPANDED_TITLE_LENGTH = 100
        const val MAX_EXPANDED_BODY_LENGTH = 1000
        const val MAX_CONTENT_DESCRIPTION_LENGTH = 32 +
            MAX_STATUS_LENGTH + MAX_EXPANDED_TITLE_LENGTH + MAX_EXPANDED_BODY_LENGTH

        @JvmField
        val CREATOR: Parcelable.Creator<ExtensionData> = object : Parcelable.Creator<ExtensionData> {
            override fun createFromParcel(parcel: Parcel): ExtensionData = ExtensionData(parcel)
            override fun newArray(size: Int): Array<ExtensionData?> = arrayOfNulls(size)
        }

        private fun objectEquals(x: Any?, y: Any?): Boolean {
            return if (x == null || y == null) {
                x === y
            } else {
                x == y
            }
        }

        /**
         * Returns true if the two provided data objects are equal (or both null).
         */
        @JvmStatic
        fun equals(x: ExtensionData?, y: ExtensionData?): Boolean {
            return if (x == null || y == null) {
                x === y
            } else {
                x == y
            }
        }
    }
}
