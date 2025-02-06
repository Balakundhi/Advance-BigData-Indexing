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

        // Inline our new "Fitness Plan" JSON Schema
        String schemaString = """
        {
          "$schema": "http://json-schema.org/draft-07/schema#",
          "type": "object",
          "properties": {
            "planId": {
              "type": "string"
            },
            "planName": {
              "type": "string"
            },
            "exercises": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "exerciseName": {
                    "type": "string"
                  },
                  "durationMins": {
                    "type": "number"
                  },
                  "caloriesBurned": {
                    "type": "number"
                  }
                },
                "required": ["exerciseName", "durationMins", "caloriesBurned"],
                "additionalProperties": false
              }
            },
            "createdBy": {
              "type": "string"
            },
            "creationDate": {
              "type": "string"
            }
          },
          "required": [
            "planId",
            "planName",
            "exercises",
            "createdBy",
            "creationDate"
          ],
          "additionalProperties": false
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
        // 1. Validate JSON against the new fitness schema
        try {
            JSONObject jsonObject = new JSONObject(planJson);
            jsonSchema.validate(jsonObject); // throws exception if invalid
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Invalid request body: " + e.getMessage());
        }

        // 2. Extract planId
        JSONObject jsonObject = new JSONObject(planJson);
        String planId = jsonObject.optString("planId", null);
        if (planId == null || planId.isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Missing or empty 'planId'.");
        }

        // 3. Check if plan with this planId already exists
        if (planService.exists(planId)) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("Plan with this planId already exists.");
        }

        // 4. Save in memory
        planService.save(planId, planJson);

        // 5. Return 201 Created
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body("Fitness plan successfully created. planId = " + planId);
    }

    // -------------------------
    //  READ (GET)
    // -------------------------
    @GetMapping("/{planId}")
    public ResponseEntity<?> getPlan(
            @PathVariable String planId,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch
    ) {
        // 1. Fetch
        String planJson = planService.get(planId);
        if (planJson == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Plan not found");
        }

        // 2. Generate ETag (MD5 hash)
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
    @DeleteMapping("/{planId}")
    public ResponseEntity<?> deletePlan(@PathVariable String planId) {
        if (!planService.exists(planId)) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Plan not found");
        }
        planService.delete(planId);
        return ResponseEntity.noContent().build(); // 204
    }

    // Helper for ETag generation
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
