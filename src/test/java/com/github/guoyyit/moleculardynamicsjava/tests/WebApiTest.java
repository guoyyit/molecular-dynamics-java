package com.github.guoyyit.moleculardynamicsjava.tests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "md.work-dir=target/md-test-work")
@AutoConfigureMockMvc
class WebApiTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void listsBuiltInExamples() throws Exception {
        mockMvc.perform(get("/api/jobs/examples"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examples[0]").value("fcc32-nve"));
    }

    @Test
    void submitsExamplePollsAndDownloadsCsv() throws Exception {
        String response = mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exampleName\":\"fcc32-nve\",\"jobId\":\"web-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("web-1"))
                .andReturn().getResponse().getContentAsString();

        String statusBody = "";
        for (int attempt = 0; attempt < 100; attempt++) {
            statusBody = mockMvc.perform(get("/api/jobs/web-1"))
                    .andReturn().getResponse().getContentAsString();
            if (statusBody.contains("\"status\":\"FINISHED\"")) {
                break;
            }
            Thread.sleep(50);
        }
        org.junit.jupiter.api.Assertions.assertTrue(statusBody.contains("\"status\":\"FINISHED\""),
                statusBody);

        String csv = mockMvc.perform(get("/api/jobs/web-1/timeseries"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(
                csv.startsWith("step,time,temperature,potentialEnergy,kineticEnergy,totalEnergy"));

        mockMvc.perform(get("/api/jobs/web-1/rdf")).andExpect(status().isOk());
        mockMvc.perform(get("/api/jobs/web-1/msd")).andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidConfigWithReadableError() throws Exception {
        String body = """
                {
                  "config": {
                    "boxLength": [10, 10, 10],
                    "species": [{"name":"A","mass":-1,"sigma":1,"epsilon":1}],
                    "particles": [
                      {"speciesIndex":0,"position":[1,1,1],"velocity":[0,0,0]},
                      {"speciesIndex":0,"position":[3,1,1],"velocity":[0,0,0]}
                    ],
                    "cutoff":2.5,"minSeparation":0.8,"timeStep":0.005,"duration":0.1,
                    "ensemble":"NVE","sampleInterval":10,"checkpointInterval":100
                  }
                }
                """;
        mockMvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("mass")));
    }

    @Test
    void unknownJobReturns400() throws Exception {
        mockMvc.perform(get("/api/jobs/does-not-exist"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelsLongRunningJobThroughApi() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exampleName\":\"fcc108-nvt\",\"jobId\":\"web-cancel\","
                                + "\"duration\":1000.0}"))
                .andExpect(status().isOk());
        Thread.sleep(100);
        mockMvc.perform(post("/api/jobs/web-cancel/cancel"))
                .andExpect(status().isOk());

        String body = "";
        for (int attempt = 0; attempt < 100; attempt++) {
            body = mockMvc.perform(get("/api/jobs/web-cancel"))
                    .andReturn().getResponse().getContentAsString();
            if (body.contains("\"status\":\"TERMINATED\"")) {
                break;
            }
            Thread.sleep(30);
        }
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"terminationReason\":\"CANCELLED\""),
                body);
    }
}
