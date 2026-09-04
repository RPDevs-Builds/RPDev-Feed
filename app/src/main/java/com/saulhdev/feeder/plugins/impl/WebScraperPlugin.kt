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
import org.jsoup.Jsoup
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class WebScraperPlugin(
    customId: String = "plugin_web_scraper",
    customName: String = "Webpage Monitor & Keyword Scraper",
    customDescription: String = "Scrape websites, track content changes, and monitor specific keywords or alerts."
) : HubPlugin {

    override val id: String = customId
    override val name: String = customName
    override val description: String = customDescription
    override val category: PluginCategory = PluginCategory.DEVELOPER
    override val iconName: String = "globe"
    override val defaultRefreshMinutes: Int = 15

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    override fun getConfigFields(): List<PluginConfigField> = listOf(
        PluginConfigField(
            key = "target_url",
            label = "Target Webpage URL",
            description = "URL to scrape (e.g. https://news.ycombinator.com or software release notes)",
            defaultValue = "",
            isRequired = true
        ),
        PluginConfigField(
            key = "keywords",
            label = "Monitored Keywords",
            description = "Comma-separated keywords to search for (e.g. Release, CVE, Android, Patch)",
            defaultValue = ""
        ),
        PluginConfigField(
            key = "css_selector",
            label = "CSS Selector (Optional)",
            description = "Target specific DOM elements (e.g. article, main, .title, or blank for full page)",
            defaultValue = ""
        ),
        PluginConfigField(
            key = "card_title",
            label = "Card Display Title",
            description = "Custom title for this web monitor card",
            defaultValue = "Web Scraper & Monitor"
        )
    )

    override suspend fun fetchCardData(
        context: Context,
        config: Map<String, String>
    ): Result<HubCardData> = withContext(Dispatchers.IO) {
        val targetUrl = config["target_url"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(IllegalArgumentException("Empty Target URL"))
        val rawKeywords = config["keywords"] ?: ""
        val cssSelector = config["css_selector"]?.trim() ?: ""
        val cardTitle = config["card_title"]?.trim()?.takeIf { it.isNotBlank() } ?: "Web Monitor"

        val keywordsList = rawKeywords.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 RPDev-Feed/1.1.0")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val html = response.body?.string() ?: ""
            val doc = Jsoup.parse(html, targetUrl)
            val pageTitle = doc.title().takeIf { it.isNotBlank() } ?: targetUrl

            // Extract target elements
            val targetElements = if (cssSelector.isNotEmpty()) {
                doc.select(cssSelector)
            } else {
                doc.body()?.children() ?: doc.children()
            }

            val fullText = if (cssSelector.isNotEmpty() && targetElements.isNotEmpty()) {
                targetElements.text()
            } else {
                doc.body()?.text() ?: doc.text()
            }

            // Calculate content hash for change detection
            val currentHash = md5(fullText.take(2048))
            val sharedPrefs = context.getSharedPreferences("rpdev_web_scraper_cache", Context.MODE_PRIVATE)
            val previousHash = sharedPrefs.getString("hash_$id", null)
            val hasChanged = previousHash != null && previousHash != currentHash

            sharedPrefs.edit()
                .putString("hash_$id", currentHash)
                .putLong("time_$id", System.currentTimeMillis())
                .apply()

            // Keyword analysis
            val foundKeywords = mutableMapOf<String, Int>()
            val matchingSnippets = mutableListOf<HubTimelineItem>()

            for (kw in keywordsList) {
                val matches = countOccurrences(fullText, kw)
                if (matches > 0) {
                    foundKeywords[kw] = matches
                }
            }

            // Extract relevant snippets / headlines
            val paragraphs = doc.select("p, h1, h2, h3, h4, li, tr, .title, .headline, a")
            var snippetCount = 0
            for (element in paragraphs) {
                if (snippetCount >= 4) break
                val text = element.text().trim()
                if (text.length in 20..180) {
                    val matchingKw = keywordsList.firstOrNull { text.contains(it, ignoreCase = true) }
                    if (matchingKw != null || (keywordsList.isEmpty() && snippetCount < 3)) {
                        matchingSnippets.add(
                            HubTimelineItem(
                                title = text,
                                subtitle = element.tagName().uppercase(),
                                tag = matchingKw ?: "Snippet",
                                clickUrl = element.attr("abs:href").takeIf { it.isNotBlank() } ?: targetUrl
                            )
                        )
                        snippetCount++
                    }
                }
            }

            val chips = mutableListOf<HubChip>()
            chips.add(HubChip(label = "HTTP ${response.code}"))

            if (foundKeywords.isNotEmpty()) {
                val topKws = foundKeywords.entries.take(3).joinToString(", ") { "${it.key}: ${it.value}" }
                chips.add(HubChip(label = "Keywords: $topKws"))
            } else if (keywordsList.isNotEmpty()) {
                chips.add(HubChip(label = "Keywords: 0 Matches"))
            }

            if (hasChanged) {
                chips.add(HubChip(label = "Status: Updated!"))
            }

            val badge = when {
                hasChanged -> "✨ Updated"
                foundKeywords.isNotEmpty() -> "🔍 ${foundKeywords.values.sum()} Found"
                else -> "✅ Checked"
            }

            val subtitleText = when {
                foundKeywords.isNotEmpty() -> "${foundKeywords.size} keyword(s) detected • ${pageTitle.take(45)}"
                hasChanged -> "Content modified • ${pageTitle.take(45)}"
                else -> "Monitoring ${pageTitle.take(45)}"
            }

            val card = HubCardData.Composite(
                pluginId = id,
                title = cardTitle,
                subtitle = subtitleText,
                badge = badge,
                chips = chips,
                timelineItems = matchingSnippets,
                actions = listOf(
                    HubAction(
                        label = "Open Webpage",
                        url = targetUrl
                    )
                )
            )

            Result.success(card)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun countOccurrences(text: String, keyword: String): Int {
        if (keyword.isBlank() || text.isBlank()) return 0
        var count = 0
        var index = 0
        while (true) {
            index = text.indexOf(keyword, index, ignoreCase = true)
            if (index == -1) break
            count++
            index += keyword.length
        }
        return count
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
