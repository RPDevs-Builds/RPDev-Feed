/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.manager.rest

import android.util.Log
import com.saulhdev.feeder.data.db.models.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class RestFeedConfig(
    val url: String,
    val sourceName: String = "Custom REST",
    val authHeader: String? = null,
    val titleKey: String = "title",
    val bodyKey: String = "body",
    val linkKey: String = "url",
    val imageKey: String = "image"
)

class CustomRestCardSource(private val okHttpClient: OkHttpClient = defaultClient()) {

    companion object {
        private const val TAG = "CustomRestCardSource"

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }

    suspend fun fetchCards(config: RestFeedConfig, feedId: Long): Result<List<Pair<Article, String>>> = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder()
                .url(config.url)
                .header("User-Agent", "RPDev-Feed/1.0.0 (REST Ingestion)")

            config.authHeader?.let {
                if (it.isNotBlank()) reqBuilder.header("Authorization", it)
            }

            val response = okHttpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP error ${response.code}"))
            }

            val bodyString = response.body?.string() ?: return@withContext Result.success(emptyList())
            val results = mutableListOf<Pair<Article, String>>()

            if (bodyString.trim().startsWith("[")) {
                val array = JSONArray(bodyString)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val card = parseJsonObjectToArticle(obj, config, feedId)
                    results.add(card)
                }
            } else if (bodyString.trim().startsWith("{")) {
                val obj = JSONObject(bodyString)
                if (obj.has("items") && obj.get("items") is JSONArray) {
                    val array = obj.getJSONArray("items")
                    for (i in 0 until array.length()) {
                        val itemObj = array.optJSONObject(i) ?: continue
                        results.add(parseJsonObjectToArticle(itemObj, config, feedId))
                    }
                } else {
                    results.add(parseJsonObjectToArticle(obj, config, feedId))
                }
            }

            Result.success(results)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching REST cards from ${config.url}", e)
            Result.failure(e)
        }
    }

    private fun parseJsonObjectToArticle(obj: JSONObject, config: RestFeedConfig, feedId: Long): Pair<Article, String> {
        val title = obj.optString(config.titleKey, "Card Update")
        val body = obj.optString(config.bodyKey, obj.optString("message", obj.optString("description", "")))
        val link = obj.optString(config.linkKey, "https://local.feed.rest/${UUID.randomUUID()}")
        val image = obj.optString(config.imageKey, "")

        val cardUuid = UUID.randomUUID().toString()
        val article = Article(
            uuid = cardUuid,
            guid = cardUuid,
            title = title,
            plainTitle = title,
            plainSnippet = body.take(200),
            description = body,
            imageUrl = if (image.isBlank()) null else image,
            link = link,
            author = config.sourceName,
            pubDate = System.currentTimeMillis(),
            feedId = feedId
        )
        return article to body
    }
}
