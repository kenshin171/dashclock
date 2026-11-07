/*
 * Copyright 2014 Google Inc.
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

package com.google.android.apps.dashclock.api.host

import android.content.ComponentName
import android.os.Parcel
import android.os.Parcelable

/**
 * A parcelable, serializable object containing information about an
 * [com.google.android.apps.dashclock.api.DashClockExtension] shared with your
 * registered [DashClockHost].
 */
class ExtensionListing : Parcelable {
    private var mComponentName: ComponentName? = null
    private var mProtocolVersion = 0
    private var mCompatible = false
    private var mWorldReadable = false
    private var mTitle: String? = null
    private var mDescription: String? = null
    private var mIcon = 0
    private var mSettingsActivity: ComponentName? = null

    constructor()

    fun componentName(): ComponentName? = mComponentName

    fun componentName(componentName: ComponentName?): ExtensionListing {
        mComponentName = componentName
        return this
    }

    fun protocolVersion(): Int = mProtocolVersion

    fun protocolVersion(protocolVersion: Int): ExtensionListing {
        mProtocolVersion = protocolVersion
        return this
    }

    fun compatible(): Boolean = mCompatible

    fun compatible(compatible: Boolean): ExtensionListing {
        mCompatible = compatible
        return this
    }

    fun worldReadable(): Boolean = mWorldReadable

    fun worldReadable(worldReadable: Boolean): ExtensionListing {
        mWorldReadable = worldReadable
        return this
    }

    fun title(): String? = mTitle

    fun title(title: String?): ExtensionListing {
        mTitle = title
        return this
    }

    fun description(): String? = mDescription

    fun description(description: String?): ExtensionListing {
        mDescription = description
        return this
    }

    fun icon(): Int = mIcon

    fun icon(icon: Int): ExtensionListing {
        mIcon = icon
        return this
    }

    fun settingsActivity(): ComponentName? = mSettingsActivity

    fun settingsActivity(settingsActivity: ComponentName?): ExtensionListing {
        this.mSettingsActivity = settingsActivity
        return this
    }

    private constructor(parcel: Parcel) {
        val parcelableVersion = parcel.readInt()
        if (parcelableVersion >= 1) {
            mComponentName = ComponentName.readFromParcel(parcel)
            mProtocolVersion = parcel.readInt()
            mCompatible = parcel.readInt() == 1
            mWorldReadable = parcel.readInt() == 1
            mTitle = parcel.readString()
            mDescription = parcel.readString()
            mIcon = parcel.readInt()
            val hasSettings = parcel.readInt() == 1
            if (hasSettings) {
                mSettingsActivity = ComponentName.readFromParcel(parcel)
            }
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(PARCELABLE_VERSION)

        // Version 1 below
        mComponentName!!.writeToParcel(parcel, 0)
        parcel.writeInt(mProtocolVersion)
        parcel.writeInt(if (mCompatible) 1 else 0)
        parcel.writeInt(if (mWorldReadable) 1 else 0)
        parcel.writeString(mTitle)
        parcel.writeString(mDescription)
        parcel.writeInt(mIcon)
        parcel.writeInt(if (mSettingsActivity != null) 1 else 0)
        mSettingsActivity?.writeToParcel(parcel, 0)
    }

    override fun describeContents(): Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExtensionListing) return false

        if (mIcon != other.mIcon) return false
        if (mProtocolVersion != other.mProtocolVersion) return false
        if (mCompatible != other.mCompatible) return false
        if (mWorldReadable != other.mWorldReadable) return false
        if (mComponentName != other.mComponentName) return false
        if (mDescription != other.mDescription) return false
        if (mSettingsActivity != other.mSettingsActivity) return false
        if (mTitle != other.mTitle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mComponentName?.hashCode() ?: 0
        result = 31 * result + mProtocolVersion
        result = 31 * result + if (mCompatible) 1 else 0
        result = 31 * result + if (mWorldReadable) 1 else 0
        result = 31 * result + (mTitle?.hashCode() ?: 0)
        result = 31 * result + (mDescription?.hashCode() ?: 0)
        result = 31 * result + mIcon
        result = 31 * result + (mSettingsActivity?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "ExtensionListing[component=$mComponentName, compatible=$mCompatible, worldReadable=$mWorldReadable, title=$mTitle]"
    }

    companion object {
        const val PARCELABLE_VERSION = 1

        @JvmField
        val CREATOR: Parcelable.Creator<ExtensionListing> =
            object : Parcelable.Creator<ExtensionListing> {
                override fun createFromParcel(parcel: Parcel): ExtensionListing =
                    ExtensionListing(parcel)

                override fun newArray(size: Int): Array<ExtensionListing?> = arrayOfNulls(size)
            }
    }
}
