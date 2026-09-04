/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.plugins

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CatalogModule(
    val id: String,
    val name: String,
    val category: String,
    val summary: String,
    val description: String,
    val version: String,
    val author: String,
    val icon: String,
    val isDefaultInstalled: Boolean = false,
    val configFields: List<PluginConfigField> = emptyList()
)

class HubModuleManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hub_module_manager_prefs", Context.MODE_PRIVATE)

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val _installedModuleIds = MutableStateFlow<Set<String>>(loadInstalledModuleIds())
    val installedModuleIds: StateFlow<Set<String>> = _installedModuleIds.asStateFlow()

    private val _catalogModules = MutableStateFlow<List<CatalogModule>>(emptyList())
    val catalogModules: StateFlow<List<CatalogModule>> = _catalogModules.asStateFlow()

    private val _isLoadingCatalog = MutableStateFlow(false)
    val isLoadingCatalog: StateFlow<Boolean> = _isLoadingCatalog.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        // Load initial catalog from cache or bundled defaults
        val cached = prefs.getString(KEY_CACHED_CATALOG_JSON, null)
        if (!cached.isNullOrBlank()) {
            parseAndSetCatalog(cached)
        } else {
            setFallbackCatalog()
        }
    }

    private fun loadInstalledModuleIds(): Set<String> {
        val saved = prefs.getStringSet(KEY_INSTALLED_IDS, null)
        return if (saved != null) {
            saved
        } else {
            // Default on initial launch: Weather and Hardware Telemetry
            val defaults = setOf("plugin_weather", "plugin_sensors")
            prefs.edit().putStringSet(KEY_INSTALLED_IDS, defaults).apply()
            defaults
        }
    }

    fun isInstalled(moduleId: String): Boolean {
        return _installedModuleIds.value.contains(moduleId)
    }

    fun installModule(moduleId: String) {
        val current = _installedModuleIds.value.toMutableSet()
        current.add(moduleId)
        prefs.edit().putStringSet(KEY_INSTALLED_IDS, current).apply()
        _installedModuleIds.value = current
        HubPluginRegistry.getInstance(context).notifyModulesChanged()
    }

    fun uninstallModule(moduleId: String) {
        val current = _installedModuleIds.value.toMutableSet()
        current.remove(moduleId)
        prefs.edit().putStringSet(KEY_INSTALLED_IDS, current).apply()
        _installedModuleIds.value = current
        HubPluginRegistry.getInstance(context).notifyModulesChanged()
    }

    fun resetToDefaults() {
        val defaults = setOf("plugin_weather", "plugin_sensors")
        prefs.edit().putStringSet(KEY_INSTALLED_IDS, defaults).apply()
        _installedModuleIds.value = defaults
        HubPluginRegistry.getInstance(context).notifyModulesChanged()
    }

    suspend fun fetchCatalog(forceRefresh: Boolean = false): Result<List<CatalogModule>> = withContext(Dispatchers.IO) {
        _isLoadingCatalog.value = true
        try {
            val customDomainUrl = "https://repo.feed.iamrp.dev/catalog/modules.json"
            val githubRawUrl = "https://raw.githubusercontent.com/RPDevs-Builds/RPDev-Feed-Modules/main/catalog/modules.json"

            var responseBody: String? = null

            // Try Custom Subdomain first
            try {
                val request = Request.Builder()
                    .url(customDomainUrl)
                    .header("Cache-Control", if (forceRefresh) "no-cache" else "max-age=300")
                    .build()
                httpClient.newCall(request).execute().use { res ->
                    if (res.isSuccessful) {
                        responseBody = res.body?.string()
                    }
                }
            } catch (_: Exception) {
            }

            // Fallback to GitHub Raw if domain is DNS propagating or unreachable
            if (responseBody.isNullOrBlank()) {
                try {
                    val rawReq = Request.Builder()
                        .url(githubRawUrl)
                        .header("Cache-Control", if (forceRefresh) "no-cache" else "max-age=300")
                        .build()
                    httpClient.newCall(rawReq).execute().use { res ->
                        if (res.isSuccessful) {
                            responseBody = res.body?.string()
                        }
                    }
                } catch (_: Exception) {}
            }

            if (!responseBody.isNullOrBlank()) {
                prefs.edit().putString(KEY_CACHED_CATALOG_JSON, responseBody).apply()
                val parsed = parseAndSetCatalog(responseBody!!)
                _isLoadingCatalog.value = false
                Result.success(parsed)
            } else {
                _isLoadingCatalog.value = false
                Result.success(_catalogModules.value)
            }
        } catch (e: Exception) {
            _isLoadingCatalog.value = false
            Result.failure(e)
        }
    }

    private fun parseAndSetCatalog(jsonStr: String): List<CatalogModule> {
        return try {
            val root = JSONObject(jsonStr)
            val modulesArr = root.optJSONArray("modules") ?: JSONArray()
            val list = mutableListOf<CatalogModule>()
            for (i in 0 until modulesArr.length()) {
                val obj = modulesArr.getJSONObject(i)
                val fieldsArr = obj.optJSONArray("configFields") ?: JSONArray()
                val fields = mutableListOf<PluginConfigField>()
                for (j in 0 until fieldsArr.length()) {
                    val f = fieldsArr.getJSONObject(j)
                    fields.add(
                        PluginConfigField(
                            key = f.optString("key"),
                            label = f.optString("label"),
                            description = f.optString("description"),
                            defaultValue = f.optString("defaultValue")
                        )
                    )
                }

                list.add(
                    CatalogModule(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        category = obj.optString("category"),
                        summary = obj.optString("summary"),
                        description = obj.optString("description"),
                        version = obj.optString("version", "1.0.0"),
                        author = obj.optString("author", "RPDevs"),
                        icon = obj.optString("icon", "Puzzle"),
                        isDefaultInstalled = obj.optBoolean("isDefaultInstalled", false),
                        configFields = fields
                    )
                )
            }
            _catalogModules.value = list
            list
        } catch (e: Exception) {
            _catalogModules.value
        }
    }

    private fun setFallbackCatalog() {
        val fallback = listOf(
            CatalogModule(
                id = "plugin_weather",
                name = "Privacy Weather",
                category = "Weather & Environment",
                summary = "Zero-telemetry live weather, radar, and hourly forecast from Open-Meteo.",
                description = "Zero-telemetry live weather from Open-Meteo. Supports address/zip lookup and GPS.",
                version = "1.1.0",
                author = "RPDevs",
                icon = "SunDim",
                isDefaultInstalled = true
            ),
            CatalogModule(
                id = "plugin_sensors",
                name = "Hardware & Battery Telemetry",
                category = "System & Hardware",
                summary = "Real-time battery charging wattage, voltage, temperature, internal storage, and RAM.",
                description = "Real-time hardware telemetry and memory pressure.",
                version = "1.1.0",
                author = "RPDevs",
                icon = "BatteryCharging",
                isDefaultInstalled = true
            ),
            CatalogModule(
                id = "plugin_calendar",
                name = "Calendar Agenda",
                category = "Productivity & Calendar",
                summary = "On-device agenda engine querying your upcoming 24-hour schedule.",
                description = "Displays upcoming calendar events with zero telemetry.",
                version = "1.1.0",
                author = "RPDevs",
                icon = "Calendar",
                isDefaultInstalled = false
            ),
            CatalogModule(
                id = "plugin_github",
                name = "GitHub Pulse",
                category = "Developer & Code",
                summary = "Live GitHub PR review requests, workflow CI runs, assigned issues, and commit activity.",
                description = "Tracks CI runs and PR review requests.",
                version = "1.1.0",
                author = "RPDevs",
                icon = "GitBranch",
                isDefaultInstalled = false
            ),
            CatalogModule(
                id = "plugin_web_scraper",
                name = "Webpage Monitor & Keyword Scraper",
                category = "Developer & Code",
                summary = "Scrape websites, track content changes via MD5 hash diffs, and monitor specific keywords.",
                description = "HTML scraping engine with change alerts.",
                version = "1.1.0",
                author = "RPDevs",
                icon = "Globe",
                isDefaultInstalled = false
            ),
            CatalogModule(
                id = "plugin_dynamic_rest",
                name = "Custom REST / JSON Endpoint",
                category = "Custom REST / Webhooks",
                summary = "Poll arbitrary HTTP JSON APIs (Home Assistant, Uptime Kuma, Gotify, Docker) and render as cards.",
                description = "Poll arbitrary REST JSON APIs.",
                version = "1.1.0",
                author = "RPDevs",
                icon = "Cloud",
                isDefaultInstalled = false
            )
        )
        _catalogModules.value = fallback
    }

    companion object {
        private const val KEY_INSTALLED_IDS = "installed_module_ids"
        private const val KEY_CACHED_CATALOG_JSON = "cached_catalog_json"

        @Volatile
        private var instance: HubModuleManager? = null

        fun getInstance(context: Context): HubModuleManager {
            return instance ?: synchronized(this) {
                instance ?: HubModuleManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
