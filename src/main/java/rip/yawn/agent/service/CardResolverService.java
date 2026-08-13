package rip.yawn.agent.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import rip.yawn.agent.dto.ResolverMatch;
import rip.yawn.agent.dto.ResolverResponse;
import rip.yawn.agent.model.CardAlias;
import rip.yawn.agent.model.PokemonCardSummary;
import rip.yawn.agent.repository.PokemonCardSummaryRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Core card resolution logic. Typed aliases are evidence and constraints in
 * the normal candidate pipeline; they never bypass ranking.
 */
@Service
public class CardResolverService {

    private static final double WEIGHT_NAME_TOKEN_MATCH = 0.60;
    private static final double WEIGHT_EXACT_NAME_MATCH = 0.15;
    private static final double WEIGHT_CARD_ALIAS_MATCH = 0.70;
    private static final double WEIGHT_SET_MATCH = 0.20;
    private static final double WEIGHT_NUMBER_MATCH = 0.15;
    private static final double WEIGHT_RARITY_MATCH = 0.05;
    private static final int MAX_QUERY_LENGTH = 200;
    private static final int MAX_QUERY_TOKENS = 8;

    private static final Set<String> SEALED_PHRASES = Set.of(
        "booster box", "booster pack", "booster bundle",
        "elite trainer", "etb",
        "collection box",
        "blister",
        "bundle"
    );

    private static final Comparator<ScoredCard> RANK_ORDER = Comparator
        .comparingDouble(ScoredCard::score).reversed()
        .thenComparing(scored -> normalize(scored.card().getId()))
        .thenComparing(scored -> normalize(scored.card().getName()));

    private final PokemonCardSummaryRepository cardRepository;
    private final AliasService aliasService;
    private final ResolverFreshnessProvider freshnessProvider;
    private final ResolverLinkService linkService;

    public CardResolverService(PokemonCardSummaryRepository cardRepository,
                               AliasService aliasService,
                               ResolverFreshnessProvider freshnessProvider,
                               ResolverLinkService linkService) {
        this.cardRepository = cardRepository;
        this.aliasService = aliasService;
        this.freshnessProvider = freshnessProvider;
        this.linkService = linkService;
    }

    @Cacheable("resolver")
    public ResolverResponse resolve(String query) {
        if (query == null || query.isBlank()) {
            return noMatch(query == null ? "" : query, "Query is empty");
        }

        String normalized = normalize(query);
        if (normalized.length() > MAX_QUERY_LENGTH) {
            return noMatch(normalized.substring(0, MAX_QUERY_LENGTH), "Query is too long");
        }
        if (tokenize(normalized).size() > MAX_QUERY_TOKENS) {
            return noMatch(normalized, "Query contains too many distinct terms");
        }
        if (looksLikeSealed(normalized)) {
            return ResolverResponse.sealedMisfire(normalized);
        }

        AliasService.AliasResolution aliasResolution = aliasService.resolve(normalized);
        QueryEvidence queryEvidence = QueryEvidence.from(aliasResolution.remainingTokens());
        AliasEvidence initialAliasEvidence = AliasEvidence.from(aliasResolution.matches());

        if (queryEvidence.nameTokens().isEmpty() && initialAliasEvidence.cardIds().isEmpty()) {
            return noMatch(normalized, "Query contained only set/rarity terms, no card name");
        }

        Map<String, PokemonCardSummary> candidatesById = new TreeMap<>();
        for (String cardId : initialAliasEvidence.cardIds()) {
            cardRepository.findSummaryById(cardId)
                .ifPresent(card -> candidatesById.put(card.getId(), card));
        }
        for (String nameToken : queryEvidence.nameTokens()) {
            for (PokemonCardSummary card
                : cardRepository.findTop50ByNameContainingIgnoreCaseOrderByIdAsc(nameToken)) {
                candidatesById.put(card.getId(), card);
            }
        }

        if (candidatesById.isEmpty()) {
            return noMatch(normalized, "No cards matched any evidence in the query");
        }

        ContextualEvidence contextualEvidence = contextualizeAliases(
            aliasResolution.matches(), queryEvidence, candidatesById.values());
        AliasEvidence aliasEvidence = AliasEvidence.from(contextualEvidence.aliases());
        QueryEvidence contextualQueryEvidence = contextualEvidence.query();

        List<ScoredCard> scored = candidatesById.values().stream()
            .filter(card -> aliasEvidence.allows(card))
            .map(card -> scoreCard(card, contextualQueryEvidence, aliasEvidence))
            .filter(scoredCard -> scoredCard.score() > 0.0)
            .sorted(RANK_ORDER)
            .toList();

        if (scored.isEmpty()) {
            return noMatch(normalized, "Cards matched the name but not the alias constraints");
        }

        RankedResults rankedResults = rank(scored);
        List<ResolverMatch> matches = rankedResults.cards().stream()
            .map(scoredCard -> buildMatch(
                scoredCard.card(),
                scoredCard.score(),
                buildWhy(contextualQueryEvidence, aliasEvidence, scoredCard)
            ))
            .toList();

        String cardEndpoint = matches.size() == 1
            ? linkService.cardDetails(matches.getFirst().cardId())
            : null;
        return ResolverResponse.matched(
            normalized,
            rankedResults.ambiguity(),
            matches,
            freshnessProvider.currentFreshness(),
            cardEndpoint
        );
    }

