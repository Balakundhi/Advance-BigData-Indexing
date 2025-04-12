package com.example.demo.elastic;

import java.io.IOException;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

import java.util.Map;
import java.util.List;

import co.elastic.clients.elasticsearch._types.Conflicts;



@Service
public class PlanIndexer {

    private final ElasticsearchClient es;
    private final ObjectMapper mapper = new ObjectMapper();

    public PlanIndexer(ElasticsearchClient es){ this.es = es; }

    /* ----- public API used by consumer ----- */
    public void indexOrUpdate(JsonNode data) throws IOException {
        String planId = data.get("objectId").asText();
        
        System.out.println("📦 Indexing parent plan: " + planId);

        // 1. root doc
        es.index(i -> i
            .index("plans").id(planId)
            .document(Map.of(
                "objectId", planId,
                "objectType", data.get("objectType").asText(),
                "_org", data.get("_org").asText(),
                "planType",   data.get("planType").asText(),
                "creationDate", data.get("creationDate").asText(),
                "plan_join", Map.of("name","plan")
            )));

//        // 2. planCostShares
//        JsonNode pcs = data.get("planCostShares");
//        es.index(i -> i
//            .index("plans")
//            .id(pcs.get("objectId").asText())
//            .routing(planId)
//            .document(mapper.convertValue(pcs, Map.class))
//            .withJson(j -> j.add("plan_join", Map.of("name","planCostShares","parent",planId))));

        
     // 2. planCostShares  ─────────────────────────────────────────────
        JsonNode pcs = data.get("planCostShares");

        String pcsId = pcs.get("objectId").asText();
        System.out.println("💰 Indexing planCostShares: " + pcsId);
        
        Map<String,Object> pcsMap = mapper.convertValue(pcs, Map.class);
        pcsMap.put("plan_join", Map.of("name","planCostShares","parent", planId));

        es.index(i -> i.index("plans")
                       .id(pcs.get("objectId").asText())
                       .routing(planId)
                       .document(pcsMap));

        
        // 3. linkedPlanServices & their children
        for (JsonNode svc : data.withArray("linkedPlanServices")) {
            String lpsId = svc.get("objectId").asText();
            System.out.println("🧩 Indexing linkedPlanService: " + lpsId);

            es.index(i -> i.index("plans").id(lpsId).routing(planId)
                .document(Map.of(
                    "objectId", lpsId,
                    "objectType", svc.get("objectType").asText(),
                    "_org", svc.get("_org").asText(),
                    "plan_join", Map.of("name","linkedPlanServices","parent",planId)
                )));

//            // linkedService
//            JsonNode ls = svc.get("linkedService");
//            es.index(i -> i.index("plans").id(ls.get("objectId").asText()).routing(lpsId)
//                .document(mapper.convertValue(ls, Map.class))
//                .withJson(j -> j.add("plan_join", Map.of("name","linkedService","parent",lpsId))));
            
         // linkedService ─────────────────────────────────────────────────
            JsonNode ls = svc.get("linkedService");
            
            String lsId = ls.get("objectId").asText();
            System.out.println("   └─🔗 linkedService: " + lsId);
            
            Map<String,Object> lsMap = mapper.convertValue(ls, Map.class);
            lsMap.put("plan_join", Map.of("name","linkedService","parent", lpsId));

            es.index(i -> i.index("plans")
                           .id(ls.get("objectId").asText())
                           .routing(lpsId)
                           .document(lsMap));
            
            

//            // planserviceCostShares
//            JsonNode lscs = svc.get("planserviceCostShares");
//            es.index(i -> i.index("plans").id(lscs.get("objectId").asText()).routing(lpsId)
//                .document(mapper.convertValue(lscs, Map.class))
//                .withJson(j -> j.add("plan_join", Map.of("name","planserviceCostShares","parent",lpsId))));
            
         // planserviceCostShares ────────────────────────────────────────
            JsonNode lscs = svc.get("planserviceCostShares");
            
            String lscsId = lscs.get("objectId").asText();
            System.out.println("   └─💸 planserviceCostShares: " + lscsId);
            
            Map<String,Object> lscsMap = mapper.convertValue(lscs, Map.class);
            lscsMap.put("plan_join", Map.of("name","planserviceCostShares","parent", lpsId));

            es.index(i -> i.index("plans")
                           .id(lscs.get("objectId").asText())
                           .routing(lpsId)
                           .document(lscsMap));
            
            
        }
    }

    public void cascadeDelete(String planId) throws IOException {
        // Delete by query on parent + all children
        es.deleteByQuery(q -> q.index("plans")
            .query(t -> t
                .bool(b -> b
                    .should(s -> s.ids(i -> i.values(planId)))
                    .should(s -> s.term(tm -> tm.field("plan_join.parent").value(planId)))
                )
            )
            .conflicts(Conflicts.Proceed));
    }
}
