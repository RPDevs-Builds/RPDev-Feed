/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.manager.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.net.Uri
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import java.util.UUID

class FeedCardPushReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "FeedCardPushReceiver"
        const val ACTION_POST_CARD = "iamrp.dev.feed.ACTION_POST_CARD"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_URL = "url"
        const val EXTRA_IMAGE_URL = "image_url"
        const val EXTRA_TAG = "tag"

        private const val PUSH_FEED_URL = "https://local.feed.push"
        private const val PUSH_FEED_TITLE = "Push Alerts & Automation"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_POST_CARD) return

        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: ""
        val sourceName = intent.getStringExtra(EXTRA_SOURCE) ?: "Local Broadcast"
        val rawUrl = intent.getStringExtra(EXTRA_URL)
        val rawImageUrl = intent.getStringExtra(EXTRA_IMAGE_URL)
        val tag = intent.getStringExtra(EXTRA_TAG) ?: "Alerts"

        // Validate URL schemes — only allow http/https
        val url = rawUrl?.takeIf { uri ->
            val scheme = Uri.parse(uri).scheme?.lowercase()
            scheme == "http" || scheme == "https"
        } ?: "https://local.feed.push/${UUID.randomUUID()}"

        val imageUrl = rawImageUrl?.takeIf { uri ->
            val scheme = Uri.parse(uri).scheme?.lowercase()
            scheme == "http" || scheme == "https"
        }

        val sourcesRepo: SourcesRepository by inject(SourcesRepository::class.java)
        val articleRepo: ArticleRepository by inject(ArticleRepository::class.java)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val strictPushUrl = sloppyLinkToStrictURL(PUSH_FEED_URL)
                var pushFeed = sourcesRepo.getAllSources().firstOrNull { it.url.toString() == strictPushUrl.toString() }
                if (pushFeed == null) {
                    val newFeed = Feed(
                        title = PUSH_FEED_TITLE,
                        url = strictPushUrl,
                        tag = tag
                    )
                    sourcesRepo.insertSource(newFeed)
                    pushFeed = sourcesRepo.getAllSources().firstOrNull { it.url.toString() == strictPushUrl.toString() }
                }

                val feedId = pushFeed?.id ?: 1L
                val cardUuid = UUID.randomUUID().toString()
                val article = Article(
                    uuid = cardUuid,
                    guid = cardUuid,
                    title = title,
                    plainTitle = title,
                    plainSnippet = body.take(200),
                    description = body,
                    imageUrl = imageUrl,
                    link = url,
                    author = sourceName,
                    pubDate = System.currentTimeMillis(),
                    feedId = feedId
                )

                articleRepo.updateOrInsertArticle(listOf(article to body)) { _, _ -> }
                Log.d(TAG, "Successfully injected push card: '$title' from '$sourceName'")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert push card", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