    private ResolverResponse noMatch(String query, String reason) {
        return ResolverResponse.noMatch(query, reason, linkService.cardSearch());
    }

    private static ScoredCard scoreCard(PokemonCardSummary card,
                                        QueryEvidence query,
                                        AliasEvidence aliases) {
        String cardName = normalize(card.getName());
        String cardNumber = normalize(card.getNumber());
        double score = 0.0;

        if (!query.nameTokens().isEmpty()) {
            long matchedTokens = query.nameTokens().stream()
                .filter(cardName::contains)
                .count();
            score += ((double) matchedTokens / query.nameTokens().size())
                * WEIGHT_NAME_TOKEN_MATCH;
            if (cardName.equals(String.join(" ", query.nameTokens()))) {
                score += WEIGHT_EXACT_NAME_MATCH;
            }
        }

        if (aliases.cardIds().contains(card.getId())) {
            score += WEIGHT_CARD_ALIAS_MATCH;
        }
        if (!aliases.setIds().isEmpty()) {
            score += WEIGHT_SET_MATCH;
        }
        if (!aliases.rarities().isEmpty()) {
            score += WEIGHT_RARITY_MATCH;
        }
        if (query.numbers().stream().anyMatch(number -> numberMatches(cardNumber, number))) {
            score += WEIGHT_NUMBER_MATCH;
        }

        return new ScoredCard(card, Math.min(1.0, score));
    }

    private static boolean numberMatches(String cardNumber, String queryNumber) {
        String normalizedCardNumber = cardNumber.replaceFirst("^0+(?!$)", "");
        String normalizedQueryNumber = queryNumber.replaceFirst("^0+(?!$)", "");
        return cardNumber.equals(queryNumber) || normalizedCardNumber.equals(normalizedQueryNumber);
    }

    private static RankedResults rank(List<ScoredCard> scored) {
        double topScore = scored.getFirst().score();
        if (scored.size() == 1) {
            String ambiguity;
            if (topScore >= 0.90) {
                ambiguity = "none";
            } else if (topScore >= 0.70) {
                ambiguity = "low";
            } else if (topScore >= 0.50) {
                ambiguity = "medium";
            } else {
                ambiguity = "high";
            }
            return new RankedResults(ambiguity, scored);
        }
        if (scored.size() >= 4 || topScore < 0.50) {
            return new RankedResults("high", scored.subList(0, Math.min(5, scored.size())));
        }
        return new RankedResults("medium", scored);
    }

    private static ResolverMatch buildMatch(PokemonCardSummary card,
                                            double confidence,
                                            String why) {
        return new ResolverMatch(
            card.getId(),
            card.getName(),
            card.getNumber(),
            null,
            card.getSetId(),
            card.getRarity(),
            Math.round(confidence * 100.0) / 100.0,
            why
        );
    }

