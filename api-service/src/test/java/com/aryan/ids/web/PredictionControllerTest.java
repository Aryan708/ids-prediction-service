package com.aryan.ids.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.aryan.ids.client.MlServiceUnavailableException;
import com.aryan.ids.domain.Label;
import com.aryan.ids.dto.PredictionResponse;
import com.aryan.ids.service.InvalidFeaturesException;
import com.aryan.ids.service.PredictionNotFoundException;
import com.aryan.ids.service.PredictionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PredictionController.class)
class PredictionControllerTest {

    private static final String FLOW = """
            {"sourceIp":"10.0.0.7","features":{"Destination Port":22,"Flow Duration":5,"SYN Flag Count":1}}
            """;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PredictionService service;

    private static PredictionResponse attack(long id) {
        return new PredictionResponse(id, Label.ATTACK, 0.97, "rf-v1", "10.0.0.7", 22, false, 12,
                Instant.parse("2026-09-28T10:00:00Z"));
    }

    @Test
    void postReturns201WithLocation() throws Exception {
        when(service.predict(any())).thenReturn(attack(7));

        mvc.perform(post("/api/v1/predictions").contentType(MediaType.APPLICATION_JSON).content(FLOW))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/predictions/7"))
                .andExpect(jsonPath("$.label").value("ATTACK"))
                .andExpect(jsonPath("$.attackProbability").value(0.97));
    }

    @Test
    void emptyFeaturesFailBeanValidation() throws Exception {
        mvc.perform(post("/api/v1/predictions").contentType(MediaType.APPLICATION_JSON).content("{\"features\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("features")));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/api/v1/predictions").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed request"));
    }

    @Test
    void invalidFeaturesAre400WithDetails() throws Exception {
        when(service.predict(any())).thenThrow(new InvalidFeaturesException(List.of("missing 75 feature(s): ...")));

        mvc.perform(post("/api/v1/predictions").contentType(MediaType.APPLICATION_JSON).content(FLOW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("missing 75"))));
    }

    @Test
    void modelDownIs503() throws Exception {
        when(service.predict(any())).thenThrow(new MlServiceUnavailableException("down", null));

        mvc.perform(post("/api/v1/predictions").contentType(MediaType.APPLICATION_JSON).content(FLOW))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void unknownIdIs404() throws Exception {
        when(service.get(99L)).thenThrow(new PredictionNotFoundException(99L));

        mvc.perform(get("/api/v1/predictions/99")).andExpect(status().isNotFound());
    }

    @Test
    void badLabelFilterIs400() throws Exception {
        mvc.perform(get("/api/v1/predictions").param("label", "MAYBE")).andExpect(status().isBadRequest());
    }

    @Test
    void batchOver500FlowsIsRejected() throws Exception {
        String flows = String.join(",", java.util.Collections.nCopies(501, FLOW.strip()));

        mvc.perform(post("/api/v1/predictions/batch").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flows\":[" + flows + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("flows")));
    }
}
