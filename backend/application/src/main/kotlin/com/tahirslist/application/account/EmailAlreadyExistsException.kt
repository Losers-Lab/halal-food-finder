package com.tahirslist.application.account

import com.tahirslist.domain.account.Email

/** Rejected because an account with the same e-mail already exists. */
class EmailAlreadyExistsException(val email: Email) : RuntimeException("Account already exists for ${email.value}.")