    private static String buildWhy(QueryEvidence query,
                                   AliasEvidence aliases,
                                   ScoredCard scored) {
        List<String> parts = new ArrayList<>();
        if (!query.nameTokens().isEmpty()) {
            parts.add("matched name tokens");
        }
        if (aliases.cardIds().contains(scored.card().getId())) {
            parts.add("card alias matched");
        }
        if (!aliases.setIds().isEmpty()) {
            parts.add("set alias matched");
        }
        if (!aliases.rarities().isEmpty()) {
            parts.add("rarity alias matched");
        }
        if (!query.numbers().isEmpty()) {
            parts.add("number evidence considered");
        }
        parts.add("score=" + String.format(Locale.ROOT, "%.2f", scored.score()));
        return String.join(", ", parts);
    }

    private static boolean looksLikeSealed(String query) {
        return SEALED_PHRASES.stream().anyMatch(query::contains);
    }

    static List<String> tokenize(String query) {
        return Arrays.stream(normalize(query)
                .replaceAll("[,\\.;:!\\?\"\\(\\)\\[\\]{}]", " ")
                .split("\\s+"))
            .filter(token -> !token.isBlank())
            .distinct()
            .toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static ContextualEvidence contextualizeAliases(
        List<CardAlias> aliases,
        QueryEvidence query,
        java.util.Collection<PokemonCardSummary> candidates
    ) {
        List<CardAlias> activeAliases = new ArrayList<>();
        List<String> lexicalTokens = new ArrayList<>();
        lexicalTokens.addAll(query.nameTokens());
        lexicalTokens.addAll(query.numbers());

        for (CardAlias alias : aliases) {
            boolean lexicalInCardName = alias.targetType() != CardAlias.TargetType.CARD
                && candidates.stream().anyMatch(card -> containsTokenPhrase(
                    tokenize(card.getName()), tokenize(alias.alias())));
            if (lexicalInCardName) {
                lexicalTokens.addAll(tokenize(alias.alias()));
            } else {
                activeAliases.add(alias);
            }
        }

        return new ContextualEvidence(activeAliases, QueryEvidence.from(lexicalTokens));
    }

    private static boolean containsTokenPhrase(List<String> value, List<String> phrase) {
        if (phrase.isEmpty() || phrase.size() > value.size()) {
            return false;
        }
        for (int start = 0; start <= value.size() - phrase.size(); start++) {
            if (value.subList(start, start + phrase.size()).equals(phrase)) {
                return true;
            }
        }
        return false;
    }

    private record ScoredCard(PokemonCardSummary card, double score) {}

    private record RankedResults(String ambiguity, List<ScoredCard> cards) {}

    private record ContextualEvidence(List<CardAlias> aliases, QueryEvidence query) {}

    private record QueryEvidence(List<String> nameTokens, List<String> numbers) {
        private static QueryEvidence from(List<String> remainingTokens) {
            List<String> uniqueTokens = remainingTokens.stream().distinct().toList();
            List<String> numbers = uniqueTokens.stream()
                .filter(token -> token.matches("\\d+(?:/\\d+)?"))
                .toList();
            List<String> names = uniqueTokens.stream()
                .filter(token -> !numbers.contains(token))
                .toList();
            return new QueryEvidence(names, numbers);
        }
    }

    private record AliasEvidence(
        Set<String> cardIds,
        Set<String> setIds,
        Set<String> rarities
    ) {
        private static AliasEvidence from(List<CardAlias> aliases) {
            return new AliasEvidence(
                targets(aliases, CardAlias.TargetType.CARD),
                targets(aliases, CardAlias.TargetType.SET),
                targets(aliases, CardAlias.TargetType.RARITY)
            );
        }

        private static Set<String> targets(List<CardAlias> aliases,
                                           CardAlias.TargetType targetType) {
            return aliases.stream()
                .filter(alias -> alias.targetType() == targetType)
                .map(CardAlias::canonicalTarget)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        private boolean allows(PokemonCardSummary card) {
            return (cardIds.isEmpty() || cardIds.contains(card.getId()))
                && (setIds.isEmpty() || containsNormalized(setIds, card.getSetId()))
                && (rarities.isEmpty() || containsNormalized(rarities, card.getRarity()));
        }

        private static boolean containsNormalized(Set<String> values, String candidate) {
            String normalizedCandidate = normalize(candidate);
            return values.stream().map(CardResolverService::normalize)
                .anyMatch(normalizedCandidate::equals);
        }
    }
}
