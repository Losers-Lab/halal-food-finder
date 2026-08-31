package com.tahirslist.application.geo

/**
 * Thrown when a geocoding provider fails to answer (non-2xx status, timeout, or
 * an unparseable response). Distinct from "no match", which the port signals by
 * returning null — callers degrade to unavailable-provider behavior rather than
 * treating an outage as "this address doesn't exist".
 */
class GeocodingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
