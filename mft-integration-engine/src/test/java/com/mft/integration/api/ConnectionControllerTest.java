package com.mft.integration.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("local")
class ConnectionControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("GET /api/connections: returns configured connections without exposing passwords")
    void testGetAllConnections() throws Exception {
        mockMvc.perform(get("/api/connections")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data[0].connectionId").exists())
                .andExpect(jsonPath("$.data[0].host").exists())
                .andExpect(jsonPath("$.data[0].password").doesNotExist()) // Passwords must NEVER be serialized
                .andExpect(jsonPath("$.data[0].passwordSecretRef").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/connections/{id}: returns specific connection")
    void testGetSingleConnection() throws Exception {
        mockMvc.perform(get("/api/connections/C_FILE_IN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.connectionId").value("C_FILE_IN"))
                .andExpect(jsonPath("$.data.port").value(2022))
                .andExpect(jsonPath("$.data.username").value("mft_source"));
    }

    @Test
    @DisplayName("GET /api/connections/{id}/test: tests live SFTP connectivity to Server A (port 2022)")
    void testConnectionReachability() throws Exception {
        mockMvc.perform(get("/api/connections/C_FILE_IN/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.connectionId").value("C_FILE_IN"))
                .andExpect(jsonPath("$.data.reachable").value(true))
                .andExpect(jsonPath("$.data.responseTimeMs").isNumber());
    }

    @Test
    @DisplayName("GET /api/connections/UNKNOWN: returns 404 Not Found")
    void testUnknownConnectionReturns404() throws Exception {
        mockMvc.perform(get("/api/connections/UNKNOWN_ID"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CONNECTION_NOT_FOUND"))
                .andExpect(jsonPath("$.errorMessage", containsString("UNKNOWN_ID")));
    }
}
