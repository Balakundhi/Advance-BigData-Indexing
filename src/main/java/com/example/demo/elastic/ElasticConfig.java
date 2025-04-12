package com.example.demo.elastic;

import java.util.Base64;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
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

        RestClient rc = RestClient.builder(new HttpHost("localhost", 9200, "http"))
            .setDefaultHeaders(new Header[]{
                new BasicHeader("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes()))
            })
            .build();

        return new ElasticsearchClient(
            new RestClientTransport(rc, new JacksonJsonpMapper()));
    }
}
