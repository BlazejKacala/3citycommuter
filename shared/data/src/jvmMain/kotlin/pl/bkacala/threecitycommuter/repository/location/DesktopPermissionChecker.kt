package pl.bkacala.threecitycommuter.repository.location

class DesktopPermissionChecker : PermissionChecker {
    override fun isLocationPermissionGranted(): Boolean = true
}
