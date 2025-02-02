package com.example.demo.controller;

import com.example.demo.service.PlanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@RestController
@RequestMapping("/v1/plan")
public class PlanController {

    private final PlanService planService;
    private final Schema jsonSchema;

    public PlanController(PlanService planService) {
        this.planService = planService;

        // Inline JSON Schema
        String schemaString = """
        {
          "$schema": "http://json-schema.org/draft-07/schema#",
          "type": "object",
          "properties": {
            "planCostShares": {
              "type": "object",
              "properties": {
                "deductible": { "type": "number" },
                "_org":       { "type": "string" },
                "copay":      { "type": "number" },
                "objectId":   { "type": "string" },
                "objectType": { "type": "string" }
              },
              "required": ["deductible", "_org", "copay", "objectId", "objectType"]
            },
            "linkedPlanServices": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "linkedService": {
                    "type": "object",
                    "properties": {
                      "_org":       { "type": "string" },
                      "objectId":   { "type": "string" },
                      "objectType": { "type": "string" },
                      "name":       { "type": "string" }
                    },
                    "required": ["_org", "objectId", "objectType", "name"]
                  },
                  "planserviceCostShares": {
                    "type": "object",
                    "properties": {
                      "deductible": { "type": "number" },
                      "_org":       { "type": "string" },
                      "copay":      { "type": "number" },
                      "objectId":   { "type": "string" },
                      "objectType": { "type": "string" }
                    },
                    "required": ["deductible", "_org", "copay", "objectId", "objectType"]
                  },
                  "_org":       { "type": "string" },
                  "objectId":   { "type": "string" },
                  "objectType": { "type": "string" }
                },
                "required": ["linkedService", "planserviceCostShares", "_org", "objectId", "objectType"]
              }
            },
            "_org":       { "type": "string" },
            "objectId":   { "type": "string" },
            "objectType": { "type": "string" },
            "planStatus": { "type": "string" },
            "creationDate": { "type": "string" }
          },
          "required": ["planCostShares", "linkedPlanServices", "_org", "objectId", "objectType", "planStatus", "creationDate"]
        }
        """;

        // Load and compile the schema
        JSONObject rawSchema = new JSONObject(new JSONTokener(schemaString));
        this.jsonSchema = SchemaLoader.load(rawSchema);
    }

    // -------------------------
    //  CREATE (POST)
    // -------------------------
    @PostMapping
    public ResponseEntity<?> createPlan(@RequestBody String planJson) {
        // 1. Validate JSON
        try {
            JSONObject jsonObject = new JSONObject(planJson);
            jsonSchema.validate(jsonObject);
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Invalid request body: " + e.getMessage());
        }

        // 2. Extract objectId
        JSONObject jsonObject = new JSONObject(planJson);
        String objectId = jsonObject.optString("objectId", null);
        if (objectId == null || objectId.isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Missing or empty 'objectId'.");
        }

        // 3. Check if plan with this objectId already exists
        if (planService.exists(objectId)) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("Plan with this objectId already exists.");
        }

        // 4. Save in memory
        planService.save(objectId, planJson);

        // 5. Return 201 Created
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body("Plan successfully created. objectId = " + objectId);
    }

    // -------------------------
    //  READ (GET)
    // -------------------------
    @GetMapping("/{objectId}")
    public ResponseEntity<?> getPlan(
            @PathVariable String objectId,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch
    ) {
        // 1. Fetch
        String planJson = planService.get(objectId);
        if (planJson == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Plan not found");
        }

        // 2. Generate ETag
        String eTag = generateETag(planJson);

        // 3. Compare with If-None-Match
        if (ifNoneMatch != null && ifNoneMatch.equals(eTag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }

        // 4. Return the plan with ETag
        return ResponseEntity
                .ok()
                .eTag(eTag)
                .body(planJson);
    }

    // -------------------------
    //  DELETE
    // -------------------------
    @DeleteMapping("/{objectId}")
    public ResponseEntity<?> deletePlan(@PathVariable String objectId) {
        if (!planService.exists(objectId)) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Plan not found");
        }
        planService.delete(objectId);
        return ResponseEntity.noContent().build();
    }

    // Helper: generate MD5 ETag
    private String generateETag(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(content.hashCode());
        }
    }
}
