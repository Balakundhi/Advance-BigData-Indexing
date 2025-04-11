package com.example.demo.controller;

import com.example.demo.security.GoogleTokenVerifier;
import com.example.demo.service.PlanService;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.*;
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

        // JSON Schema (same as Demo One):
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

    // ==========================
    //     CREATE (POST)
    // ==========================
    @PostMapping
    public ResponseEntity<?> createPlan(
        @RequestBody String planJson,
        @RequestHeader(value="Authorization", required=false) String authHeader
    ) {
        // 1. Validate Bearer token
        try {
            GoogleTokenVerifier.verifyToken(authHeader);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                 .body("Invalid token: " + ex.getMessage());
        }

        // 2. Validate JSON with schema
        try {
            JSONObject jsonObj = new JSONObject(planJson);
            jsonSchema.validate(jsonObj);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                 .body("Invalid request body: " + e.getMessage());
        }

        // 3. Extract objectId
        JSONObject planObj = new JSONObject(planJson);
        String objectId = planObj.optString("objectId", null);
        if (objectId == null || objectId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                 .body("Missing or empty 'objectId'.");
        }

        // 4. Check duplicates
        if (planService.exists(objectId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                                 .body("Plan with objectId '" + objectId + "' already exists.");
        }

        // 5. Save
        planService.save(objectId, planJson);

        // 6. ETag
        String eTag = generateETag(planJson);

        return ResponseEntity.status(HttpStatus.CREATED)
                             .eTag(eTag)
                             .body("Plan created. objectId=" + objectId);
    }

    // ==========================
    //     READ (GET)
    // ==========================
    @GetMapping("/{objectId}")
    public ResponseEntity<?> getPlan(
       @PathVariable String objectId,
       @RequestHeader(value="If-None-Match", required=false) String ifNoneMatch,
       @RequestHeader(value="Authorization", required=false) String authHeader
    ) {
        // 1. Verify token
        try {
            GoogleTokenVerifier.verifyToken(authHeader);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                 .body("Invalid token: " + ex.getMessage());
        }

        // 2. Fetch from Redis
        String planJson = planService.get(objectId);
        if (planJson == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                 .body("Plan not found for objectId: " + objectId);
        }

        // 3. ETag check
        String eTag = generateETag(planJson);
        if (ifNoneMatch != null && ifNoneMatch.equals(eTag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }

        // 4. Return
        return ResponseEntity.ok().eTag(eTag).body(planJson);
    }

    // ==========================
    //    UPDATE (PATCH)
    // ==========================
    @PatchMapping("/{objectId}")
    public ResponseEntity<?> patchPlan(
        @PathVariable String objectId,
        @RequestBody String patchJson,
        @RequestHeader(value="If-Match", required=false) String ifMatch,
        @RequestHeader(value="Authorization", required=false) String authHeader
    ) {
        // 1. Token check
        try {
            GoogleTokenVerifier.verifyToken(authHeader);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                 .body("Invalid token: " + ex.getMessage());
        }

        // 2. Get existing data
        String existing = planService.get(objectId);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                 .body("No plan found for objectId: " + objectId);
        }

        // 3. If-Match
        if (ifMatch == null) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                                 .body("If-Match header is required for PATCH");
        }
        String currentEtag = generateETag(existing);
        if (!ifMatch.equals(currentEtag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                                 .body("ETag mismatch: resource changed");
        }

        // 4. Merge
        String merged;
        try {
            merged = deepMerge(existing, patchJson);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                 .body("Error merging JSON: " + e.getMessage());
        }

        // 5. Validate
        try {
            JSONObject mergedObj = new JSONObject(merged);
            jsonSchema.validate(mergedObj);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                 .body("Validation error after merge: " + e.getMessage());
        }

        // 6. Save
        planService.save(objectId, merged);

        // 7. new ETag
        String newEtag = generateETag(merged);
        return ResponseEntity.ok().eTag(newEtag).body("Plan updated successfully");
    }

    // ==========================
    //     DELETE
    // ==========================
    @DeleteMapping("/{objectId}")
    public ResponseEntity<?> deletePlan(
        @PathVariable String objectId,
        @RequestHeader(value="Authorization", required=false) String authHeader
    ) {
        // 1. Token check
        try {
            GoogleTokenVerifier.verifyToken(authHeader);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                 .body("Invalid token: " + ex.getMessage());
        }

        // 2. If resource exists
        if (!planService.exists(objectId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                 .body("No plan found for objectId: " + objectId);
        }
        planService.delete(objectId);
        return ResponseEntity.noContent().build(); // 204
    }

    // ==========================
    //  GET ALL KEYS (DEBUG)
    // ==========================
    @GetMapping("/keys")
    public ResponseEntity<?> listAllKeys(
        @RequestHeader(value="Authorization", required=false) String authHeader
    ) {
        try {
            GoogleTokenVerifier.verifyToken(authHeader);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                 .body("Invalid token: " + ex.getMessage());
        }

        return ResponseEntity.ok(planService.getAllKeys());
    }

    // =========================================
    // Helper: Generate ETag (MD5)
    // =========================================
    private String generateETag(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // fallback
            return String.valueOf(content.hashCode());
        }
    }

    // =========================================
    // Helper: Deep Merge
    // =========================================
    private String deepMerge(String originalJson, String patchJson) throws Exception {
        JSONObject original = new JSONObject(originalJson);
        JSONObject patch = new JSONObject(patchJson);

        JSONObject mergedObj = mergeObjects(original, patch);
        return mergedObj.toString();
    }

    private JSONObject mergeObjects(JSONObject source, JSONObject update) {
        for (String key : update.keySet()) {
            Object value = update.get(key);

            if (value instanceof JSONObject) {
                JSONObject subSrc = source.optJSONObject(key);
                if (subSrc == null) {
                    source.put(key, value);
                } else {
                    // recursively merge
                    source.put(key, mergeObjects(subSrc, (JSONObject)value));
                }
            }
            else if (value instanceof JSONArray) {
                // handle array logic
                if ("linkedPlanServices".equals(key)) {
                    JSONArray sourceArr = source.optJSONArray(key);
                    if (sourceArr == null) {
                        // not present, just put it
                        source.put(key, value);
                    } else {
                        // replicate your friend's approach:
                        JSONArray patchArr = (JSONArray)value;
                        for (int i = 0; i < patchArr.length(); i++) {
                            JSONObject newItem = patchArr.getJSONObject(i);
                            String newId = newItem.optString("objectId");
                            // check if it already exists
                            boolean exists = false;
                            for (int j = 0; j < sourceArr.length(); j++) {
                                JSONObject existingItem = sourceArr.getJSONObject(j);
                                String existingId = existingItem.optString("objectId");
                                if (existingId.equals(newId)) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                sourceArr.put(newItem);
                            }
                        }
                    }
                } else {
                    // Just overwrite the array entirely
                    source.put(key, value);
                }
            }
            else {
                // primitive
                source.put(key, value);
            }
        }
        return source;
    }
}
