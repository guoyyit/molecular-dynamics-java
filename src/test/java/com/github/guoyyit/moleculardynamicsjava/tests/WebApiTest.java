package com.github.guoyyit.moleculardynamicsjava.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.moleculardynamicsjava.examples.ExampleSystems;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WebApiTest {
    private static final java.nio.file.Path WORK_DIR;

    static {
        try {
            WORK_DIR = Files.createTempDirectory("md-web-test");
        } catch (java.io.IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @DynamicPropertySource
    static void workDir(DynamicPropertyRegistry registry) {
        registry.add("md.work-dir", WORK_DIR::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listsExamples() throws Exception {
        mockMvc.perform(get("/api/examples"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examples['argon-fcc-nve']").exists())
                .andExpect(jsonPath("$.examples['argon-fcc-nve'].particles").value(108));
    }

    @Test
    void rejectsInvalidConfigWithReadableMessage() throws Exception {
        SimulationConfig config = ExampleSystems.all().get("argon-fcc-nve");
        config.setCutoff(99.0);
        mockMvc.perform(post("/api/simulations")
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(config)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void submitsQueriesAndCancelsJob() throws Exception {
        SimulationConfig config = ExampleSystems.all().get("argon-fcc-nve");
        config.setSteps(2_000_000);
        config.setCheckpointInterval(100);
        String response = mockMvc.perform(post("/api/simulations")
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(config)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String jobId = mapper.readTree(response).get("id").asText();

        mockMvc.perform(post("/api/simulations/" + jobId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelRequested").value(true));

        Thread.sleep(500);
        mockMvc.perform(get("/api/simulations/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void unknownJobReturns404() throws Exception {
        mockMvc.perform(get("/api/simulations/does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
