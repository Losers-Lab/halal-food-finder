package com.tahirslist.web.account

import com.tahirslist.application.account.AccountRepository
import com.tahirslist.application.account.CreateAccount
import com.tahirslist.application.account.PasswordHasher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the framework-free [CreateAccount] use case from its two ports
 * ([AccountRepository], [PasswordHasher]), both implemented by other modules.
 */
@Configuration
class CreateAccountConfig {

    @Bean
    fun createAccount(repository: AccountRepository, passwordHasher: PasswordHasher): CreateAccount =
        CreateAccount(repository = repository, hasher = passwordHasher)
}