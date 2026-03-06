package pl.bkacala.threecitycommuter.repository.location

interface PermissionChecker {
    fun isLocationPermissionGranted(): Boolean
}
