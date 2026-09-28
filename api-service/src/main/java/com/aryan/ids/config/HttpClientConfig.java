package com.aryan.ids.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MlServiceProperties.class)
public class HttpClientConfig {

    /** RestClient pointed at the model service, with explicit timeouts so a slow model can't hang the API. */
    @Bean
    public RestClient mlRestClient(RestClient.Builder builder, MlServiceProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.connectTimeout().toMillis());
        factory.setReadTimeout((int) props.readTimeout().toMillis());
        return builder
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }
}
