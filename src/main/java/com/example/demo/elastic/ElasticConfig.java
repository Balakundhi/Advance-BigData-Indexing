package com.example.demo.elastic;

import java.util.Base64;

import javax.net.ssl.SSLContext;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;

@Configuration
public class ElasticConfig {

    @Bean
    public ElasticsearchClient esClient() {

        String credentials = "elastic:EF1ug5GXnaLAxF8xTI=+";

        /* 1️⃣  Build an “all‑trusting” SSLContext (ONLY for local demo) */
        SSLContext sslContext;
        try {
            sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, (chain, authType) -> true)  // trust everything
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create SSL context", e);
        }

        /* 2️⃣  Build the RestClient with that SSLContext + no hostname check */
        RestClient rc = RestClient.builder(new HttpHost("localhost", 9200, "https"))
            .setDefaultHeaders(new Header[]{
                new BasicHeader("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes()))
            })
            .setHttpClientConfigCallback(
                (HttpAsyncClientBuilder b) -> b
                    .setSSLContext(sslContext)
                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
            )
            .build();

        return new ElasticsearchClient(
            new RestClientTransport(rc, new JacksonJsonpMapper()));
    }
}
