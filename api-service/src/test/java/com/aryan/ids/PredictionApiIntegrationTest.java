package com.aryan.ids;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.aryan.ids.client.MlModels;
import com.aryan.ids.client.MlServiceClient;
import com.aryan.ids.client.MlServiceUnavailableException;
import com.aryan.ids.dto.FlowRequest;
import com.aryan.ids.repository.PredictionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Full stack (controller → service → cache → JPA/Flyway on H2) with only the Python model mocked. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PredictionApiIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private PredictionRepository repository;
    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private MlServiceClient mlClient;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        when(mlClient.metadata()).thenReturn(TestFlows.metadata());
    }

    private String flow(Map<String, Double> features) throws Exception {
        return json.writeValueAsString(new FlowRequest("10.0.0.7", features));
    }

    @Test
    void predictionIsStoredAndRepeatFlowHitsCache() throws Exception {
        when(mlClient.predict(anyList())).thenReturn(new MlModels.PredictResponse(TestFlows.MODEL_VERSION,
                List.of(new MlModels.Prediction("ATTACK", 0.97))));
        String body = flow(TestFlows.features(22, 5, 1));

        String first = mvc.perform(post("/api/v1/predictions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("ATTACK"))
                .andExpect(jsonPath("$.cacheHit").value(false))
                .andExpect(jsonPath("$.destinationPort").value(22))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/v1/predictions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cacheHit").value(true));

        verify(mlClient, times(1)).predict(anyList());
        assertThat(repository.count()).isEqualTo(2);

        long id = json.readTree(first).get("id").asLong();
        mvc.perform(get("/api/v1/predictions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceIp").value("10.0.0.7"))
                .andExpect(jsonPath("$.modelVersion").value(TestFlows.MODEL_VERSION));
    }

    @Test
    void batchListAndStats() throws Exception {
        when(mlClient.predict(anyList())).thenReturn(new MlModels.PredictResponse(TestFlows.MODEL_VERSION, List.of(
                new MlModels.Prediction("NORMAL", 0.02),
                new MlModels.Prediction("ATTACK", 0.91),
                new MlModels.Prediction("NORMAL", 0.10))));
        String batch = "{\"flows\":[" + String.join(",",
                flow(TestFlows.features(443, 900, 0)),
                flow(TestFlows.features(22, 3, 1)),
                flow(TestFlows.features(80, 1200, 0))) + "]}";

        mvc.perform(post("/api/v1/predictions/batch").contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.attacks").value(1));

        mvc.perform(get("/api/v1/predictions").param("label", "ATTACK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].destinationPort").value(22));

        mvc.perform(get("/api/v1/predictions/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPredictions").value(3))
                .andExpect(jsonPath("$.attacks").value(1))
                .andExpect(jsonPath("$.normal").value(2));
    }

    @Test
    void missingFeatureIs400AndNothingStored() throws Exception {
        mvc.perform(post("/api/v1/predictions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"features\":{\"Destination Port\":22}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").exists());

        assertThat(repository.count()).isZero();
    }

    @Test
    void modelDownIs503() throws Exception {
        when(mlClient.predict(anyList())).thenThrow(new MlServiceUnavailableException("down", null));

        mvc.perform(post("/api/v1/predictions").contentType(MediaType.APPLICATION_JSON)
                        .content(flow(TestFlows.features(8080, 10, 1))))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void modelEndpointExposesFeatureList() throws Exception {
        mvc.perform(get("/api/v1/model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureNames.length()").value(3));
    }
}
