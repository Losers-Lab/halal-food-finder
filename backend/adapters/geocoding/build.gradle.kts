description = "Geocoding adapter: Photon (photon.komoot.io) implementing the GeocoderPort (default, $0, no key). Server-side only."

dependencies {
    implementation(project(":application"))
    implementation(project(":domain")) // direct: PhotonGeocoder maps responses to domain LatLng

    // JSON tree parsing for the Photon GeoJSON (FeatureCollection) response.
    implementation(libs.jackson.databind)
}
