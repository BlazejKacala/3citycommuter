package pl.bkacala.threecitycommuter

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.stops.BusStopType
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val busStationsRepository: BusStopsRepository
) : ViewModel() {

    fun loadBusStopsTypes() {

        val jsonString = applicationContext.assets.open("relations.json")
            .bufferedReader().use { it.readText() }
        val relations: List<BusStopType> = Json.decodeFromString(jsonString)

        busStationsRepository.storeBusStopsTypes(relations)
    }
}