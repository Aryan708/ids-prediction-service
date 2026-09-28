package com.aryan.ids.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI idsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Network Intrusion Detection API")
                .version("1.0.0")
                .description("Scores network flows (CICIDS2017 features) as NORMAL or ATTACK using a "
                        + "Random Forest model, and stores prediction history."));
    }
}
