/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.manager.rest

import android.util.Log
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.utils.HtmlToPlainTextConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
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
            return try {
                org.koin.java.KoinJavaComponent.get(OkHttpClient::class.java)
            } catch (_: Exception) {
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
            }
        }

        private fun isValidHttpUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            return try {
                val uri = URI(url)
                val scheme = uri.scheme?.lowercase()
                (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun fetchCards(config: RestFeedConfig, feedId: Long): Result<List<Pair<Article, String>>> = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder()
                .url(config.url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) Mobile RPDev-Feed/1.2")

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
        val converter = HtmlToPlainTextConverter()
        val title = obj.optString(config.titleKey, "Card Update")
        val rawBody = obj.optString(config.bodyKey, obj.optString("message", obj.optString("description", "")))
        val sanitizedBody = converter.convert(rawBody).trim()

        val rawLink = obj.optString(config.linkKey, "")
        val rawImage = obj.optString(config.imageKey, "")

        val cardUuid = UUID.randomUUID().toString()
        val link = if (isValidHttpUrl(rawLink)) rawLink else "https://local.feed.rest/$cardUuid"
        val image = if (isValidHttpUrl(rawImage)) rawImage else null

        val article = Article(
            uuid = cardUuid,
            guid = cardUuid,
            title = title,
            plainTitle = title,
            plainSnippet = sanitizedBody.take(200),
            description = sanitizedBody,
            imageUrl = image,
            link = link,
            author = config.sourceName,
            pubDate = System.currentTimeMillis(),
            feedId = feedId
        )
        return article to sanitizedBody
    }
}
