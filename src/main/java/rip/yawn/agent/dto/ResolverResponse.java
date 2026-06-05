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

    public static ResolverResponse noMatch(String query, String reason) {
        return new ResolverResponse(
            query, "high", List.of(),
            reason,
            null,
            new SuggestedNext(
                "/api/pokemon/cards/search",
                "Try manual search with fewer or different terms"
            )
        );
    }

    public static ResolverResponse matched(String query, String ambiguity,
                                           List<ResolverMatch> matches,
                                           Freshness freshness) {
        return new ResolverResponse(
            query, ambiguity, matches,
            null, freshness,
            matches.size() == 1
                ? new SuggestedNext(
                    "/api/pokemon/cards/" + matches.getFirst().cardId(),
                    "Fetch full card details")
                : null
        );
    }
}
