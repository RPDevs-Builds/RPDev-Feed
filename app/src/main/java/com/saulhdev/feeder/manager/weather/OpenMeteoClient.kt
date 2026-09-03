/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.manager.weather

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GeoLocationResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String = "",
    val admin1: String = "",
    val timezone: String = "",
    val countryCode: String = ""
) {
    val displayLabel: String
        get() = listOf(name, admin1, country).filter { it.isNotBlank() }.joinToString(", ")
}

data class WeatherInfo(
    val temperature: Double,
    val apparentTemperature: Double,
    val relativeHumidity: Int,
    val weatherCode: Int,
    val conditionDescription: String,
    val conditionIcon: String,
    val windSpeed: Double,
    val isDay: Boolean,
    val hourlyForecast: List<HourlyWeather> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class HourlyWeather(
    val time: String,
    val temperature: Double,
    val weatherCode: Int,
    val precipitationProb: Int
)

class OpenMeteoClient(private val okHttpClient: OkHttpClient = defaultClient()) {

    companion object {
        private const val TAG = "OpenMeteoClient"
        private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"
        private const val GEO_URL = "https://geocoding-api.open-meteo.com/v1/search"

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        }

        fun mapWeatherCode(code: Int, isDay: Boolean = true): Pair<String, String> {
            return when (code) {
                0 -> if (isDay) "Clear sky" to "☀️" else "Clear sky" to "🌙"
                1 -> if (isDay) "Mainly clear" to "🌤️" else "Mainly clear" to "🌤️"
                2 -> "Partly cloudy" to "⛅"
                3 -> "Overcast" to "☁️"
                45, 48 -> "Foggy" to "🌫️"
                51, 53, 55 -> "Drizzle" to "🌦️"
                56, 57 -> "Freezing Drizzle" to "🌧️"
                61, 63, 65 -> "Rain" to "🌧️"
                66, 67 -> "Freezing Rain" to "🌧️"
                71, 73, 75 -> "Snow fall" to "🌨️"
                77 -> "Snow grains" to "🌨️"
                80, 81, 82 -> "Rain showers" to "🌦️"
                85, 86 -> "Snow showers" to "🌨️"
                95 -> "Thunderstorm" to "⛈️"
                96, 99 -> "Thunderstorm with hail" to "⛈️"
                else -> "Fair" to "🌤️"
            }
        }
    }

    suspend fun fetchWeather(latitude: Double, longitude: Double, isFahrenheit: Boolean = false): Result<WeatherInfo> = withContext(Dispatchers.IO) {
        try {
            val tempUnit = if (isFahrenheit) "&temperature_unit=fahrenheit" else ""
            val windUnit = if (isFahrenheit) "&wind_speed_unit=mph" else "&wind_speed_unit=kmh"
            val url = "$BASE_URL?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m&hourly=temperature_2m,precipitation_probability,weather_code&forecast_days=1&timezone=auto$tempUnit$windUnit"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RPDev-Feed/1.0.0 (Android; Privacy-First)")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Weather API HTTP ${response.code}"))
            }

            val responseBody = response.body?.string() ?: return@withContext Result.failure(IOException("Empty response"))
            val json = JSONObject(responseBody)

            val current = json.getJSONObject("current")
            val temp = current.getDouble("temperature_2m")
            val apparentTemp = current.optDouble("apparent_temperature", temp)
            val humidity = current.optInt("relative_humidity_2m", 0)
            val isDay = current.optInt("is_day", 1) == 1
            val weatherCode = current.optInt("weather_code", 0)
            val windSpeed = current.optDouble("wind_speed_10m", 0.0)

            val (description, icon) = mapWeatherCode(weatherCode, isDay)

            val hourlyList = mutableListOf<HourlyWeather>()
            if (json.has("hourly")) {
                val hourly = json.getJSONObject("hourly")
                val times = hourly.optJSONArray("time")
                val temps = hourly.optJSONArray("temperature_2m")
                val codes = hourly.optJSONArray("weather_code")
                val precipProbs = hourly.optJSONArray("precipitation_probability")

                if (times != null && temps != null) {
                    val count = minOf(times.length(), 12)
                    for (i in 0 until count) {
                        val timeStr = times.optString(i, "")
                        val hourOnly = if (timeStr.contains("T")) timeStr.substringAfter("T") else timeStr
                        hourlyList.add(
                            HourlyWeather(
                                time = hourOnly,
                                temperature = temps.optDouble(i, 0.0),
                                weatherCode = codes?.optInt(i, 0) ?: 0,
                                precipitationProb = precipProbs?.optInt(i, 0) ?: 0
                            )
                        )
                    }
                }
            }

            Result.success(
                WeatherInfo(
                    temperature = temp,
                    apparentTemperature = apparentTemp,
                    relativeHumidity = humidity,
                    weatherCode = weatherCode,
                    conditionDescription = description,
                    conditionIcon = icon,
                    windSpeed = windSpeed,
                    isDay = isDay,
                    hourlyForecast = hourlyList
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch weather from Open-Meteo", e)
            Result.failure(e)
        }
    }

    suspend fun searchLocation(query: String): Result<List<GeoLocationResult>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext Result.success(emptyList())

        try {
            val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
            val url = "$GEO_URL?name=$encoded&count=8&language=en&format=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RPDev-Feed/1.0.0 (Android; Privacy-First)")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Geocoding HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.success(emptyList())
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return@withContext Result.success(emptyList())

            val list = mutableListOf<GeoLocationResult>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                list.add(
                    GeoLocationResult(
                        name = item.optString("name", ""),
                        latitude = item.optDouble("latitude", 0.0),
                        longitude = item.optDouble("longitude", 0.0),
                        country = item.optString("country", ""),
                        admin1 = item.optString("admin1", ""),
                        timezone = item.optString("timezone", ""),
                        countryCode = item.optString("country_code", "")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to geocode location", e)
            Result.failure(e)
        }
    }
}
