package com.srm.creditengine.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * O frontend (Vite, porta 5173) e o backend (Spring Boot, porta 8080) sao
 * origens diferentes do ponto de vista do navegador (mesmo scheme e host,
 * portas diferentes ja' bastam) - sem CORS explicito, o navegador bloqueia
 * a chamada ANTES mesmo dela chegar ao controller, e o fetch() do
 * frontend falha com um erro de rede generico (nao um erro HTTP com corpo),
 * o que faz a UI cair no fallback "Falha ao simular" em vez de mostrar o
 * erro real da API.
 *
 * srm.cors.allowed-origins e' configuravel via variavel de ambiente
 * (CORS_ALLOWED_ORIGINS no docker-compose) para nao hardcodar localhost em
 * nenhum ambiente que nao seja o default de desenvolvimento local.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${srm.cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
