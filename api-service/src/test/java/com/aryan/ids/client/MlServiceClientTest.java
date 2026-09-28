package com.aryan.ids.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import com.aryan.ids.TestFlows;
import com.aryan.ids.service.InvalidFeaturesException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MlServiceClientTest {

    private MockRestServiceServer server;
    private MlServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ml");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MlServiceClient(builder.build());
    }

    @Test
    void parsesSnakeCasePredictResponse() {
        server.expect(requestTo("http://ml/predict"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {"model_version":"rf-v1","predictions":[{"label":"ATTACK","attack_probability":0.97}]}
                        """, MediaType.APPLICATION_JSON));

        MlModels.PredictResponse r = client.predict(List.of(TestFlows.features(22, 5, 1)));

        assertThat(r.modelVersion()).isEqualTo("rf-v1");
        assertThat(r.predictions()).containsExactly(new MlModels.Prediction("ATTACK", 0.97));
        server.verify();
    }

    @Test
    void parsesMetadataAndIgnoresExtraFields() {
        server.expect(requestTo("http://ml/metadata"))
                .andRespond(withSuccess("""
                        {"model_version":"rf-v1","feature_names":["a","b"],"threshold":0.5,"metrics":{"accuracy":0.99}}
                        """, MediaType.APPLICATION_JSON));

        MlModels.Metadata m = client.metadata();

        assertThat(m.featureNames()).containsExactly("a", "b");
        assertThat(m.threshold()).isEqualTo(0.5);
    }

    @Test
    void serverErrorBecomesUnavailable() {
        server.expect(requestTo("http://ml/predict")).andRespond(withServerError());

        assertThatThrownBy(() -> client.predict(List.of(TestFlows.features(1, 2, 3))))
                .isInstanceOf(MlServiceUnavailableException.class);
    }

    @Test
    void clientErrorBecomesInvalidFeatures() {
        server.expect(requestTo("http://ml/predict"))
                .andRespond(withBadRequest().body("{\"detail\":\"bad\"}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.predict(List.of(TestFlows.features(1, 2, 3))))
                .isInstanceOf(InvalidFeaturesException.class);
    }

    @Test
    void wrongNumberOfPredictionsIsRejected() {
        server.expect(requestTo("http://ml/predict"))
                .andRespond(withSuccess("{\"model_version\":\"v\",\"predictions\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.predict(List.of(TestFlows.features(1, 2, 3))))
                .isInstanceOf(MlServiceUnavailableException.class);
    }
}
