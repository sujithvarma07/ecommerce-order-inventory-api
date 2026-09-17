package com.ecommerce.orderinventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BASIC_AUTH_SCHEME = "basicAuth";

    @Bean
    public OpenAPI orderInventoryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order & Inventory API")
                        .description("REST API for managing e-commerce orders, inventory, and product catalog.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BASIC_AUTH_SCHEME,
                                new SecurityScheme()
                                        .name(BASIC_AUTH_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("basic")));
    }
}
