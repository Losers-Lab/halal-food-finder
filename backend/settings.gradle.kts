rootProject.name = "halal-food-finder-backend"

include(":domain")
include(":application")

// Hexagonal adapters, nested physically under adapters/ but registered with
// top-level project names so the module graph matches the ratified architecture:
//   :domain -> :application -> adapters -> :bootstrap
include(":persistence", ":storage-s3", ":verification-ai", ":verification-committee", ":web-api")
include(":geocoding")
include(":bootstrap")

project(":persistence").projectDir = file("adapters/persistence")
project(":geocoding").projectDir = file("adapters/geocoding")
project(":storage-s3").projectDir = file("adapters/storage-s3")
project(":verification-ai").projectDir = file("adapters/verification-ai")
project(":verification-committee").projectDir = file("adapters/verification-committee")
project(":web-api").projectDir = file("adapters/web-api")
