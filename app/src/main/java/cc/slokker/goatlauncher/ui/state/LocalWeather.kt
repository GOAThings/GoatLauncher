package cc.slokker.goatlauncher.ui.state

import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.DeviceSettings
import cc.slokker.goatlauncher.data.WeatherNow
import cc.slokker.goatlauncher.data.WeatherSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * O tempo e o sítio da TV no ecrã inicial. O sítio escolhido nas definições ([DeviceSettings.location]) ganha ao do IP
 * (passa ao [WeatherSource.fixed]); sem ele, vale o do IP, guardado um dia. O [location] também decide o dia/noite
 * (ver NightClock).
 */
class LocalWeather(
    private val scope: CoroutineScope,
    private val source: WeatherSource,
    private val account: StateFlow<AccountSettings>,
    private val device: StateFlow<DeviceSettings>,
) {
    private val _weather = MutableStateFlow(source.cached())

    /** O tempo agora (null sem dados ou com a meteorologia desligada). */
    val weather: StateFlow<WeatherNow?> = _weather.asStateFlow()

    private val _location = MutableStateFlow(source.cachedLocation())

    /** Onde está a box (a escolhida, ou a do IP); null até se saber. */
    val location: StateFlow<Pair<Double, Double>?> = _location.asStateFlow()

    /** Chamado uma vez, ao arrancar o ecrã. */
    fun start() {
        scope.launch {
            // Com o dia/noite automático, a localização para o nascer e o pôr do sol (uma vez por dia, como no tempo).
            account.map { it.nightAuto }.distinctUntilChanged().collectLatest { on ->
                if (on) source.location()?.let { _location.value = it }
            }
        }
        scope.launch {
            // O sítio escolhido nas definições (ou volta ao do IP): muda o dia/noite e o tempo logo.
            device.map { it.location }.distinctUntilChanged().collectLatest { place ->
                source.fixed = place?.let { it.lat to it.lon }
                _location.value = source.location()
                if (account.value.showWeather) source.current()?.let { _weather.value = it }
            }
        }
        scope.launch {
            // Tempo: ao abrir e de 30 em 30 minutos, enquanto estiver ligado nas definições.
            account.map { it.showWeather }.distinctUntilChanged().collectLatest { on ->
                while (on) {
                    source.current()?.let { _weather.value = it }
                    delay(WEATHER_MS)
                }
            }
        }
    }

    private companion object {
        const val WEATHER_MS = 30 * 60_000L
    }
}
