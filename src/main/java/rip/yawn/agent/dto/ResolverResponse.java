package rip.yawn.agent.dto;

import java.util.List;

public record ResolverResponse(
    String query,
    String ambiguity,
    List<ResolverMatch> matches,
    String noMatchReason,
    Freshness freshness,
    SuggestedNext suggestedNext
) {

    public static ResolverResponse noMatch(String query, String reason, String searchEndpoint) {
        return new ResolverResponse(
            query, "high", List.of(),
            reason,
            null,
            new SuggestedNext(
                searchEndpoint,
                "Try manual search with fewer or different terms"
            )
        );
    }

    public static ResolverResponse sealedMisfire(String query) {
        return new ResolverResponse(
            query, "high", List.of(),
            "Query appears to describe a sealed product, not a single card.",
            null,
            null
        );
    }

    public static ResolverResponse matched(String query, String ambiguity,
                                           List<ResolverMatch> matches,
                                           Freshness freshness,
                                           String cardEndpoint) {
        return new ResolverResponse(
            query, ambiguity, matches,
            null, freshness,
            matches.size() == 1
                ? new SuggestedNext(cardEndpoint, "Fetch full card details")
                : null
        );
    }
}
