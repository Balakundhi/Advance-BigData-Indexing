package com.example.demo.controller;

import com.example.demo.service.PlanService;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

        // JSON Schema matching the professor's structure
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
              "required": ["deductible", "_org", "copay", "objectId", "objectType"],
              "additionalProperties": false
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
                    "required": ["_org", "objectId", "objectType", "name"],
                    "additionalProperties": false
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
                    "required": ["deductible", "_org", "copay", "objectId", "objectType"],
                    "additionalProperties": false
                  },
                  "_org":       { "type": "string" },
                  "objectId":   { "type": "string" },
                  "objectType": { "type": "string" }
                },
                "required": ["linkedService", "planserviceCostShares", "_org", "objectId", "objectType"],
                "additionalProperties": false
              }
            },
            "_org":       { "type": "string" },
            "objectId":   { "type": "string" },
            "objectType": { "type": "string" },
            "planType":   { "type": "string" },
            "creationDate": { "type": "string" }
          },
          "required": [
            "planCostShares",
            "linkedPlanServices",
            "_org",
            "objectId",
            "objectType",
            "planType",
            "creationDate"
          ],
          "additionalProperties": false
        }
        """;

        // Compile the schema
        JSONObject rawSchema = new JSONObject(new JSONTokener(schemaString));
        this.jsonSchema = SchemaLoader.load(rawSchema);
    }

    /**
     * Creates a new plan using the professor's JSON structure.
     */
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

        // 2. Extract 'objectId' from the JSON
        JSONObject jsonObject = new JSONObject(planJson);
        String objectId = jsonObject.optString("objectId", null);
        if (objectId == null || objectId.isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Missing or empty 'objectId'.");
        }

        // 3. Check if a plan with this objectId already exists
        if (planService.exists(objectId)) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("A plan with objectId '" + objectId + "' already exists.");
        }

        // 4. Save into Redis (or in-memory, depending on PlanService)
        planService.save(objectId, planJson);

        // 5. Generate ETag and return 201
        String eTag = generateETag(planJson);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .eTag(eTag)
                .body("Plan successfully created. objectId = " + objectId);
    }

    /**
     * Retrieves a plan by objectId (path variable) and returns ETag for conditional GET.
     */
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
                    .body("Plan not found for objectId: " + objectId);
        }

        // 2. ETag generation
        String eTag = generateETag(planJson);

        // 3. Check If-None-Match
        if (ifNoneMatch != null && ifNoneMatch.equals(eTag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }

        // 4. Return plan + ETag
        return ResponseEntity
                .ok()
                .eTag(eTag)
                .body(planJson);
    }

    /**
     * Deletes a plan by objectId.
     */
    @DeleteMapping("/{objectId}")
    public ResponseEntity<?> deletePlan(@PathVariable String objectId) {
        if (!planService.exists(objectId)) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("No plan found for objectId: " + objectId);
        }
        planService.delete(objectId);
        return ResponseEntity.noContent().build(); // 204
    }

    /**
     * Returns all keys in Redis for debugging/demo.
     */
    @GetMapping("/keys")
    public ResponseEntity<?> listAllKeys() {
        return ResponseEntity.ok(planService.getAllKeys());
    }

    /**
     * Helper to generate ETag (MD5 hash).
     */
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
