package pl.bkacala.threecitycommuter.mocks

import android.Manifest
import dev.shreyaspatil.permissionFlow.MultiplePermissionState
import dev.shreyaspatil.permissionFlow.PermissionFlow
import dev.shreyaspatil.permissionFlow.PermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object MockPermissionFlows {
    val mockGrantedPermissionFlow =
        object : PermissionFlow {
            override fun getMultiplePermissionState(vararg permissions: String): StateFlow<MultiplePermissionState> =
                throw IllegalStateException("It's not used and therefore shouldn't be called")

            override fun getPermissionState(permission: String): StateFlow<PermissionState> =
                MutableStateFlow(PermissionState(Manifest.permission.ACCESS_FINE_LOCATION, true, true))

            override fun notifyPermissionsChanged(vararg permissions: String) {}

            override fun startListening() {}

            override fun stopListening() {}
        }

    val mockDeniedPermissionFlow =
        object : PermissionFlow {
            override fun getMultiplePermissionState(vararg permissions: String): StateFlow<MultiplePermissionState> =
                throw IllegalStateException("It's not used and therefore shouldn't be called")

            override fun getPermissionState(permission: String): StateFlow<PermissionState> =
                MutableStateFlow(PermissionState(Manifest.permission.ACCESS_FINE_LOCATION, false, false))

            override fun notifyPermissionsChanged(vararg permissions: String) {}

            override fun startListening() {}

            override fun stopListening() {}
        }
}
