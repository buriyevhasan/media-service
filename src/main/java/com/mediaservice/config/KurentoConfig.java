package com.mediaservice.config;

import org.kurento.client.KurentoClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KurentoConfig {
    @Value("${kurento.ws.url:ws://localhost:8888/kurento}")
    private String kurentoUrl;

    @Bean
    public KurentoClient kurentoClient() throws InterruptedException {
        String url = "ws://localhost:8888/kurento";
        Exception last = null;

        for (int i = 1; i <= 10; i++) {
            try {
                return KurentoClient.create(url);
            } catch (Exception e) {
                last = e;
                Thread.sleep(3000);
            }
        }

        throw new IllegalStateException("Could not connect to Kurento after retries", last);
    }
}
