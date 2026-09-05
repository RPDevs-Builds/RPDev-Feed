/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.plugins.impl

import android.content.Context
import com.saulhdev.feeder.plugins.ConfigFieldType
import com.saulhdev.feeder.plugins.HubPlugin
import com.saulhdev.feeder.plugins.PluginCategory
import com.saulhdev.feeder.plugins.PluginConfigField
import com.saulhdev.feeder.plugins.models.HubAction
import com.saulhdev.feeder.plugins.models.HubCardData
import com.saulhdev.feeder.plugins.models.HubChip
import com.saulhdev.feeder.plugins.models.HubTimelineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DynamicRestPlugin(
    customId: String = "plugin_dynamic_rest",
    customName: String = "Custom REST / JSON Endpoint",
    customDescription: String = "Poll arbitrary HTTP JSON APIs (Home Assistant, Uptime Kuma, Gotify, Docker) and render as cards."
) : HubPlugin {

    override val id: String = customId
    override val name: String = customName
    override val description: String = customDescription
    override val category: PluginCategory = PluginCategory.CUSTOM
    override val iconName: String = "globe"
    override val defaultRefreshMinutes: Int = 15

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    override fun getConfigFields(): List<PluginConfigField> = listOf(
        PluginConfigField(
            key = "endpoint_url",
            label = "Endpoint URL",
            description = "HTTPS or HTTP JSON endpoint to query",
            defaultValue = "",
            isRequired = true
        ),
        PluginConfigField(
            key = "headers",
            label = "Headers (JSON)",
            description = "Optional HTTP headers e.g. {\"Authorization\": \"Bearer token\"}",
            defaultValue = ""
        ),
        PluginConfigField(
            key = "card_title",
            label = "Card Title",
            description = "Display title for the generated card",
            defaultValue = "Custom REST Endpoint"
        )
    )

    private fun isValidHttpUrl(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun fetchCardData(
        context: Context,
        config: Map<String, String>
    ): Result<HubCardData> = withContext(Dispatchers.IO) {
        val url = config["endpoint_url"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(IllegalArgumentException("Empty Endpoint URL"))
        if (!isValidHttpUrl(url)) {
            return@withContext Result.failure(IllegalArgumentException("Only HTTP/HTTPS URLs are supported: $url"))
        }
        val title = config["card_title"]?.takeIf { it.isNotBlank() } ?: "Custom REST"
        val headersJson = config["headers"]

        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "RPDev-Feed/1.0.1")

            if (!headersJson.isNullOrBlank()) {
                try {
                    val headersObj = JSONObject(headersJson)
                    val keys = headersObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        requestBuilder.header(k, headersObj.getString(k))
                    }
                } catch (_: Exception) {
                }
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: "{}"
            val chips = mutableListOf<HubChip>()
            val items = mutableListOf<HubTimelineItem>()
            var subtitle = "HTTP 200 OK"
            var badge: String? = null

            if (body.trimStart().startsWith("{")) {
                val json = JSONObject(body)
                badge = json.optString("state").takeIf { it.isNotBlank() }
                    ?: json.optString("status").takeIf { it.isNotBlank() }

                subtitle = json.optString("message").takeIf { it.isNotBlank() }
                    ?: json.optString("description").takeIf { it.isNotBlank() }
                    ?: "JSON Object (${json.length()} keys)"

                val keys = json.keys()
                var count = 0
                while (keys.hasNext() && count < 6) {
                    val key = keys.next()
                    val value = json.opt(key)
                    if (value !is JSONObject && value !is JSONArray) {
                        chips.add(HubChip(label = "$key: $value"))
                        count++
                    }
                }
            } else if (body.trimStart().startsWith("[")) {
                val array = JSONArray(body)
                badge = "${array.length()} items"
                subtitle = "Array Response"
                for (i in 0 until minOf(array.length(), 5)) {
                    val item = array.opt(i)
                    if (item is JSONObject) {
                        items.add(
                            HubTimelineItem(
                                title = item.optString("title", item.optString("name", "Item #$i")),
                                subtitle = item.optString("status", item.optString("description", "")),
                                statusSuccess = true
                            )
                        )
                    }
                }
            }

            val card = HubCardData.Composite(
                pluginId = id,
                title = "🌐 $title",
                subtitle = subtitle,
                badge = badge,
                chips = chips,
                timelineItems = items,
                actions = listOf(
                    HubAction(label = "Open URL", url = url, isPrimary = true)
                )
            )

            Result.success(card)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
