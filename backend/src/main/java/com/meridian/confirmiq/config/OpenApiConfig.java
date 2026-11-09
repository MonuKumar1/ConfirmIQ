package com.meridian.confirmiq.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI confirmIqOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ConfirmIQ API")
                        .version("v1")
                        .description("Post-trade confirmation lifecycle, client documents and matching. "
                                + "Authenticate with POST /api/v1/auth/login and use the returned bearer token."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
