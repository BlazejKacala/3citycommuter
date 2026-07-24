package pl.bkacala.threecitycommuter.mocks

import pl.bkacala.threecitycommuter.repository.location.PermissionChecker

object MockPermissionChecker {
    val mockGrantedPermissionChecker = object : PermissionChecker {
        override fun isLocationPermissionGranted(): Boolean = true
    }

    val mockDeniedPermissionChecker = object : PermissionChecker {
        override fun isLocationPermissionGranted(): Boolean = false
    }
}
