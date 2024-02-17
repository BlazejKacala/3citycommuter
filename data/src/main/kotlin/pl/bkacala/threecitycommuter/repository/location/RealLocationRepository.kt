package pl.bkacala.threecitycommuter.repository.location

import android.annotation.SuppressLint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.OnTokenCanceledListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import pl.bkacala.threecitycommuter.model.location.UserLocation
import javax.inject.Inject

internal class RealLocationRepository @Inject constructor(
    private val fusedLocationProviderClient: FusedLocationProviderClient
) : LocationRepository {

    @SuppressLint("MissingPermission")
    override fun getLocation(): Flow<UserLocation> = callbackFlow {

        var isCancellationRequested = false

        val callback = object : CancellationToken() {
            override fun onCanceledRequested(p0: OnTokenCanceledListener): CancellationToken {
                return this
            }

            override fun isCancellationRequested(): Boolean {
                return isCancellationRequested
            }
        }

        fusedLocationProviderClient.getCurrentLocation(PRIORITY_HIGH_ACCURACY, callback)
            .addOnSuccessListener {
                it?.let {
                    trySend(
                        UserLocation(
                            latitude = it.latitude,
                            longitude = it.longitude,
                            isFixed = false
                        )
                    )
                }
            }

        awaitClose {
            isCancellationRequested = true
        }

    }.flowOn(Dispatchers.IO)

}