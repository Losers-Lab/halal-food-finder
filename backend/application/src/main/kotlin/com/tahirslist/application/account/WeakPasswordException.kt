package com.tahirslist.application.account

/** Rejected because the submitted password does not meet the minimum strength rule. */
class WeakPasswordException(message: String) : RuntimeException(message)