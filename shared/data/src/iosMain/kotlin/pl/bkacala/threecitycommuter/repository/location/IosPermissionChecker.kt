package pl.bkacala.threecitycommuter.repository.location

class IosPermissionChecker : PermissionChecker {
    // TODO: Implement with CLLocationManager.authorizationStatus
    override fun isLocationPermissionGranted(): Boolean = false
}
