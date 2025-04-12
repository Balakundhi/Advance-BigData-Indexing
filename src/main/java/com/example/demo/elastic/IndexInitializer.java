package com.example.demo.elastic;

import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

@Component
public class IndexInitializer implements ApplicationRunner {

    private final ElasticsearchClient es;

    public IndexInitializer(ElasticsearchClient es) {
        this.es = es;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {

        if (es.indices().exists(b -> b.index("plans")).value()) {
            return;                        // index already exists
        }

        es.indices().create(c -> c
            .index("plans")
            .mappings(m -> m
            		.properties("plan_join",
            			    p -> p.join(j -> j.relations(
            			        Map.ofEntries(
            			            Map.entry("plan",                List.of("planCostShares",
            			                                                     "linkedPlanServices")),
            			            Map.entry("linkedPlanServices",  List.of("linkedService",
            			                                                     "planserviceCostShares"))
            			        )
            			    ))
            			)


                .properties("objectId",   p -> p.keyword(k -> k))
                .properties("objectType", p -> p.keyword(k -> k))
                .properties("_org",       p -> p.keyword(k -> k))
                .properties("deductible", p -> p.integer(i -> i))
                .properties("copay",      p -> p.integer(i -> i))
                .properties("name",       p -> p.keyword(k -> k))
                .properties("planStatus", p -> p.keyword(k -> k))
                .properties("creationDate",
                        p -> p.date(d -> d.format("dd-MM-yyyy||yyyy-MM-dd")))

            )
        );
    }
}
