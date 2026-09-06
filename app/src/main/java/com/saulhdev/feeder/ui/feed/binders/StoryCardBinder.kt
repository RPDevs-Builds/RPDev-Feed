package com.saulhdev.feeder.ui.feed.binders

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.text.Html
import android.util.SparseIntArray
import android.view.View
import android.widget.Toast
import coil.load
import com.google.android.material.button.MaterialButton
import com.saulhdev.feeder.MainActivity
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.data.entity.MenuItem
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.databinding.FeedCardStoryLargeBinding
import com.saulhdev.feeder.ui.navigation.Routes
import com.saulhdev.feeder.ui.theme.CardTheme
import com.saulhdev.feeder.ui.views.DialogMenu
import com.saulhdev.feeder.utils.RelativeTimeHelper
import com.saulhdev.feeder.utils.extensions.isDark
import com.saulhdev.feeder.utils.extensions.launchView
import com.saulhdev.feeder.utils.extensions.safeShareIntent
import com.saulhdev.feeder.utils.extensions.safeStartActivity
import com.saulhdev.feeder.utils.openLinkInCustomTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get
import org.koin.java.KoinJavaComponent.inject

object StoryCardBinder : FeedBinder {
    override fun bind(theme: SparseIntArray?, item: FeedItem, view: View) {
        bind(theme, item, view, null)
    }

