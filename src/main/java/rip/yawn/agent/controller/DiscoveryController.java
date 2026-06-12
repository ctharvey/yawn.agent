package rip.yawn.agent.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
// Discovery endpoints for agent clients.
public class DiscoveryController {

    private static final Map<String, Object> SERVICE_INFO = Map.of(
            "service", "yawn.agent",
            "description", "Agent-facing resolver for Pokemon card identities. Start with /agent/card/resolve to convert messy queries into canonical card IDs.",
            "version", "1.0",
            "endpoints", List.of(
                    Map.of(
                            "path", "/api/agent/card/resolve",
                            "method", "GET",
                            "description", "Resolve a messy Pokemon card query into a canonical cardId.",
                            "params", List.of(
                                    Map.of("name", "q", "type", "string", "required", true,
                                            "description", "Free-text card query, e.g. 'charizard 151 sir'")
                            ),
                            "free", true
                    )
            ),
            "llmsTxt", "/llms.txt"
    );

    private static final Map<String, Object> TOOLS_RESPONSE = Map.of(
            "tools", List.of(
                    Map.of(
                            "name", "card_resolve",
                            "endpoint", "/api/agent/card/resolve",
                            "method", "GET",
                            "description", "Resolve a messy Pokemon card query into a canonical cardId with confidence scoring and ambiguity signal.",
                            "params", List.of(
                                    Map.of("name", "q", "type", "string", "required", true)
                            ),
                            "free", true,
                            "recommendedFirst", true
                    )
            )
    );

    @GetMapping("/agent")
    public ResponseEntity<Map<String, Object>> getAgent() {
        return ResponseEntity.ok(SERVICE_INFO);
    }

    @GetMapping("/agent/tools")
    public ResponseEntity<Map<String, Object>> getTools() {
        return ResponseEntity.ok(TOOLS_RESPONSE);
    }

    @GetMapping(value = "/llms.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getLlmsTxt() {
        String content = """
                # yawn.agent — Pokemon Card Resolver API

                This service resolves messy Pokemon card queries into canonical Yawn card identities.

                ## Recommended workflow

                1. Call GET /api/agent/card/resolve?q={query} to convert free-text into a canonical cardId.
                2. Use the returned cardId as the key for all subsequent lookups.

                ## Resolver endpoint

                GET /api/agent/card/resolve?q={query}

                - q: Free-text Pokemon card description. Examples: "charizard 151 sir", "moonbreon", "base set zard shadowless"
                - Returns: matches[], ambiguity (none/low/medium/high), confidence scores, and suggestedNext call.
                - Free and rate-limit friendly. Safe to call repeatedly.
                - Never returns prices. Never triggers upstream fetches.

                ## Ambiguity signal

                - none: single confident match (confidence >= 0.90)
                - low: single match with moderate confidence
                - medium: 2-3 plausible matches
                - high: many matches or no good match — caller should narrow the query

                ## Tips for agents

                - Always start with the resolver before any paid/premium endpoint.
                - If ambiguity is "high", ask the user for more detail before proceeding.
                - Use the "why" field in each match to explain the result to a human.
                """;
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(content);
    }
}
