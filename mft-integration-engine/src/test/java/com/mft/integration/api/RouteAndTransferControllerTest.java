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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("local")
@org.springframework.test.context.TestPropertySource(properties = "mft.scheduling.enabled=false")
class RouteAndTransferControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        for (java.nio.file.Path uploadDir : java.util.List.of(
                java.nio.file.Path.of("D:\\mft\\sftpgo\\instance-a\\data\\mft_source\\upload"),
                java.nio.file.Path.of("D:\\sftpgo\\instance-a\\data\\mft_source\\upload"),
                java.nio.file.Path.of("C:\\Users\\samee\\Downloads\\inbound\\upload"),
                java.nio.file.Path.of("C:\\Users\\samee\\Downloads\\mft-enterprise-engine-windows-x64\\sftpgo\\instance-a\\data\\mft_source\\upload")
        )) {
            try {
                java.nio.file.Files.createDirectories(uploadDir);
                java.nio.file.Path pos = uploadDir.resolve("positions.txt");
                if (!java.nio.file.Files.exists(pos) || java.nio.file.Files.size(pos) == 0) {
                    java.nio.file.Files.writeString(pos, "TRD000000001USD000000000001500.5020261120\n".repeat(30));
                }
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("GET /api/routes: returns all configured routes")
    void testGetAllRoutes() throws Exception {
        mockMvc.perform(get("/api/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @DisplayName("GET /api/routes/{id}: returns single route definition")
    void testGetSingleRoute() throws Exception {
        mockMvc.perform(get("/api/routes/R_RAW_COPY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.routeId").value("R_RAW_COPY"))
                .andExpect(jsonPath("$.data.processingMode").value("RAW_COPY"))
                .andExpect(jsonPath("$.data.sourceConnectionId").value("C_FILE_IN"))
                .andExpect(jsonPath("$.data.destinationConnectionId").value("C_FILE_OUT"));
    }

    @Test
    @DisplayName("POST /api/routes/{id}/execute: triggers route execution via REST API")
    void testExecuteRouteViaRest() throws Exception {
        String requestJson = """
                {
                    "patternOverride": "positions.txt"
                }
                """;

        mockMvc.perform(post("/api/routes/R_RAW_COPY/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.routeId").value("R_RAW_COPY"))
                .andExpect(jsonPath("$.data.runId").exists())
                .andExpect(jsonPath("$.data.filesDiscovered").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("GET /api/transfers: lists transfer runs")
    void testGetTransfers() throws Exception {
        mockMvc.perform(get("/api/transfers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/audit: lists structured audit events")
    void testGetAuditEvents() throws Exception {
        mockMvc.perform(get("/api/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("POST /api/routes/{id}/execute with Idempotency-Key returns cached response on retry")
    void testExecuteRouteWithIdempotencyKey() throws Exception {
        String key = "test-idemp-key-" + System.currentTimeMillis();
        String requestJson = """
                {
                    "patternOverride": "positions.txt"
                }
                """;

        mockMvc.perform(post("/api/routes/R_RAW_COPY/execute")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/routes/R_RAW_COPY/execute")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Idempotent response for key")));
    }
}