    fun bind(
        theme: SparseIntArray?,
        item: FeedItem,
        view: View,
        onDismissStory: ((String) -> Unit)? = null
    ) {
        val context = view.context
        val content = item.toStoryCardContent()
        val binding = FeedCardStoryLargeBinding.bind(view)
        val prefs = get<FeedPreferences>(FeedPreferences::class.java)
        val repository: ArticleRepository by inject(ArticleRepository::class.java)
        var bookmarked = item.bookmarked
        binding.storyTitle.text = content.title
        binding.storySource.text = content.source.title
        binding.storyDate.text =
            RelativeTimeHelper.getDateFormattedRelative(
                view.context,
                (item.timeMillis / 1000) - 1000
            )

        val displayMode = prefs.articleDisplayMode.getValue()
        if (content.text.isEmpty()) {
            binding.storySummary.visibility = View.GONE
        } else {
            binding.storySummary.visibility = View.VISIBLE
            binding.storySummary.text = Html.fromHtml(content.text, 0).toString()
        }

        if (displayMode == "text_only") {
            binding.storyPic.visibility = View.GONE
        } else if (
            content.backgroundUrl.isEmpty() ||
            content.backgroundUrl == "null" ||
            content.backgroundUrl.contains(".rss")
        ) {
            binding.storyPic.visibility = View.GONE
        } else {
            binding.storyPic.visibility = View.VISIBLE
            binding.storyPic.load(content.backgroundUrl) {
                crossfade(true)
                crossfade(300)
            }
        }

        updateSaveIcon(binding.saveButton, bookmarked)
        binding.saveButton.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                repository.bookmarkArticle(item.id, !bookmarked)
                bookmarked = !bookmarked
                updateSaveIcon(binding.saveButton, bookmarked)
            }
        }

        binding.shareButton.setOnClickListener {
            context.safeShareIntent(content.link, content.title)
        }

        val preferredBrowser = item.feed.preferredPackage.takeIf { it.isNotBlank() }

        binding.root.setOnClickListener {
            if (prefs.openInBrowser.getValue() || preferredBrowser != null) {
                view.context.launchView(content.link, preferredBrowser)
            } else {
                val scope = CoroutineScope(Dispatchers.Main)

                scope.launch {
                    if (prefs.offlineReader.getValue()) {
                        view.context.safeStartActivity(
                            MainActivity.navigateIntent(
                                view.context,
                                "${Routes.ARTICLE_VIEW}/${item.id}"
                            )
                        )
                    } else {
                        openLinkInCustomTab(
                            context,
                            content.link,
                            preferredBrowser
                        )
                    }
                }
            }
        }

        val showCardMenu: (View) -> Unit = { anchorView ->
            val popup = DialogMenu(anchorView)
            val menuList = listOf(
                MenuItem(R.drawable.ic_news, R.string.menu_read_offline, 0, "offline"),
                MenuItem(R.drawable.ic_notification, R.string.menu_open_in_browser, 0, "browser"),
                MenuItem(R.drawable.ic_dots_vertical, R.string.menu_open_with, 0, "open_with"),
                MenuItem(
                    if (bookmarked) R.drawable.ic_heart_fill else R.drawable.ic_heart,
                    if (bookmarked) R.string.bookmark_remove else R.string.bookmark,
                    1,
                    "bookmark"
                ),
                MenuItem(R.drawable.ic_bookmarks, R.string.menu_copy_link, 1, "copy"),
                MenuItem(R.drawable.ic_share, R.string.menu_share_link, 1, "share"),
                MenuItem(R.drawable.ic_trash, R.string.menu_dismiss_story, 2, "dismiss_story"),
                MenuItem(R.drawable.ic_gear, R.string.menu_feed_settings, 2, "feed_settings")
            )

            popup.show(menuList) { selected ->
                popup.dismiss()
                when (selected.id) {
                    "offline" -> {
                        context.safeStartActivity(
                            MainActivity.navigateIntent(
                                context,
                                "${Routes.ARTICLE_VIEW}/${item.id}"
                            )
                        )
                    }
                    "browser" -> {
                        context.launchView(content.link, preferredBrowser)
                    }
                    "open_with" -> {
                        val chooser = Intent.createChooser(
                            Intent(Intent.ACTION_VIEW, Uri.parse(content.link)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                            context.getString(R.string.menu_open_with)
                        )
                        context.safeStartActivity(chooser)
                    }
                    "bookmark" -> {
                        CoroutineScope(Dispatchers.Main).launch {
                            repository.bookmarkArticle(item.id, !bookmarked)
                            bookmarked = !bookmarked
                            updateSaveIcon(binding.saveButton, bookmarked)
                        }
                    }
                    "copy" -> {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("Article Link", content.link)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, R.string.link_copied_toast, Toast.LENGTH_SHORT).show()
                    }
                    "share" -> {
                        context.safeShareIntent(content.link, content.title)
                    }
                    "dismiss_story" -> {
                        CoroutineScope(Dispatchers.IO).launch {
                            repository.deleteArticles(listOf(item.id))
                        }
                        onDismissStory?.invoke(item.id)
                    }
                    "feed_settings" -> {
                        context.safeStartActivity(
                            MainActivity.navigateIntent(
                                context,
                                "${Routes.MAIN}/2"
                            )
                        )
                    }
                }
            }
        }

        binding.root.setOnLongClickListener { anchorView ->
            showCardMenu(anchorView)
            true
        }

        binding.moreButton.setOnClickListener { anchorView ->
            showCardMenu(anchorView)
        }

        theme ?: return
        binding.cardStory.setCardBackgroundColor(ColorStateList.valueOf(theme.get(CardTheme.Colors.CARD_BG.ordinal)))
        val themeCard = if (theme.get(CardTheme.Colors.CARD_BG.ordinal).isDark())
            CardTheme.defaultDarkThemeColors
        else
            CardTheme.defaultLightThemeColors
        binding.storyTitle.setTextColor(themeCard.get(CardTheme.Colors.TEXT_COLOR_PRIMARY.ordinal))
        binding.storySource.setTextColor(themeCard.get(CardTheme.Colors.TEXT_COLOR_SECONDARY.ordinal))
        binding.storyDate.setTextColor(themeCard.get(CardTheme.Colors.TEXT_COLOR_SECONDARY.ordinal))
        binding.storySummary.setTextColor(themeCard.get(CardTheme.Colors.TEXT_COLOR_SECONDARY.ordinal))
        binding.shareButton.iconTint =
            ColorStateList.valueOf(themeCard.get(CardTheme.Colors.TEXT_COLOR_PRIMARY.ordinal))
        binding.saveButton.iconTint =
            ColorStateList.valueOf(themeCard.get(CardTheme.Colors.TEXT_COLOR_PRIMARY.ordinal))
        binding.moreButton.iconTint =
            ColorStateList.valueOf(themeCard.get(CardTheme.Colors.TEXT_COLOR_PRIMARY.ordinal))
    }

    private fun updateSaveIcon(button: MaterialButton, bookmarked: Boolean) {
        if (bookmarked) {
            button.setIconResource(R.drawable.ic_heart_fill)
        } else {
            button.setIconResource(R.drawable.ic_heart)
        }
    }
}