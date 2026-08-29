package app.halal.web.account

import app.halal.application.account.AccountRepository
import app.halal.application.account.CreateAccount
import app.halal.application.account.PasswordHasher
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