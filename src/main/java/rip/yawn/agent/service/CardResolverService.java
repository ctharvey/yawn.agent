package rip.yawn.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import rip.yawn.agent.dto.Freshness;
import rip.yawn.agent.dto.ResolverMatch;
import rip.yawn.agent.dto.ResolverResponse;
import rip.yawn.agent.model.PokemonCardSummary;
import rip.yawn.agent.repository.PokemonCardSummaryRepository;

import java.time.Instant;
import java.util.*;

/**
 * Core card resolution logic.
 *
 * Tokenizes the query, resolves aliases, searches by name token + set + number,
 * computes confidence scores, and buckets ambiguity.
 */
@Service
public class CardResolverService {

    private static final Logger log = LoggerFactory.getLogger(CardResolverService.class);

    // Confidence weight ranges
    private static final double WEIGHT_NAME_TOKEN_MATCH = 0.60;
    private static final double WEIGHT_SET_MATCH        = 0.20;
    private static final double WEIGHT_NUMBER_MATCH     = 0.15;
    private static final double WEIGHT_RARITY_MATCH     = 0.05;

    private final PokemonCardSummaryRepository cardRepository;
    private final AliasService aliasService;

    public CardResolverService(PokemonCardSummaryRepository cardRepository, AliasService aliasService) {
        this.cardRepository = cardRepository;
        this.aliasService = aliasService;
    }

    @Cacheable("resolver")
    public ResolverResponse resolve(String query) {
        if (query == null || query.isBlank()) {
            return ResolverResponse.noMatch(query == null ? "" : query, "Query is empty");
        }

        String normalized = query.trim().toLowerCase();

        // 1. Tokenize the query
        List<String> tokens = tokenize(normalized);

        // 2. Identify which tokens are rarity/set aliases vs card name tokens
        Set<String> rarityAliases = aliasService.getRarityAliases();
        Set<String> setAliases = aliasService.getSetAliases();

        List<String> rarityTokens = new ArrayList<>();
        List<String> setTokens = new ArrayList<>();
        List<String> nameTokens = new ArrayList<>();

        for (String token : tokens) {
            if (rarityAliases.contains(token)) {
                rarityTokens.add(token);
            } else if (setAliases.contains(token)) {
                setTokens.add(token);
            } else {
                nameTokens.add(token);
            }
        }

        // 3. Check for alias-based resolution (collector nickname)
        if (nameTokens.size() <= 2 && normalized.length() < 40) {
            List<String> aliasCardIds = aliasService.resolveAlias(normalized);
            if (!aliasCardIds.isEmpty()) {
                var matches = aliasCardIds.stream()
                    .map(cardId -> {
                        var card = cardRepository.findById(cardId);
                        return card.map(c -> buildMatch(c, 0.95, "Matched collector nickname"))
                            .orElse(null);
                    })
                    .filter(Objects::nonNull)
                    .toList();
                if (!matches.isEmpty()) {
                    return buildResponse(normalized, matches, "Matched via alias");
                }
            }
        }

        // 4. Try resolving by individual name tokens
        if (nameTokens.isEmpty()) {
            // Query was all aliases — cannot resolve to a card
            return ResolverResponse.noMatch(normalized,
                "Query contained only set/rarity terms, no card name");
        }

        // Search by first name token (most meaningful)
        String primaryNameToken = nameTokens.getFirst();
        List<PokemonCardSummary> candidates = cardRepository.findByNameContainingIgnoreCase(primaryNameToken);

        if (candidates.isEmpty()) {
            // Try the full combined name as a fallback
            String fullNameQuery = String.join(" ", nameTokens);
            log.debug("No match on primary token '{}', trying full name '{}'",
                primaryNameToken, fullNameQuery);
            // Fall back to a LIKE query on the entire name
            // (JPA can't do native CONTAINS on whole phrase, so we search each token)
            for (String token : nameTokens) {
                candidates = cardRepository.findByNameContainingIgnoreCase(token);
                if (!candidates.isEmpty()) break;
            }
        }

        if (candidates.isEmpty()) {
            return ResolverResponse.noMatch(normalized,
                "No cards matched any token in the query");
        }

        // 5. Score and rank candidates
        List<ScoredCard> scored = candidates.stream()
            .map(card -> scoreCard(card, nameTokens, setTokens, rarityTokens))
            .filter(s -> s.score() > 0.0)
            .sorted((a, b) -> Double.compare(b.score(), a.score()))
            .distinct()
            .toList();

        if (scored.isEmpty()) {
            return ResolverResponse.noMatch(normalized,
                "Query matched cards but confidence was too low");
        }

        // 6. Determine ambiguity
        double topScore = scored.getFirst().score();
        String ambiguity;
        List<ScoredCard> results;

        if (scored.size() == 1 && topScore >= 0.8) {
            ambiguity = "none";
            results = scored.subList(0, 1);
        } else if (scored.size() >= 2 && topScore - scored.get(1).score() > 0.15 && topScore >= 0.7) {
            // Clear winner
            ambiguity = "low";
            results = scored.subList(0, Math.min(3, scored.size()));
        } else {
            // Ambiguous — return top candidates with explanation
            ambiguity = scored.size() >= 2 && topScore > 0.3 ? "medium" : "high";
            results = scored.subList(0, Math.min(5, scored.size()));
        }

        List<ResolverMatch> matches = results.stream()
            .map(s -> buildMatch(s.card(), s.score(), buildWhy(nameTokens, setTokens, rarityTokens, s)))
            .toList();

        return buildResponse(normalized, matches, null);
    }

