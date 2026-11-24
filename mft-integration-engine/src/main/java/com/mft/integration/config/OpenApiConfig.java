package com.mft.integration.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 configuration providing interactive Swagger UI at /swagger-ui.html.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mftOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Enterprise MFT Integration Engine API")
                        .description("High-throughput Managed File Transfer (MFT) REST API for managing SFTP connections, inspecting transfer routes, monitoring live transfers, querying non-repudiation audit logs, and tracking file lifecycles.")
                        .version("1.2.5")
                        .contact(new Contact()
                                .name("MFT Support Team")
                                .email("support@mft.corp"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local MFT Integration Engine")
                ));
    }
}
