package com.example.system

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object WeatherRepository {
    private const val TAG = "WeatherRepository"

    data class WeatherForecast(
        val city: String,
        val tempMax: Double,
        val tempMin: Double,
        val condition: String,
        val rainChance: String
    )

    fun fetchWeather(cityName: String): WeatherForecast {
        try {
            val encodedCity = URLEncoder.encode(cityName.ifBlank { "Madrid" }, "UTF-8")
            val geoUrl = URL("https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=es&format=json")
            val geoConn = geoUrl.openConnection() as HttpURLConnection
            geoConn.connectTimeout = 5000
            geoConn.readTimeout = 5000

            var lat = 40.4168
            var lon = -3.7038
            var resolvedName = cityName.ifBlank { "Madrid" }

            if (geoConn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(geoConn.inputStream))
                val jsonStr = reader.readText()
                reader.close()
                val root = JSONObject(jsonStr)
                val results = root.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val loc = results.getJSONObject(0)
                    lat = loc.optDouble("latitude", 40.4168)
                    lon = loc.optDouble("longitude", -3.7038)
                    resolvedName = loc.optString("name", resolvedName)
                }
            }
            geoConn.disconnect()

            val weatherUrl = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&daily=temperature_2m_max,temperature_2m_min,weathercode&timezone=auto")
            val weatherConn = weatherUrl.openConnection() as HttpURLConnection
            weatherConn.connectTimeout = 5000
            weatherConn.readTimeout = 5000

            if (weatherConn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(weatherConn.inputStream))
                val jsonStr = reader.readText()
                reader.close()

                val root = JSONObject(jsonStr)
                val daily = root.optJSONObject("daily")
                if (daily != null) {
                    val maxArr = daily.optJSONArray("temperature_2m_max")
                    val minArr = daily.optJSONArray("temperature_2m_min")
                    val codeArr = daily.optJSONArray("weathercode")

                    val max = maxArr?.optDouble(0, 22.0) ?: 22.0
                    val min = minArr?.optDouble(0, 14.0) ?: 14.0
                    val wcode = codeArr?.optInt(0, 0) ?: 0

                    val condition = getWeatherDescription(wcode)
                    weatherConn.disconnect()
                    return WeatherForecast(resolvedName, max, min, condition, "Baja")
                }
            }
            weatherConn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching weather data", e)
        }
        return WeatherForecast(cityName.ifBlank { "Madrid" }, 22.0, 14.0, "Parcialmente nublado", "N/A")
    }

    private fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "Cielo despejado y soleado"
            1, 2 -> "Parcialmente nublado"
            3 -> "Nublado"
            45, 48 -> "Neblina o bruma"
            51, 53, 55, 56, 57 -> "Llovizna ligera"
            61, 63, 65 -> "Lluvia moderada"
            71, 73, 75, 77 -> "Nevada"
            95, 96, 99 -> "Tormenta eléctrica"
            else -> "Condiciones estables"
        }
    }
}
