package com.palavecino.backend.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The /api/health endpoint is public and answers both GET and HEAD, so external uptime monitors
 * have a cheap, DB-free way to check the app is alive (and keep the free-tier instance awake).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class HealthIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getHealthReturns200WithStatusUpWithoutAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/health"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("UP"));
    }

    @Test
    void headHealthReturns200WithoutAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.head("/api/health"))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }
}
