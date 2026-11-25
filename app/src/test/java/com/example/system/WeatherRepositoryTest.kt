package com.example.system

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeatherRepositoryTest {
    @Test fun `returns actual forecast for resolved city`() {
        val result = WeatherRepository.fetchWeather("Caracas") { url ->
            if (url.contains("geocoding")) JSONObject("""{"results":[{"name":"Caracas","latitude":10.5,"longitude":-66.9}]}""")
            else JSONObject("""{"daily":{"temperature_2m_max":[29.4],"temperature_2m_min":[20.1],"weather_code":[3]}}""")
        }
        val forecast = (result as WeatherRepository.Result.Success).forecast
        assertEquals("Caracas", forecast.city)
        assertEquals(29.4, forecast.tempMax, 0.001)
        assertEquals("Nublado", forecast.condition)
    }

    @Test fun `missing city does not call network`() {
        val result = WeatherRepository.fetchWeather("") { error("network must not be called") }
        assertTrue(result is WeatherRepository.Result.Error)
    }

    @Test fun `unknown city does not use Madrid coordinates`() {
        var calls = 0
        val result = WeatherRepository.fetchWeather("No existe") { calls++; JSONObject("{}") }
        assertTrue(result is WeatherRepository.Result.Error)
        assertEquals(1, calls)
    }

    @Test fun `network and malformed forecast fail without fabricated data`() {
        assertTrue(WeatherRepository.fetchWeather("Caracas") { throw java.io.IOException() } is WeatherRepository.Result.Error)
        val result = WeatherRepository.fetchWeather("Caracas") { url ->
            if (url.contains("geocoding")) JSONObject("""{"results":[{"name":"Caracas","latitude":10.5,"longitude":-66.9}]}""")
            else JSONObject("""{"daily":{}}""")
        }
        assertTrue(result is WeatherRepository.Result.Error)
    }
}
