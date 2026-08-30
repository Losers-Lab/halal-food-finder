package app.halal.web.account

import app.halal.application.account.AccountRepository
import app.halal.application.account.AuthenticateAccount
import app.halal.application.account.LogoutSession
import app.halal.application.account.PasswordHasher
import app.halal.application.account.RefreshSession
import app.halal.application.account.RefreshTokenStore
import app.halal.application.account.TokenIssuer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the framework-free Log In (sc-40) use cases from their ports, all
 * implemented by other modules: [AccountRepository], [PasswordHasher],
 * [TokenIssuer] and [RefreshTokenStore].
 */
@Configuration
class AuthConfig {

    @Bean
    fun authenticateAccount(
        repository: AccountRepository,
        passwordHasher: PasswordHasher,
        tokenIssuer: TokenIssuer,
        refreshTokenStore: RefreshTokenStore,
    ): AuthenticateAccount = AuthenticateAccount(
        repository = repository,
        hasher = passwordHasher,
        tokenIssuer = tokenIssuer,
        refreshTokenStore = refreshTokenStore,
    )

    @Bean
    fun refreshSession(
        tokenIssuer: TokenIssuer,
        refreshTokenStore: RefreshTokenStore,
        accountRepository: AccountRepository,
    ): RefreshSession = RefreshSession(
        tokenIssuer = tokenIssuer,
        refreshTokenStore = refreshTokenStore,
        accountRepository = accountRepository,
    )

    @Bean
    fun logoutSession(refreshTokenStore: RefreshTokenStore): LogoutSession =
        LogoutSession(refreshTokenStore)
}