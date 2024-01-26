package pl.bkacala.threecitycommuter.repository.location
import android.Manifest
import android.annotation.SuppressLint
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import dev.shreyaspatil.permissionFlow.PermissionFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import pl.bkacala.threecitycommuter.model.location.UserLocation
import javax.inject.Inject

class RealLocationRepository @Inject constructor(
    private val fusedLocationProviderClient: FusedLocationProviderClient,
    private val permissionFlow: PermissionFlow

) : LocationRepository {

    @SuppressLint("MissingPermission")
    override fun getLocation(): Flow<UserLocation> = callbackFlow {

        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let {
                    trySend(
                        UserLocation
                            (
                            latitude = it.latitude,
                            longitude = it.longitude,
                            isFixed = false
                        )
                    )
                }
            }
        }

        if(permissionFlow.getPermissionState(Manifest.permission.ACCESS_FINE_LOCATION).value.isGranted) {
            val locationRequest = LocationRequest.Builder(60 * 1000).build()
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
        } else {
            trySend(
                UserLocation.default()
            )
        }
        awaitClose {
            fusedLocationProviderClient.removeLocationUpdates(callback)
        }
    }
}