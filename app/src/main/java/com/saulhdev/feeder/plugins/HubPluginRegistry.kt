/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.plugins

import android.content.Context
import android.content.SharedPreferences
import com.saulhdev.feeder.plugins.impl.CalendarPlugin
import com.saulhdev.feeder.plugins.impl.DynamicRestPlugin
import com.saulhdev.feeder.plugins.impl.GitHubPlugin
import com.saulhdev.feeder.plugins.impl.SensorsPlugin
import com.saulhdev.feeder.plugins.impl.WeatherPlugin
import com.saulhdev.feeder.plugins.models.HubCardData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class HubPluginRegistry(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("rpdev_hub_plugins", Context.MODE_PRIVATE)
    }

    private val allPlugins = listOf(
        WeatherPlugin(),
        GitHubPlugin(),
        CalendarPlugin(),
        SensorsPlugin(),
        DynamicRestPlugin()
    )

    private val _cardsFlow = MutableStateFlow<List<HubCardData>>(emptyList())
    val cardsFlow: StateFlow<List<HubCardData>> = _cardsFlow.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun getAllPlugins(): List<HubPlugin> = allPlugins

    fun isPluginEnabled(pluginId: String): Boolean {
        // Weather, Sensors, and Calendar enabled by default; GitHub and REST opt-in
        return prefs.getBoolean("plugin_enabled_$pluginId", pluginId != "plugin_dynamic_rest")
    }

    fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        prefs.edit().putBoolean("plugin_enabled_$pluginId", enabled).apply()
    }

    fun getPluginConfig(pluginId: String): Map<String, String> {
        val jsonStr = prefs.getString("plugin_config_$pluginId", "{}") ?: "{}"
        val map = mutableMapOf<String, String>()
        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = json.getString(k)
            }
        } catch (_: Exception) {
        }
        return map
    }

    fun savePluginConfig(pluginId: String, config: Map<String, String>) {
        val json = JSONObject(config)
        prefs.edit().putString("plugin_config_$pluginId", json.toString()).apply()
    }

    fun refreshCards(scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        scope.launch {
            _isRefreshing.value = true
            val enabledPlugins = allPlugins.filter { isPluginEnabled(it.id) }

            val deferredResults = enabledPlugins.map { plugin ->
                async(Dispatchers.IO) {
                    val config = getPluginConfig(plugin.id)
                    plugin.fetchCardData(context, config).getOrNull()
                }
            }

            val cards = deferredResults.awaitAll().filterNotNull()
            withContext(Dispatchers.Main) {
                _cardsFlow.value = cards
                _isRefreshing.value = false
            }
        }
    }

    companion object {
        @Volatile
        private var instance: HubPluginRegistry? = null

        fun getInstance(context: Context): HubPluginRegistry {
            return instance ?: synchronized(this) {
                instance ?: HubPluginRegistry(context.applicationContext).also { instance = it }
            }
        }
    }
}
