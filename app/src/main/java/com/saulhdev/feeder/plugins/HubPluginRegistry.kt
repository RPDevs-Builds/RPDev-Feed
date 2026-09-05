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
import com.saulhdev.feeder.plugins.impl.WebScraperPlugin
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
import org.json.JSONArray
import org.json.JSONObject

class HubPluginRegistry(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("rpdev_hub_plugins", Context.MODE_PRIVATE)
    }

    private val allKnownBuiltInPlugins: List<HubPlugin> = listOf(
        WeatherPlugin(),
        SensorsPlugin(),
        CalendarPlugin(),
        GitHubPlugin(),
        WebScraperPlugin(),
        DynamicRestPlugin()
    )

    private val _cardsFlow = MutableStateFlow<List<HubCardData>>(emptyList())
    val cardsFlow: StateFlow<List<HubCardData>> = _cardsFlow.asStateFlow()

    private val _dismissedCardIds = MutableStateFlow<Set<String>>(emptySet())
    val dismissedCardIds: StateFlow<Set<String>> = _dismissedCardIds.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private fun loadCustomPlugins(): List<HubPlugin> {
        val customJsonStr = prefs.getString("custom_plugins_list", "[]") ?: "[]"
        val customList = mutableListOf<HubPlugin>()
        try {
            val array = JSONArray(customJsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.optString("name", "Custom REST Module")
                val desc = obj.optString("description", "Dynamic REST Endpoint")
                customList.add(
                    DynamicRestPlugin(
                        customId = id,
                        customName = name,
                        customDescription = desc
                    )
                )
            }
        } catch (_: Exception) {
        }
        return customList
    }

    fun getAllPlugins(): List<HubPlugin> {
        val moduleManager = HubModuleManager.getInstance(context)
        val installedBuiltIn = allKnownBuiltInPlugins.filter { moduleManager.isInstalled(it.id) }
        val custom = loadCustomPlugins().filter { moduleManager.isInstalled(it.id) }
        val catalogDynamic = moduleManager.catalogModules.value
            .filter { moduleManager.isInstalled(it.id) && allKnownBuiltInPlugins.none { b -> b.id == it.id } && custom.none { c -> c.id == it.id } }
            .map { mod ->
                DynamicRestPlugin(
                    customId = mod.id,
                    customName = mod.name,
                    customDescription = mod.summary
                )
            }
        val allMap = (installedBuiltIn + custom + catalogDynamic).associateBy { it.id }

        val order = getPluginOrder()
        val orderedList = mutableListOf<HubPlugin>()

        order.forEach { id ->
            allMap[id]?.let { orderedList.add(it) }
        }

        // Add any new or missing plugins not yet in saved order
        allMap.values.forEach { plugin ->
            if (orderedList.none { it.id == plugin.id }) {
                orderedList.add(plugin)
            }
        }

        return orderedList
    }

    fun notifyModulesChanged() {
        refreshCards()
    }

    fun getPluginOrder(): List<String> {
        val raw = prefs.getString("plugins_order", null)
        if (!raw.isNullOrBlank()) {
            try {
                val array = JSONArray(raw)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                return list
            } catch (_: Exception) {
            }
        }
        return allKnownBuiltInPlugins.map { it.id }
    }

    fun setPluginOrder(orderedIds: List<String>) {
        val array = JSONArray(orderedIds)
        prefs.edit().putString("plugins_order", array.toString()).apply()
        refreshCards()
    }

    fun movePluginUp(pluginId: String) {
        val currentOrder = getAllPlugins().map { it.id }.toMutableList()
        val index = currentOrder.indexOf(pluginId)
        if (index > 0) {
            val temp = currentOrder[index]
            currentOrder[index] = currentOrder[index - 1]
            currentOrder[index - 1] = temp
            setPluginOrder(currentOrder)
        }
    }

    fun movePluginDown(pluginId: String) {
        val currentOrder = getAllPlugins().map { it.id }.toMutableList()
        val index = currentOrder.indexOf(pluginId)
        if (index >= 0 && index < currentOrder.size - 1) {
            val temp = currentOrder[index]
            currentOrder[index] = currentOrder[index + 1]
            currentOrder[index + 1] = temp
            setPluginOrder(currentOrder)
        }
    }

    fun addCustomPlugin(name: String, endpointUrl: String, headers: String = ""): String {
        val id = "plugin_custom_rest_${System.currentTimeMillis()}"
        val customJsonStr = prefs.getString("custom_plugins_list", "[]") ?: "[]"
        try {
            val array = JSONArray(customJsonStr)
            val newObj = JSONObject().apply {
                put("id", id)
                put("name", name)
                put("description", "Custom endpoint: $endpointUrl")
            }
            array.put(newObj)
            prefs.edit().putString("custom_plugins_list", array.toString()).apply()

            // Save config
            val config = mapOf(
                "endpoint_url" to endpointUrl,
                "card_title" to name,
                "headers" to headers
            )
            savePluginConfig(id, config)
            HubModuleManager.getInstance(context).installModule(id)
            setPluginEnabled(id, true)

            val newOrder = getPluginOrder() + id
            setPluginOrder(newOrder)
        } catch (_: Exception) {
        }
        return id
    }

    fun deleteCustomPlugin(pluginId: String) {
        HubModuleManager.getInstance(context).uninstallModule(pluginId)
        val customJsonStr = prefs.getString("custom_plugins_list", "[]") ?: "[]"
        try {
            val array = JSONArray(customJsonStr)
            val newArray = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.getString("id") != pluginId) {
                    newArray.put(obj)
                }
            }
            prefs.edit().putString("custom_plugins_list", newArray.toString()).apply()
            val newOrder = getPluginOrder().filter { it != pluginId }
            setPluginOrder(newOrder)
        } catch (_: Exception) {
        }
    }

    fun uninstallPlugin(pluginId: String) {
        if (pluginId.startsWith("plugin_custom_rest_")) {
            deleteCustomPlugin(pluginId)
        } else {
            HubModuleManager.getInstance(context).uninstallModule(pluginId)
        }
    }

    fun isPluginEnabled(pluginId: String): Boolean {
        return prefs.getBoolean("plugin_enabled_$pluginId", pluginId != "plugin_dynamic_rest")
    }

    fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        prefs.edit().putBoolean("plugin_enabled_$pluginId", enabled).apply()
        refreshCards()
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
        refreshCards()
    }

    fun dismissCard(pluginId: String) {
        _dismissedCardIds.value = _dismissedCardIds.value + pluginId
    }

    fun restoreDismissedCards() {
        if (_dismissedCardIds.value.isNotEmpty()) {
            _dismissedCardIds.value = emptySet()
        }
    }

    fun refreshCards(
        clearDismissed: Boolean = true,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ) {
        if (clearDismissed) {
            _dismissedCardIds.value = emptySet()
        }
        scope.launch {
            _isRefreshing.value = true
            val orderedPlugins = getAllPlugins().filter { isPluginEnabled(it.id) }

            val deferredResults = orderedPlugins.map { plugin ->
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
