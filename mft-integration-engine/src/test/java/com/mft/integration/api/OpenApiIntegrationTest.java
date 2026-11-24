package com.mft.integration.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "mft.scheduling.enabled=false"
})
class OpenApiIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("GET /v3/api-docs: returns valid OpenAPI 3.0 specification for all routes and endpoints")
    void testApiDocsSpec() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("Enterprise MFT Integration Engine API"))
                .andExpect(jsonPath("$.info.version").value("1.2.5"))
                .andExpect(jsonPath("$.paths['/api/routes']").exists())
                .andExpect(jsonPath("$.paths['/api/routes/{routeId}']").exists())
                .andExpect(jsonPath("$.paths['/api/routes/{routeId}/execute']").exists())
                .andExpect(jsonPath("$.paths['/api/connections']").exists())
                .andExpect(jsonPath("$.paths['/api/transfers']").exists());
    }

    @Test
    @DisplayName("GET /swagger-ui.html: redirects to Swagger UI index")
    void testSwaggerUiRedirect() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }
}
