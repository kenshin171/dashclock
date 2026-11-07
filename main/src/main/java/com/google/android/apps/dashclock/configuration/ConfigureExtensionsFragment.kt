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

package com.google.android.apps.dashclock.configuration

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.app.Fragment
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Vibrator
import android.text.Html
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.apps.dashclock.DashClockService
import com.google.android.apps.dashclock.ExtensionManager
import com.google.android.apps.dashclock.ExtensionSettingActivityProxy
import com.google.android.apps.dashclock.Utils
import com.google.android.apps.dashclock.api.host.ExtensionListing
import com.google.android.apps.dashclock.ui.SwipeDismissListViewTouchListener
import com.google.android.apps.dashclock.ui.UndoBarController
import com.mobeta.android.dslv.DragSortController
import com.mobeta.android.dslv.DragSortListView
import net.nurik.roman.dashclock.R
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.HashMap
import java.util.HashSet

/**
 * Fragment for allowing the user to configure active extensions, shown within a [ConfigurationActivity].
 */
class ConfigureExtensionsFragment : Fragment(),
    ExtensionManager.OnChangeListener,
    UndoBarController.UndoListener {

    private lateinit var mExtensionManager: ExtensionManager

    private var mSelectedExtensions: MutableList<ComponentName> = ArrayList()
    private var mSelectedExtensionsAdapter: ExtensionListAdapter? = null

    private val mExtensionListings: MutableMap<ComponentName, ExtensionListing> = HashMap()
    private val mExtensionIcons: MutableMap<ComponentName, BitmapDrawable> = HashMap()
    private val mAvailableExtensions: MutableList<ComponentName> = ArrayList()
    private var mAddExtensionPopupMenu: PopupMenu? = null

    private val mPackageChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            repopulateAvailableExtensions()
        }
    }

    private lateinit var mListView: DragSortListView
    private lateinit var mSwipeDismissTouchListener: SwipeDismissListViewTouchListener
    private var mUndoBarController: UndoBarController? = null
    private var mAddFabView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up helper components
        mExtensionManager = ExtensionManager.getInstance(activity)
        mExtensionManager.addOnChangeListener(this)

        if (savedInstanceState == null) {
            // TODO: should load in the correct order
            mSelectedExtensions = mExtensionManager.getInternalActiveExtensionNames().toMutableList()
        } else {
            val selected = savedInstanceState.getStringArrayList(SAVE_KEY_SELECTED_EXTENSIONS)
            if (selected != null) {
                for (s in selected) {
                    val cn = ComponentName.unflattenFromString(s)
                    if (cn != null) {
                        mSelectedExtensions.add(cn)
                    }
                }
            }
        }

        mSelectedExtensionsAdapter = ExtensionListAdapter()

        repopulateAvailableExtensions()

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            activity,
            mPackageChangedReceiver,
            packageFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(
            R.layout.fragment_configure_extensions, container, false
        ) as ViewGroup

        mListView = rootView.findViewById<View>(android.R.id.list) as DragSortListView
        mListView.adapter = mSelectedExtensionsAdapter
        mListView.emptyView = rootView.findViewById(android.R.id.empty)

        val dragSortController = ConfigurationDragSortController()
        mListView.setFloatViewManager(dragSortController)
        mListView.setDropListener { from, to ->
            val name = mSelectedExtensions.removeAt(from)
            mSelectedExtensions.add(to, name)
            mSelectedExtensionsAdapter?.notifyDataSetChanged()
            mExtensionManager.setInternalActiveExtensions(mSelectedExtensions)
        }
        mSwipeDismissTouchListener = SwipeDismissListViewTouchListener(
            mListView,
            object : SwipeDismissListViewTouchListener.DismissCallbacks {
                override fun canDismiss(position: Int): Boolean {
                    return true
                }

                override fun onDismiss(listView: ListView, reverseSortedPositions: IntArray) {
                    showRemoveUndoBar(reverseSortedPositions)
                    for (position in reverseSortedPositions) {
                        mSelectedExtensions.removeAt(position)
                    }
                    repopulateAvailableExtensions()
                    mSelectedExtensionsAdapter?.notifyDataSetChanged()
                    mExtensionManager.setInternalActiveExtensions(mSelectedExtensions)
                }
            }
        )
        mListView.setOnScrollListener(mSwipeDismissTouchListener.makeScrollListener())
        mListView.setOnTouchListener { view, motionEvent ->
            if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN) {
                mUndoBarController?.hideUndoBar(false)
            }

            dragSortController.onTouch(view, motionEvent) ||
                (!dragSortController.isDragging() &&
                    mSwipeDismissTouchListener.onTouch(view, motionEvent))
        }

        mListView.itemsCanFocus = true

        mAddFabView = rootView.findViewById<View>(R.id.add_extension_fab).apply {
            setOnClickListener { view ->
                showAddExtensionMenu(view)
            }
        }
        showHideAddFabView(false)

        mUndoBarController = UndoBarController(rootView.findViewById(R.id.undobar), this).apply {
            onRestoreInstanceState(savedInstanceState)
        }

        return rootView
    }

    override fun onPause() {
        super.onPause()
        mExtensionManager.setInternalActiveExtensions(mSelectedExtensions)
    }

    override fun onDestroy() {
        super.onDestroy()
        activity.unregisterReceiver(mPackageChangedReceiver)
        mExtensionManager.removeOnChangeListener(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val selectedExtensions = ArrayList<String>()
        for (cn in mSelectedExtensions) {
            selectedExtensions.add(cn.flattenToString())
        }
        outState.putStringArrayList(SAVE_KEY_SELECTED_EXTENSIONS, selectedExtensions)
        mUndoBarController?.onSaveInstanceState(outState)
    }

    private fun repopulateAvailableExtensions() {
        val selectedExtensions: MutableSet<ComponentName> = HashSet()
        selectedExtensions.addAll(mSelectedExtensions)
        var selectedExtensionsDirty = false

        mExtensionListings.clear()
        mAvailableExtensions.clear()

        val res = resources

        for (listing in mExtensionManager.getAvailableExtensions()) {
            val extension = listing.componentName() ?: continue
            mExtensionListings[extension] = listing
            val previousListing = mExtensionListings.put(extension, listing)

            if (listing.icon() != 0) {
                @Suppress("DEPRECATION")
                val icon = Utils.loadExtensionIcon(
                    activity,
                    extension,
                    listing.icon(),
                    null,
                    res.getColor(R.color.extension_item_color)
                )
                mExtensionIcons[extension] = BitmapDrawable(res, icon)
            }

            if (selectedExtensions.contains(extension)) {
                if (previousListing == null) {
                    // Extension was newly detected
                    selectedExtensionsDirty = true
                }
                if (!listing.compatible()) {
                    // If the extension is selected and its protocol isn't supported,
                    // then make it available but remove it from the selection.
                    mSelectedExtensions.remove(listing.componentName())
                    selectedExtensionsDirty = true
                } else {
                    // If it's selected and supported, don't add it to the list of available
                    // extensions.
                    continue
                }
            }

            mAvailableExtensions.add(extension)
        }

        Collections.sort(mAvailableExtensions, Comparator { cn1, cn2 ->
            val listing1 = mExtensionListings[cn1]
            val listing2 = mExtensionListings[cn2]
            val title1 = listing1?.title() ?: ""
            val title2 = listing2?.title() ?: ""
            title1.compareTo(title2, ignoreCase = true)
        })

        showHideAddFabView(true)

        if (selectedExtensionsDirty && mSelectedExtensionsAdapter != null) {
            mSelectedExtensionsAdapter?.notifyDataSetChanged()
            mExtensionManager.setInternalActiveExtensions(mSelectedExtensions)
        }

        mAddExtensionPopupMenu?.dismiss()
        mAddExtensionPopupMenu = null
    }

    private fun showHideAddFabView(animate: Boolean) {
        val addFabView = mAddFabView ?: return

        val show = mAvailableExtensions.size > 0
        val lastValue = addFabView.tag as? Boolean
        if (lastValue == null || lastValue != show) {
            if (show) {
                addFabView.visibility = View.VISIBLE
            }
            addFabView.animate()
                .setDuration(if (animate) 200 else 0)
                .scaleX(if (show) 1f else 0f)
                .scaleY(if (show) 1f else 0f)
                .withEndAction {
                    if (!show) {
                        addFabView.visibility = View.GONE
                    }
                }
        }
    }

    private fun showAddExtensionMenu(anchorView: View) {
        mAddExtensionPopupMenu?.dismiss()

        val popupMenu = PopupMenu(activity, anchorView)
        mAddExtensionPopupMenu = popupMenu

        for (i in mAvailableExtensions.indices) {
            val cn = mAvailableExtensions[i]
            val listing = mExtensionListings[cn]
            var label = listing?.title()
            if (TextUtils.isEmpty(label)) {
                label = cn.flattenToShortString()
            }

            if (listing != null && !listing.compatible()) {
                label = getString(R.string.incompatible_extension_menu_template, label)
            }

            popupMenu.menu.add(Menu.NONE, i, Menu.NONE, label)
        }
        popupMenu.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { menuItem ->
            mAddExtensionPopupMenu?.dismiss()
            mAddExtensionPopupMenu = null

            val cn = mAvailableExtensions[menuItem.itemId]
            val extensionListing = mExtensionListings[cn]
            if (extensionListing == null || !extensionListing.compatible()) {
                var title = cn.shortClassName
                if (extensionListing != null) {
                    title = extensionListing.title()
                }
                CantAddExtensionDialog
                    .createInstance(cn.packageName, title)
                    .show(fragmentManager, "cantaddextension")
                return@OnMenuItemClickListener true
            }

            // Item id == position for this popup menu.
            mSelectedExtensions.add(cn)
            repopulateAvailableExtensions()
            mSelectedExtensionsAdapter?.notifyDataSetChanged()
            mListView.smoothScrollToPosition((mSelectedExtensionsAdapter?.count ?: 1) - 1)
            true
        })
        popupMenu.show()
    }

    private inner class ConfigurationDragSortController : DragSortController(
        this@ConfigureExtensionsFragment.mListView,
        R.id.drag_handle,
        ON_DOWN,
        0
    ) {
        private var mPos = 0
        private var mDragging = false

        init {
            isRemoveEnabled = false
            mDragging = false
        }

        fun isDragging(): Boolean {
            return mDragging
        }

        override fun startDrag(position: Int, deltaX: Int, deltaY: Int): Boolean {
            mDragging = super.startDrag(position, deltaX, deltaY)
            return mDragging
        }

        override fun onTouch(v: View, ev: MotionEvent): Boolean {
            val ret = super.onTouch(v, ev)
            when (ev.action) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mDragging = false
            }
            return ret
        }

        override fun onCreateFloatView(position: Int): View? {
            val v = this@ConfigureExtensionsFragment.mListView
                .context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            @Suppress("DEPRECATION")
            v.vibrate(10)
            mPos = position
            return mSelectedExtensionsAdapter?.getView(position, null, mListView)
        }

        override fun onDestroyFloatView(floatView: View) {
            // do nothing; block super from crashing
        }
    }

    override fun onExtensionsChanged(sourceExtension: ComponentName?) {
        repopulateAvailableExtensions()
    }

    private fun showRemoveUndoBar(reverseSortedPositions: IntArray) {
        val undoString: String = if (reverseSortedPositions.size == 1) {
            val listing = mExtensionListings[mSelectedExtensions[reverseSortedPositions[0]]]
            getString(
                R.string.extension_removed_template,
                listing?.title() ?: "??"
            )
        } else {
            getString(
                R.string.extensions_removed_template,
                reverseSortedPositions.size
            )
        }

        val extensions = arrayOfNulls<ComponentName>(reverseSortedPositions.size)
        for (i in reverseSortedPositions.indices) {
            extensions[i] = mSelectedExtensions[reverseSortedPositions[i]]
        }

        val undoBundle = Bundle().apply {
            putIntArray("positions", reverseSortedPositions)
            putParcelableArray("extensions", extensions)
        }
        mUndoBarController?.showUndoBar(
            false,
            undoString,
            undoBundle
        )
    }

    override fun onUndo(token: Bundle?) {
        if (token == null) {
            return
        }

        // Perform the undo
        val reverseSortedPositions = token.getIntArray("positions") ?: return
        @Suppress("DEPRECATION")
        val extensions = token.getParcelableArray("extensions") ?: return

        for (i in reverseSortedPositions.indices) {
            mSelectedExtensions.add(reverseSortedPositions[i], extensions[i] as ComponentName)
        }

        repopulateAvailableExtensions()
        mSelectedExtensionsAdapter?.notifyDataSetChanged()
    }

    inner class ExtensionListAdapter : BaseAdapter() {
        override fun getViewTypeCount(): Int {
            return 1
        }

        override fun hasStableIds(): Boolean {
            return true
        }

        override fun getCount(): Int {
            return mSelectedExtensions.size
        }

        override fun getItemViewType(position: Int): Int {
            return VIEW_TYPE_ITEM
        }

        override fun getItem(position: Int): Any {
            return mSelectedExtensions[position]
        }

        override fun getItemId(position: Int): Long {
            return mSelectedExtensions[position].hashCode().toLong()
        }

        override fun isEnabled(position: Int): Boolean {
            return false
        }

        override fun areAllItemsEnabled(): Boolean {
            return false
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
            when (getItemViewType(position)) {
                VIEW_TYPE_ITEM -> {
                    val cn = getItem(position) as ComponentName
                    val view = convertView ?: activity.layoutInflater
                        .inflate(R.layout.list_item_extension, parent, false)

                    val titleView = view.findViewById<View>(android.R.id.text1) as TextView
                    val descriptionView = view.findViewById<View>(android.R.id.text2) as TextView
                    val iconView = view.findViewById<View>(android.R.id.icon1) as ImageView
                    val overflowButton = view.findViewById<View>(R.id.overflow_button)

                    val listing = mExtensionListings[cn]
                    if (listing == null || TextUtils.isEmpty(listing.title())) {
                        iconView.setImageBitmap(null)
                        titleView.text = cn.flattenToShortString()
                        descriptionView.visibility = View.GONE
                        overflowButton.visibility = View.GONE
                    } else {
                        iconView.setImageDrawable(mExtensionIcons[listing.componentName()])
                        titleView.text = listing.title()
                        descriptionView.visibility =
                            if (TextUtils.isEmpty(listing.description())) View.GONE else View.VISIBLE
                        descriptionView.text = listing.description()
                        overflowButton.visibility = View.VISIBLE
                        overflowButton.setOnClickListener { v ->
                            mUndoBarController?.hideUndoBar(false)
                            val menu = PopupMenu(v.context, v)
                            menu.inflate(R.menu.configure_item_overflow)
                            if (listing.settingsActivity() == null) {
                                menu.menu.findItem(R.id.action_settings).isVisible = false
                            }
                            menu.setOnMenuItemClickListener(OverflowItemClickListener(position))
                            menu.show()
                        }
                    }
                    return view
                }
            }

            return null
        }

        private inner class OverflowItemClickListener(private val mPosition: Int) :
            PopupMenu.OnMenuItemClickListener {

            override fun onMenuItemClick(item: MenuItem): Boolean {
                if (mPosition > count) {
                    return false
                }

                when (item.itemId) {
                    R.id.action_remove -> {
                        mSwipeDismissTouchListener.dismiss(mPosition)
                        return true
                    }

                    R.id.action_settings -> {
                        val cn = getItem(mPosition) as ComponentName
                        val listing = mExtensionListings[cn] ?: return false

                        showExtensionSettings(activity, listing)
                        return true
                    }
                }
                return false
            }
        }
    }

    fun showExtensionSettings(context: Context, listing: ExtensionListing) {
        val settingsActivity = listing.settingsActivity() ?: return

        // Start the proxy activity
        val i = Intent(context, ExtensionSettingActivityProxy::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(DashClockService.EXTRA_COMPONENT_NAME, listing.componentName()?.flattenToString())
            putExtra(
                ExtensionSettingActivityProxy.EXTRA_SETTINGS_ACTIVITY,
                settingsActivity.flattenToString()
            )
        }
        startActivity(i)
    }

    class CantAddExtensionDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val extensionTitle = arguments.getString(ARG_EXTENSION_TITLE)
            val extensionPackageName = arguments.getString(ARG_EXTENSION_PACKAGE_NAME)
            @Suppress("DEPRECATION")
            return AlertDialog.Builder(activity, R.style.Theme_Dialog)
                .setTitle(R.string.incompatible_extension_title)
                .setMessage(
                    Html.fromHtml(
                        getString(
                            R.string.incompatible_extension_message_search_play_template,
                            extensionTitle
                        )
                    )
                )
                .setPositiveButton(R.string.search_play) { dialog, _ ->
                    val playIntent = Intent(Intent.ACTION_VIEW).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        data = Uri.parse(
                            "https://play.google.com/store/apps/details?id=$extensionPackageName"
                        )
                    }
                    try {
                        startActivity(playIntent)
                    } catch (ignored: ActivityNotFoundException) {
                    }

                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
        }

        companion object {
            private const val ARG_EXTENSION_TITLE = "title"
            private const val ARG_EXTENSION_PACKAGE_NAME = "package_name"

            @JvmStatic
            fun createInstance(
                extensionPackageName: String?,
                extensionTitle: String?
            ): CantAddExtensionDialog {
                val fragment = CantAddExtensionDialog()
                val args = Bundle().apply {
                    putString(ARG_EXTENSION_TITLE, extensionTitle)
                    putString(ARG_EXTENSION_PACKAGE_NAME, extensionPackageName)
                }
                fragment.arguments = args
                return fragment
            }
        }
    }

    companion object {
        private const val SAVE_KEY_SELECTED_EXTENSIONS = "selected_extensions"
        private const val VIEW_TYPE_ITEM = 0
    }
}
