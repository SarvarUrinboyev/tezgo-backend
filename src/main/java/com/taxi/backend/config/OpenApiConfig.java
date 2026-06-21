package com.taxi.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI taxiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TezYol Taxi API")
                        .description("TezYol taxi ilovasi uchun backend API. " +
                                "Haydovchi, yo'lovchi va admin endpointlari.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("TezYol")
                                .url("https://tezyol.uz")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Token"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Token",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT access token (Authorization: Bearer {token})")));
    }
}
