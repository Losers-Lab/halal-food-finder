package com.tahirslist.web.account

import com.tahirslist.application.account.AccountRepository
import com.tahirslist.application.account.AuthenticateAccount
import com.tahirslist.application.account.LogoutSession
import com.tahirslist.application.account.PasswordHasher
import com.tahirslist.application.account.RefreshSession
import com.tahirslist.application.account.RefreshTokenStore
import com.tahirslist.application.account.TokenIssuer
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