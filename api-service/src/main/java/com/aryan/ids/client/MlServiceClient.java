package com.aryan.ids.client;

import java.util.List;
import java.util.Map;

import com.aryan.ids.service.InvalidFeaturesException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Thin HTTP client for the Python model service. */
@Component
public class MlServiceClient {

    private final RestClient restClient;

    public MlServiceClient(RestClient mlRestClient) {
        this.restClient = mlRestClient;
    }

    public MlModels.PredictResponse predict(List<Map<String, Double>> instances) {
        try {
            MlModels.PredictResponse response = restClient.post()
                    .uri("/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new MlModels.PredictRequest(instances))
                    .retrieve()
                    .body(MlModels.PredictResponse.class);
            if (response == null || response.predictions() == null
                    || response.predictions().size() != instances.size()) {
                throw new MlServiceUnavailableException("Model service returned an unexpected response", null);
            }
            return response;
        } catch (HttpClientErrorException e) {
            // 4xx from the model = bad input that slipped past our validation
            throw new InvalidFeaturesException(List.of("Model service rejected input: " + e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            // timeouts, connection refused, 5xx
            throw new MlServiceUnavailableException("Model service is unavailable", e);
        }
    }

    public MlModels.Metadata metadata() {
        try {
            MlModels.Metadata metadata = restClient.get()
                    .uri("/metadata")
                    .retrieve()
                    .body(MlModels.Metadata.class);
            if (metadata == null || metadata.featureNames() == null) {
                throw new MlServiceUnavailableException("Model service returned no metadata", null);
            }
            return metadata;
        } catch (RestClientException e) {
            throw new MlServiceUnavailableException("Model service is unavailable", e);
        }
    }
}