    // ---- internal helpers ----

    private record ScoredCard(PokemonCardSummary card, double score) {}

    private ScoredCard scoreCard(PokemonCardSummary card, List<String> nameTokens,
                                  List<String> setTokens, List<String> rarityTokens) {
        String cardName = card.getName().toLowerCase();
        String cardNumber = card.getNumber() != null ? card.getNumber().toLowerCase() : "";
        String cardRarity = card.getRarity() != null ? card.getRarity().toLowerCase() : "";
        String cardSetId = card.getSetId() != null ? card.getSetId().toLowerCase() : "";

        double score = 0.0;

        // Name token matching
        if (!nameTokens.isEmpty()) {
            long matchedTokens = nameTokens.stream()
                .filter(t -> cardName.contains(t) || t.contains(cardName))
                .count();
            double nameRatio = (double) matchedTokens / nameTokens.size();
            score += nameRatio * WEIGHT_NAME_TOKEN_MATCH;

            // Bonus for exact name match
            if (cardName.equals(String.join(" ", nameTokens))) {
                score += 0.15;
            }
        }

        // Set match
        if (!setTokens.isEmpty()) {
            for (String setToken : setTokens) {
                if (cardSetId.contains(setToken)) {
                    score += WEIGHT_SET_MATCH;
                    break;
                }
            }
            List<String> aliasCardIds = aliasService.resolveAlias(String.join(" ", setTokens));
            if (!aliasCardIds.isEmpty() && aliasCardIds.contains(card.getId())) {
                score += WEIGHT_SET_MATCH;
            }
        }

        // Number match
        if (!nameTokens.isEmpty()) {
            String lastToken = nameTokens.getLast();
            if (cardNumber.equals(lastToken) || cardNumber.replaceAll("^0+", "").equals(lastToken)) {
                score += WEIGHT_NUMBER_MATCH;
            }
        }

        // Rarity match
        if (!rarityTokens.isEmpty()) {
            for (String rarityToken : rarityTokens) {
                if (cardRarity.contains(rarityToken)) {
                    score += WEIGHT_RARITY_MATCH;
                    break;
                }
            }
        }

        // Cap at 1.0
        return new ScoredCard(card, Math.min(1.0, score));
    }

    private ResolverMatch buildMatch(PokemonCardSummary card, double confidence, String why) {
        return new ResolverMatch(
            card.getId(),
            card.getName(),
            card.getNumber(),
            null, // setName — would need join to pokemon_sets; TBD
            card.getSetId(),
            card.getRarity(),
            Math.round(confidence * 100.0) / 100.0,
            why
        );
    }

    private String buildWhy(List<String> nameTokens, List<String> setTokens,
                             List<String> rarityTokens, ScoredCard scored) {
        var parts = new ArrayList<String>();
        if (!nameTokens.isEmpty()) parts.add("matched name tokens");
        if (!setTokens.isEmpty()) parts.add("set hint matched");
        if (!rarityTokens.isEmpty()) parts.add("rarity hint matched");
        if (parts.isEmpty()) parts.add("matched");
        parts.add("score=" + String.format("%.2f", scored.score()));
        return String.join(", ", parts);
    }

    private ResolverResponse buildResponse(String query, List<ResolverMatch> matches,
                                            String aliasNote) {
        String ambiguity;
        if (matches.size() == 1) {
            ambiguity = matches.getFirst().confidence() >= 0.85 ? "none" : "low";
        } else {
            ambiguity = "medium";
        }

        Freshness freshness = new Freshness(
            Instant.now(), // placeholder — would read from metadata table
            Instant.now()
        );

        if (!matches.isEmpty()) {
            return ResolverResponse.matched(query, ambiguity, matches, freshness);
        }
        return ResolverResponse.noMatch(query, "No match found");
    }

    static List<String> tokenize(String query) {
        // Split on whitespace and common punctuation, filter blanks, deduplicate
        return Arrays.stream(query.toLowerCase().trim()
                .replaceAll("[,\\.;:!\\?\"\\(\\)\\[\\]{}]", " ")
                .split("\\s+"))
            .filter(t -> !t.isBlank())
            .distinct()
            .toList();
    }
}
