package com.example.system

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Datos reales de Open-Meteo; nunca sustituye errores por un pronóstico inventado. */
object WeatherRepository {
    data class WeatherForecast(
        val city: String,
        val tempMax: Double,
        val tempMin: Double,
        val condition: String
    )

    sealed class Result {
        data class Success(val forecast: WeatherForecast) : Result()
        data class Error(val message: String) : Result()
    }

    // La función inyectable permite probar respuestas y fallos sin conexión de red.
    fun fetchWeather(cityName: String, getJson: (String) -> JSONObject = ::readJson): Result {
        val city = cityName.trim()
        if (city.isEmpty()) return Result.Error("Indica una ciudad, por ejemplo: clima en Caracas.")
        return try {
            val encoded = URLEncoder.encode(city, "UTF-8")
            val locations = getJson("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=es&format=json")
                .optJSONArray("results")
            if (locations == null || locations.length() == 0) {
                return Result.Error("No encontré la ciudad '$city'. Prueba con otro nombre.")
            }
            val location = locations.getJSONObject(0)
            val lat = location.getDouble("latitude")
            val lon = location.getDouble("longitude")
            val name = location.getString("name")
            val daily = getJson("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto")
                .getJSONObject("daily")
            val max = daily.getJSONArray("temperature_2m_max").getDouble(0)
            val min = daily.getJSONArray("temperature_2m_min").getDouble(0)
            val code = daily.getJSONArray("weather_code").getInt(0)
            if (!max.isFinite() || !min.isFinite()) error("Temperaturas inválidas")
            Result.Success(WeatherForecast(name, max, min, getWeatherDescription(code)))
        } catch (e: Exception) {
            Log.w("WeatherRepository", "No se pudo consultar el clima", e)
            Result.Error("No pude obtener el clima de '$city'. Comprueba tu conexión e inténtalo de nuevo.")
        }
    }

    private fun readJson(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            check(conn.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${conn.responseCode}" }
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun getWeatherDescription(code: Int): String = when (code) {
        0 -> "Cielo despejado"
        1, 2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Neblina o bruma"
        51, 53, 55, 56, 57 -> "Llovizna"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Lluvia"
        71, 73, 75, 77, 85, 86 -> "Nevada"
        95, 96, 99 -> "Tormenta eléctrica"
        else -> "Condición meteorológica desconocida"
    }
}
