package platform.CoreLocation

typealias CLAuthorizationStatus = Int
enum class CLAccuracyAuthorization { CLAccuracyAuthorizationReducedAccuracy, full }
const val kCLAuthorizationStatusAuthorizedAlways = 3
const val kCLAuthorizationStatusAuthorizedWhenInUse = 4
const val kCLAuthorizationStatusDenied = 2
const val kCLAuthorizationStatusNotDetermined = 0
const val kCLAuthorizationStatusRestricted = 1
val locationManagers = mutableListOf<CLLocationManager>()

class CLLocationManager {
    init { locationManagers.add(this) }
    companion object { fun authorizationStatus(): Int = 0 }
    var delegate: CLLocationManagerDelegateProtocol? = null
    val accuracyAuthorization = CLAccuracyAuthorization.full
    var authorizationStatus = 0
    var requests = 0
    fun requestWhenInUseAuthorization() { requests++ }
}

interface CLLocationManagerDelegateProtocol {
    fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {}
    fun locationManager(manager: CLLocationManager, didChangeAuthorizationStatus: CLAuthorizationStatus) {}
}
