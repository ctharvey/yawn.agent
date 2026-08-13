package rip.yawn.agent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import rip.yawn.agent.dto.Freshness;
import rip.yawn.agent.dto.ResolverResponse;
import rip.yawn.agent.model.CardAlias;
import rip.yawn.agent.model.PokemonCardSummary;
import rip.yawn.agent.repository.PokemonCardSummaryRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CardResolverServiceTest {

    @Mock
    private PokemonCardSummaryRepository cardRepository;

    @Mock
    private AliasService aliasService;

    @Mock
    private ResolverFreshnessProvider freshnessProvider;

    private CardResolverService service;

    @BeforeEach
    void setUp() {
        when(aliasService.resolve(any())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0);
            return resolution(List.of(), CardResolverService.tokenize(query));
        });
        when(freshnessProvider.currentFreshness()).thenReturn(new Freshness(null, null));
        service = new CardResolverService(
            cardRepository,
            aliasService,
            freshnessProvider,
            new ResolverLinkService("https://yawn.rip/")
        );
    }

    @Test
    void tokenize_normalizesPunctuationWhitespaceAndDuplicates() {
        assertThat(CardResolverService.tokenize("  Pikachu,  gray Pikachu! "))
            .containsExactly("pikachu", "gray");
    }

    @Test
    void resolve_emptyQuery_returnsAbsoluteSearchSuggestion() {
        ResolverResponse response = service.resolve("");

        assertThat(response.ambiguity()).isEqualTo("high");
        assertThat(response.matches()).isEmpty();
        assertThat(response.suggestedNext().endpoint())
            .isEqualTo("https://yawn.rip/api/pokemon/cards/search");
    }

    @Test
    void resolve_noDbMatch_returnsHighAmbiguity() {
        when(cardRepository.findTop50ByNameContainingIgnoreCaseOrderByIdAsc("missing")).thenReturn(List.of());

        ResolverResponse response = service.resolve("missing");

        assertThat(response.ambiguity()).isEqualTo("high");
        assertThat(response.matches()).isEmpty();
    }

    @Test
    void resolve_setAlias151_constrainsToCanonicalSv3pt5Set() {
        CardAlias setAlias = alias("151", CardAlias.TargetType.SET, "sv3pt5");
        when(aliasService.resolve("charizard 151"))
            .thenReturn(resolution(List.of(setAlias), List.of("charizard")));
        when(cardRepository.findTop50ByNameContainingIgnoreCaseOrderByIdAsc("charizard")).thenReturn(List.of(
            card("base1-4", "Charizard", "base1", "4", "Rare Holo"),
            card("sv3pt5-199", "Charizard ex", "sv3pt5", "199", "Special Illustration Rare")
        ));

        ResolverResponse response = service.resolve("charizard 151");

        assertThat(response.matches()).extracting(match -> match.cardId())
            .containsExactly("sv3pt5-199");
        assertThat(response.matches().getFirst().confidence()).isEqualTo(0.8);
    }

    @Test
    void resolve_sirAlias_constrainsToCanonicalRarity() {
        CardAlias rarityAlias = alias(
            "sir", CardAlias.TargetType.RARITY, "Special Illustration Rare");
        when(aliasService.resolve("charizard sir"))
            .thenReturn(resolution(List.of(rarityAlias), List.of("charizard")));
        when(cardRepository.findTop50ByNameContainingIgnoreCaseOrderByIdAsc("charizard")).thenReturn(List.of(
            card("sv3pt5-199", "Charizard ex", "sv3pt5", "199", "Special Illustration Rare"),
            card("sv3pt5-006", "Charizard ex", "sv3pt5", "006", "Double Rare")
        ));

        ResolverResponse response = service.resolve("charizard sir");

        assertThat(response.matches()).extracting(match -> match.cardId())
            .containsExactly("sv3pt5-199");
        assertThat(response.matches().getFirst().why()).contains("rarity alias matched");
    }

    @Test
    void resolve_aliasPhraseInsideCardName_isLexicalEvidenceNotDestructiveSetFilter() {
        CardAlias exSetAlias = alias("ex", CardAlias.TargetType.SET, "ex1");
        when(aliasService.resolve("charizard ex"))
            .thenReturn(resolution(List.of(exSetAlias), List.of("charizard")));
        when(cardRepository.findTop50ByNameContainingIgnoreCaseOrderByIdAsc("charizard"))
            .thenReturn(List.of(
                card("sv4-125", "Charizard ex", "sv4", "125", "Double Rare"),
                card("base1-4", "Charizard", "base1", "4", "Rare Holo")
            ));

        ResolverResponse response = service.resolve("charizard ex");

        assertThat(response.matches()).extracting(match -> match.cardId())
            .containsExactly("sv4-125", "base1-4");
        assertThat(response.matches().getFirst().confidence()).isEqualTo(0.75);
        assertThat(response.matches().getFirst().why()).doesNotContain("set alias matched");
    }

    @Test
    void resolve_twoCardAliasTargets_bothSurviveAndRankDeterministically() {
        CardAlias second = alias("zard", CardAlias.TargetType.CARD, "sv3pt5-199");
        CardAlias first = alias("zard", CardAlias.TargetType.CARD, "base1-4");
        when(aliasService.resolve("zard"))
            .thenReturn(resolution(List.of(second, first), List.of()));
        when(cardRepository.findSummaryById("base1-4"))
            .thenReturn(Optional.of(card("base1-4", "Charizard", "base1", "4", "Rare Holo")));
        when(cardRepository.findSummaryById("sv3pt5-199"))
            .thenReturn(Optional.of(card(
                "sv3pt5-199", "Charizard ex", "sv3pt5", "199", "Special Illustration Rare")));

        ResolverResponse response = service.resolve("zard");

        assertThat(response.matches()).extracting(match -> match.cardId())
            .containsExactly("base1-4", "sv3pt5-199");
        assertThat(response.matches()).allSatisfy(match ->
            assertThat(match.confidence()).isEqualTo(0.7));
        assertThat(response.ambiguity()).isEqualTo("medium");
    }

    @Test
    void resolve_repeatedGenericName_isRankedAmbiguityWithoutAliasShortcut() {
        when(cardRepository.findTop50ByNameContainingIgnoreCaseOrderByIdAsc("charizard")).thenReturn(List.of(
            card("sv3pt5-199", "Charizard", "sv3pt5", "199", "Rare"),
            card("base1-4", "Charizard", "base1", "4", "Rare Holo")
        ));

        ResolverResponse response = service.resolve("charizard charizard");

        assertThat(response.matches()).extracting(match -> match.cardId())
            .containsExactly("base1-4", "sv3pt5-199");
        assertThat(response.matches()).allSatisfy(match ->
            assertThat(match.confidence()).isEqualTo(0.75));
        assertThat(response.ambiguity()).isEqualTo("medium");
    }

    @Test
    void resolve_scoreTies_useCardIdAsStableOrdering() {
        when(cardRepository.findTop50ByNameContainingIgnoreCaseOrderByIdAsc("pikachu")).thenReturn(List.of(
            card("z-set-2", "Pikachu", "z-set", "2", "Common"),
            card("a-set-1", "Pikachu", "a-set", "1", "Common")
        ));

        ResolverResponse first = service.resolve("pikachu");
        ResolverResponse second = service.resolve("pikachu");

        assertThat(first.matches()).extracting(match -> match.cardId())
            .containsExactly("a-set-1", "z-set-2");
        assertThat(second.matches()).isEqualTo(first.matches());
    }

    @Test
    void resolve_unknownFreshness_keepsBothFieldsNull() {
        PokemonCardSummary card = card("base1-4", "Charizard", "base1", "4", "Rare Holo");
        when(cardRepository.findTop50ByNameContainingIgnoreCaseOrderByIdAsc("charizard")).thenReturn(List.of(card));

        ResolverResponse response = service.resolve("charizard");

        assertThat(response.freshness()).isEqualTo(new Freshness(null, null));
    }

    @Test
    void resolve_preservesKnownAuthoritativeFreshness() {
        Instant seedSync = Instant.parse("2026-08-10T12:00:00Z");
        Instant priceUpdate = Instant.parse("2026-08-11T13:30:00Z");
        when(freshnessProvider.currentFreshness())
            .thenReturn(new Freshness(seedSync, priceUpdate));
        when(cardRepository.findTop50ByNameContainingIgnoreCaseOrderByIdAsc("charizard")).thenReturn(List.of(
            card("base1-4", "Charizard", "base1", "4", "Rare Holo")
        ));

        ResolverResponse response = service.resolve("charizard");

        assertThat(response.freshness().lastSeedSync()).isEqualTo(seedSync);
        assertThat(response.freshness().lastPriceUpdate()).isEqualTo(priceUpdate);
    }

    @Test
    void resolve_singleMatch_emitsAbsoluteEncodedCardSuggestion() {
        String cardId = "set/id ?1";
        when(cardRepository.findTop50ByNameContainingIgnoreCaseOrderByIdAsc("pikachu")).thenReturn(List.of(
            card(cardId, "Pikachu", "set", "1", "Common")
        ));

        ResolverResponse response = service.resolve("pikachu");

        assertThat(response.suggestedNext().endpoint()).isEqualTo(
            "https://yawn.rip/api/pokemon/cards/set%2Fid%20%3F1");
    }

    @Test
    void resolve_sealedQuery_hasNoInventedResolverSuggestion() {
        ResolverResponse response = service.resolve("151 elite trainer box");

        assertThat(response.matches()).isEmpty();
        assertThat(response.noMatchReason()).contains("sealed product");
        assertThat(response.suggestedNext()).isNull();
    }

    @Test
    void resolve_rejectsOversizedAndHighTokenFanoutBeforeDatabaseAccess() {
        ResolverResponse oversized = service.resolve("x".repeat(201));
        ResolverResponse tooManyTokens = service.resolve("one two three four five six seven eight nine");

        assertThat(oversized.noMatchReason()).isEqualTo("Query is too long");
        assertThat(tooManyTokens.noMatchReason()).isEqualTo("Query contains too many distinct terms");
        verify(aliasService, never()).resolve(any());
        verify(cardRepository, never()).findTop50ByNameContainingIgnoreCaseOrderByIdAsc(any());
    }

    private static AliasService.AliasResolution resolution(List<CardAlias> aliases,
                                                           List<String> remainingTokens) {
        return new AliasService.AliasResolution(aliases, remainingTokens);
    }

    private static CardAlias alias(String phrase,
                                   CardAlias.TargetType targetType,
                                   String canonicalTarget) {
        return new CardAlias(
            phrase,
            targetType,
            targetType == CardAlias.TargetType.CARD ? canonicalTarget : null,
            targetType == CardAlias.TargetType.SET ? canonicalTarget : null,
            targetType == CardAlias.TargetType.RARITY ? canonicalTarget : null,
            canonicalTarget
        );
    }

    private static PokemonCardSummary card(String id, String name, String setId,
                                           String number, String rarity) {
        return new PokemonCardSummary() {
            @Override public String getId() { return id; }
            @Override public String getName() { return name; }
            @Override public String getNumber() { return number; }
            @Override public String getRarity() { return rarity; }
            @Override public String getSetId() { return setId; }
        };
    }
}
