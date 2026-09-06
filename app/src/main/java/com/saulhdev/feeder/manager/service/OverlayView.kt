package com.saulhdev.feeder.manager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.libraries.gsa.d.a.OverlayController
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.saulhdev.feeder.MainActivity
import com.saulhdev.feeder.NeoApp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.entity.MenuItem
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.manager.sync.SyncRestClient
import com.saulhdev.feeder.ui.feed.FeedAdapter
import com.saulhdev.feeder.ui.navigation.Routes
import com.saulhdev.feeder.ui.theme.CardTheme
import com.saulhdev.feeder.ui.theme.OverlayThemeHolder
import com.saulhdev.feeder.ui.views.AbstractFloatingView
import com.saulhdev.feeder.ui.views.DialogMenu
import com.saulhdev.feeder.ui.views.FilterBottomSheet
import com.saulhdev.feeder.utils.Android
import com.saulhdev.feeder.utils.LinearLayoutManagerWrapper
import com.saulhdev.feeder.utils.extensions.isDark
import com.saulhdev.feeder.utils.extensions.safeStartActivity
import com.saulhdev.feeder.utils.extensions.setCustomTheme
import com.saulhdev.feeder.viewmodels.ArticleListViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject

class OverlayView(val context: Context) :
    OverlayController(context, R.style.AppTheme, R.style.WindowTheme),
    KoinComponent, OverlayBridge.OverlayBridgeCallback,
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val _viewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = _viewModelStore

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var themeHolder: OverlayThemeHolder
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("NeoFeedSync"))
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var bookmarkCollectorJob: Job? = null
    private var articleCollectorJob: Job? = null
    private val viewModel: ArticleListViewModel by inject(ArticleListViewModel::class.java)
    private val articles: SyncRestClient by inject(SyncRestClient::class.java)
    private val articleRepo: ArticleRepository by inject(ArticleRepository::class.java)
    val prefs: FeedPreferences by inject()

    var bookmarkVisible = false
    private var pendingCloseOnResume = false

    private lateinit var rootView: View
    private lateinit var adapter: FeedAdapter

    private val closeSystemDialogsReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                closePanelIfNeeded(1)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        savedStateRegistryController.performRestore(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        super.onCreate(savedInstanceState)

        container?.setViewTreeLifecycleOwner(this)
        container?.setViewTreeViewModelStoreOwner(this)
        container?.setViewTreeSavedStateRegistryOwner(this)

        try {
            window?.decorView?.let { decor ->
                decor.setViewTreeLifecycleOwner(this)
                decor.setViewTreeViewModelStoreOwner(this)
                decor.setViewTreeSavedStateRegistryOwner(this)
            }
        } catch (_: Throwable) {
        }

        themeHolder = OverlayThemeHolder(this)
        val bgColor = themeHolder.currentTheme.get(CardTheme.Colors.OVERLAY_BG.ordinal)
        getWindow().setBackgroundDrawable((bgColor and 0x00ffffff).toDrawable())

        rootView = View.inflate(
            ContextThemeWrapper(this, R.style.AppTheme),
            R.layout.overlay_layout,
            this.container
        )

        rootView.setViewTreeLifecycleOwner(this)
        rootView.setViewTreeViewModelStoreOwner(this)
        rootView.setViewTreeSavedStateRegistryOwner(this)

        val mainContainer = rootView.findViewById<ViewGroup>(R.id.overlay_root)
        AbstractFloatingView.container = mainContainer
        AbstractFloatingView.closeAllOpenViews(context)

        initInsets()
        initRecyclerView()
        initHeader()
        refreshNotifications()

        articleCollectorJob = syncScope.launch {
            viewModel.articleListState.collect { state ->
                withContext(Dispatchers.Main) {
                    if (!bookmarkVisible) {
                        adapter.replace(state.articles)
                    }
                    rootView.findViewById<SwipeRefreshLayout>(R.id.swipe_to_refresh).isRefreshing =
                        state.isSyncing
                }
            }
        }
        syncScope.launch {
            prefs.overlayTheme.get().collect {
                withContext(Dispatchers.Main) {
                    applyNewTheme(it)
                }
            }
        }
        NeoApp.bridge.setCallback(this)

        val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        val closePerm = "android.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(closeSystemDialogsReceiver, filter, closePerm, null, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(closeSystemDialogsReceiver, filter, closePerm, null)
        }
    }

    override fun closePanelIfNeeded(flags: Int) {
        if (AbstractFloatingView.isAnyOpen()) {
            AbstractFloatingView.closeAllOpenViews(context)
        }
        super.closePanelIfNeeded(flags)
    }

    override fun onBackPressed() {
        if (AbstractFloatingView.isAnyOpen()) {
            AbstractFloatingView.closeAllOpenViews(context)
            return
        } else {
            super.onBackPressed()
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onResume() {
        super.onResume()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        if (pendingCloseOnResume) {
            pendingCloseOnResume = false
            closePanelIfNeeded(1)
        }
        com.saulhdev.feeder.plugins.HubPluginRegistry.getInstance(context).refreshCards()
    }

    override fun onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onPause()
    }

    override fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    private fun updateTheme(force: String? = null) {
        setTheme(force)
        updateStubUi()
        adapter.setTheme(themeHolder.currentTheme)
    }

    private fun setTheme(force: String?) {
        themeHolder.setTheme(
            when (force ?: prefs.overlayTheme.getValue()) {
                "auto_system_black" -> CardTheme.getThemeBySystem(context, true)
                "auto_system"       -> CardTheme.getThemeBySystem(context, false)
                "dark"              -> CardTheme.defaultDarkThemeColors
                "black"             -> CardTheme.defaultBlackThemeColors
                else                -> CardTheme.defaultLightThemeColors
            }
        )
        setCustomTheme()
    }

    private fun updateStubUi() {
        val theme = if (themeHolder.currentTheme.get(CardTheme.Colors.OVERLAY_BG.ordinal)
                .isDark()
        ) CardTheme.defaultDarkThemeColors else CardTheme.defaultLightThemeColors
        rootView.findViewById<MaterialButton>(R.id.header_settings).iconTint =
            ColorStateList.valueOf(
                theme.get(
                    CardTheme.Colors.TEXT_COLOR_PRIMARY.ordinal
                )
            )

        rootView.findViewById<MaterialButton>(R.id.header_filter).iconTint =
            ColorStateList.valueOf(
                theme.get(
                    CardTheme.Colors.TEXT_COLOR_PRIMARY.ordinal
                )
            )

        rootView.findViewById<MaterialButton>(R.id.header_bookmark).iconTint =
            ColorStateList.valueOf(
                theme.get(
                    CardTheme.Colors.TEXT_COLOR_PRIMARY.ordinal
                )
            )

        rootView.findViewById<TextView>(R.id.header_title)
            .setTextColor(theme.get(CardTheme.Colors.TEXT_COLOR_PRIMARY.ordinal))
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun getNavigationBarHeight(): Int {
        val resourceId =
            context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun applyInsets(statusBarTop: Int, navBarBottom: Int, left: Int = 0, right: Int = 0) {
        val top = maxOf(statusBarTop, getStatusBarHeight())
        val bottom = maxOf(navBarBottom, getNavigationBarHeight())
        val density = context.resources.displayMetrics.density

        rootView.findViewById<View>(R.id.app_bar)?.updatePadding(top = top)

        rootView.findViewById<RecyclerView>(R.id.recycler)?.updatePadding(
            left = left,
            right = right,
            bottom = bottom
        )

        rootView.findViewById<FloatingActionButton>(R.id.button_return_to_top)?.let { fab ->
            fab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = (64 * density).toInt() + bottom
                rightMargin = (24 * density).toInt() + right
            }
        }

    }

    private fun initInsets() {
        applyInsets(getStatusBarHeight(), getNavigationBarHeight())

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            applyInsets(insets.top, insets.bottom, insets.left, insets.right)
            windowInsets
        }

        rootView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                ViewCompat.requestApplyInsets(v)
            }

            override fun onViewDetachedFromWindow(v: View) {}
        })
    }

    private fun initRecyclerView() {
        val recyclerView = rootView.findViewById<RecyclerView>(R.id.recycler)
        val buttonReturnToTop =
            rootView.findViewById<FloatingActionButton>(R.id.button_return_to_top).apply {
                visibility = View.GONE
                setOnClickListener {
                    visibility = View.GONE
                    recyclerView.smoothScrollToPosition(0)

                }
            }

        rootView.findViewById<SwipeRefreshLayout>(R.id.swipe_to_refresh).setOnRefreshListener {
            rootView.findViewById<RecyclerView>(R.id.recycler).recycledViewPool.clear()
            adapter.clearDismissed()
            refreshNotifications()
        }

        adapter = FeedAdapter { articleId ->
            syncScope.launch {
                articleRepo.deleteArticles(listOf(articleId))
            }
        }
        recyclerView.apply {
            layoutManager = LinearLayoutManagerWrapper(context, LinearLayoutManager.VERTICAL, false)
            adapter = this@OverlayView.adapter
        }

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                if (viewHolder is FeedAdapter.FeedViewHolder) {
                    return ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                }
                return 0
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    adapter.dismissStoryAt(position)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if ((recyclerView.layoutManager as LinearLayoutManager)
                        .findFirstCompletelyVisibleItemPosition() < 5
                ) {
                    buttonReturnToTop.visibility = View.GONE
                } else if ((recyclerView.layoutManager as LinearLayoutManager)
                        .findFirstCompletelyVisibleItemPosition() > 5
                ) {
                    buttonReturnToTop.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun updateToggleColor(button: MaterialButton, isChecked: Boolean) {
        val context = button.context
        val darkTheme = themeHolder.currentTheme.get(CardTheme.Colors.OVERLAY_BG.ordinal).isDark()
        val a14 = Android.sdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        val a12 = Android.sdk(Build.VERSION_CODES.S)
        val backgroundTint = when {
            !isChecked       -> Color.TRANSPARENT

            a14 && darkTheme -> ContextCompat.getColor(
                context,
                android.R.color.system_on_primary_container_dark
            )

            a14              -> ContextCompat.getColor(
                context,
                android.R.color.system_primary_container_light
            )

            a12              -> ContextCompat.getColor(
                context,
                android.R.color.system_accent1_400
            )

            else             -> ContextCompat.getColor(
                context,
                R.color.md_theme_primary
            )
        }
        button.backgroundTintList = ColorStateList.valueOf(backgroundTint)

    }

    private fun initHeader() {
        val toggleButton = rootView.findViewById<MaterialButton>(R.id.header_bookmark)

        updateToggleColor(toggleButton, bookmarkVisible)
        toggleButton.setOnClickListener {
            bookmarkCollectorJob?.cancel()
            bookmarkVisible = !bookmarkVisible
            toggleButton.isChecked = bookmarkVisible
            updateToggleColor(toggleButton, bookmarkVisible)

            if (bookmarkVisible) {
                bookmarkCollectorJob = mainScope.launch {
                    viewModel.bookmarksState.collect {
                        adapter.replace(it.bookmarkedArticles)
                    }
                }
            } else {
                bookmarkCollectorJob = mainScope.launch {
                    val currentState = viewModel.articleListState.value
                    adapter.replace(currentState.articles)
                }
            }
        }

        rootView.findViewById<MaterialButton>(R.id.header_filter).apply {
            setOnClickListener {
                if (AbstractFloatingView.isAnyOpen()) {
                    AbstractFloatingView.closeAllOpenViews(context)
                    return@setOnClickListener
                } else {
                    FilterBottomSheet.show(context, true)
                }
            }
        }


        rootView.findViewById<MaterialButton>(R.id.header_settings).apply {
            setOnClickListener {
                openMenu(it)
            }
        }
    }

    private fun openMenu(view: View) {
        val popup = DialogMenu(view)
        popup.show(createMenuList()) {
            popup.dismiss()
            when (it.id) {
                "config"  -> {
                    mainScope.launch {
                        view.context.safeStartActivity(
                            MainActivity.navigateIntent(
                                view.context,
                                "${Routes.MAIN}/1",
                            )
                        )
                    }
                }

                "reload"  -> {
                    rootView.findViewById<RecyclerView>(R.id.recycler).recycledViewPool.clear()
                    refreshNotifications()
                }

                "restart" -> {
                    val application: NeoApp by inject(NeoApp::class.java)
                    application.restart(false)
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            context.unregisterReceiver(closeSystemDialogsReceiver)
        } catch (_: Exception) {
        }
        syncScope.cancel()
        mainScope.cancel()
        bookmarkCollectorJob?.cancel()
        articleCollectorJob?.cancel()
        AbstractFloatingView.container = null
        try {
            savedStateRegistryController.performSave(Bundle())
        } catch (_: Exception) {
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        _viewModelStore.clear()
        super.onDestroy()
        NeoApp.bridge.setCallback(null)
    }

    override fun onScroll(f: Float) {
        super.onScroll(f)

        val bgColor = themeHolder.currentTheme.get(CardTheme.Colors.OVERLAY_BG.ordinal)
        val alpha = if (f <= 0f) 0f else prefs.overlayTransparency.getValue()
        val color = (alpha * 255.0f).toInt() shl 24 or (bgColor and 0x00ffffff)
        getWindow().setBackgroundDrawable(color.toDrawable())
    }

    override fun onClientMessage(action: String) {
        if (prefs.debugging.getValue()) {
            Log.d("OverlayView", "New message by OverlayBridge: $action")
        }
        if (action == "openContentView") {
            pendingCloseOnResume = true
        }
    }

    override fun applyNewTheme(value: String) {
        updateTheme(value)
    }

    override fun applyNewTransparency(value: Float) {
        themeHolder.prefs.overlayTransparency.setValue(value)
    }

    override fun applyCompactCard(value: Boolean) {
        adapter = FeedAdapter { articleId ->
            syncScope.launch {
                articleRepo.deleteArticles(listOf(articleId))
            }
        }
        adapter.setTheme(themeHolder.currentTheme)
        rootView.findViewById<RecyclerView>(R.id.recycler).adapter = adapter
        refreshNotifications()
    }

    private fun refreshNotifications() {
        syncScope.launch {
            com.saulhdev.feeder.plugins.HubPluginRegistry.getInstance(context).refreshCards()
            articles.syncAllFeeds()
        }
    }

    private fun createMenuList(): List<MenuItem> {
        return listOf(
            MenuItem(R.drawable.ic_arrow_clockwise, R.string.action_reload, 0, "reload"),
            MenuItem(R.drawable.ic_gear, R.string.title_settings, 2, "config"),
            MenuItem(R.drawable.ic_power, R.string.action_restart, 2, "restart")
        )
    }
}
