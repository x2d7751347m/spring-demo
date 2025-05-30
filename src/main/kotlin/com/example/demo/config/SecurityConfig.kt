package com.example.demo.config

import org.springframework.boot.actuate.autoconfigure.security.reactive.EndpointRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec
import org.springframework.security.config.web.server.ServerHttpSecurity.OAuth2ResourceServerSpec
import org.springframework.security.web.server.SecurityWebFilterChain


/**
 * Created by jt, Spring Framework Guru.
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig {
    @Bean
    @Order(1)
    @Throws(Exception::class)
    fun actuatorSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain? {
        http.securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeExchange(Customizer { authorize: AuthorizeExchangeSpec? ->
                authorize!!.anyExchange().permitAll()
            })

        return http.build()
    }

    @Bean
    @Order(2)
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain? {
        http.authorizeExchange(Customizer { authorizeExchangeSpec: AuthorizeExchangeSpec? ->
            authorizeExchangeSpec!!.anyExchange().authenticated()
        })
            .oauth2ResourceServer(Customizer { oAuth2ResourceServerSpec: OAuth2ResourceServerSpec? ->
                oAuth2ResourceServerSpec!!.jwt(
                    Customizer.withDefaults<OAuth2ResourceServerSpec.JwtSpec?>()
                )
            })
            .csrf { csrfSpec -> csrfSpec.disable() }

        return http.build()
    }
}
