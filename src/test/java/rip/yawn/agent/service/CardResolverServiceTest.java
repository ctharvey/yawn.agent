package rip.yawn.agent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import rip.yawn.agent.dto.ResolverResponse;
import rip.yawn.agent.model.PokemonCardSummary;
import rip.yawn.agent.repository.PokemonCardSummaryRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CardResolverServiceTest {

    @Mock
    private PokemonCardSummaryRepository cardRepository;

    @Mock
    private AliasService aliasService;

    @InjectMocks
    private CardResolverService service;

    @BeforeEach
    void setUpAliasDefaults() {
        when(aliasService.getRarityAliases()).thenReturn(Set.of("sir", "alt", "promo", "rainbow", "gold"));
        when(aliasService.getSetAliases()).thenReturn(Set.of("151", "es", "sv", "bs", "swsh"));
        when(aliasService.resolveAlias(any())).thenReturn(List.of());
    }

    // ---- tokenize ----

    @Test
    void tokenize_splitsByWhitespace() {
        assertThat(CardResolverService.tokenize("charizard 151 sir"))
                .containsExactly("charizard", "151", "sir");
    }

    @Test
    void tokenize_stripsLeadingTrailingWhitespace() {
        assertThat(CardResolverService.tokenize("  moonbreon  "))
                .containsExactly("moonbreon");
    }

    @Test
    void tokenize_collapsesInternalWhitespace() {
        assertThat(CardResolverService.tokenize("base  set  zard"))
                .containsExactly("base", "set", "zard");
    }

    @Test
    void tokenize_stripsPunctuation() {
        assertThat(CardResolverService.tokenize("pikachu, gray felt hat!"))
                .containsExactly("pikachu", "gray", "felt", "hat");
    }

    @Test
    void tokenize_deduplicatesTokens() {
        assertThat(CardResolverService.tokenize("charizard charizard"))
                .containsExactly("charizard");
    }

    @Test
    void tokenize_singleToken() {
        assertThat(CardResolverService.tokenize("moonbreon"))
                .containsExactly("moonbreon");
    }

    // ---- resolve — no-match cases ----

    @Test
    void resolve_emptyQuery_returnsHighAmbiguity() {
        ResolverResponse response = service.resolve("");
        assertThat(response.ambiguity()).isEqualTo("high");
        assertThat(response.matches()).isEmpty();
    }

    @Test
    void resolve_nullQuery_returnsHighAmbiguity() {
        ResolverResponse response = service.resolve(null);
        assertThat(response.ambiguity()).isEqualTo("high");
        assertThat(response.matches()).isEmpty();
    }

    @Test
    void resolve_noDbMatch_returnsHighAmbiguity() {
        when(cardRepository.findByNameContainingIgnoreCase(any())).thenReturn(List.of());

        ResolverResponse response = service.resolve("asdfghjkl");
        assertThat(response.ambiguity()).isEqualTo("high");
        assertThat(response.matches()).isEmpty();
    }

    @Test
    void resolve_onlySetTokens_returnsNoMatch() {
        // "151" is a set alias — no name tokens → cannot resolve
        ResolverResponse response = service.resolve("151");
        assertThat(response.matches()).isEmpty();
    }

    // ---- resolve — alias hit ----

    @Test
    void resolve_aliasHit_singleCard_noneAmbiguity() {
        when(aliasService.resolveAlias("moonbreon")).thenReturn(List.of("swsh7-243"));
        when(cardRepository.findSummaryById("swsh7-243"))
                .thenReturn(Optional.of(card("swsh7-243", "Umbreon VMAX", "swsh7", "243", "Alternate Art Rare")));

        ResolverResponse response = service.resolve("moonbreon");
        assertThat(response.matches()).hasSize(1);
        assertThat(response.matches().getFirst().cardId()).isEqualTo("swsh7-243");
        assertThat(response.matches().getFirst().confidence()).isEqualTo(0.95);
        assertThat(response.ambiguity()).isEqualTo("none");
    }

    @Test
    void resolve_aliasHit_twoCards_mediumAmbiguity() {
        // 2 alias matches → plan spec: 2-3 matches = "medium"
        when(aliasService.resolveAlias("zard")).thenReturn(List.of("base1-4", "sv3pt5-199"));
        when(cardRepository.findSummaryById("base1-4"))
                .thenReturn(Optional.of(card("base1-4", "Charizard", "base1", "4", "Holo Rare")));
        when(cardRepository.findSummaryById("sv3pt5-199"))
                .thenReturn(Optional.of(card("sv3pt5-199", "Charizard ex", "sv3pt5", "199", "Special Illustration Rare")));

        ResolverResponse response = service.resolve("zard");
        assertThat(response.matches()).hasSize(2);
        assertThat(response.ambiguity()).isEqualTo("medium");
    }

    // ---- resolve — ambiguity buckets ----

    @Test
    void resolve_singleHighConfidenceCandidate_noneAmbiguity() {
        // "pikachu v swsh promo": nameTokens=["pikachu","v"], setTokens=["swsh"], rarityTokens=["promo"]
        // Score: name ratio 1.0 → 0.60, exact name "pikachu v" → +0.15,
        //        set "swsh7".contains("swsh") → +0.20, rarity → +0.05 = 1.00 (capped)
        // scored.size()==1 && topScore>=0.90 → ambiguity="none"
        PokemonCardSummary card = card("swsh1-001", "Pikachu V", "swsh7", "001", "Promo");
        when(cardRepository.findByNameContainingIgnoreCase("pikachu")).thenReturn(List.of(card));

        ResolverResponse response = service.resolve("pikachu v swsh promo");
        assertThat(response.matches()).hasSize(1);
        assertThat(response.ambiguity()).isEqualTo("none");
    }

    @Test
    void resolve_singleMediumConfidenceCandidate_lowAmbiguity() {
        // "charizard ex": nameTokens=["charizard","ex"]
        // Score: ratio 1.0 → 0.60, exact name "charizard ex" → +0.15 = 0.75
        // scored.size()==1, 0.70 <= 0.75 < 0.90 → ambiguity="low"
        PokemonCardSummary card = card("sv3pt5-199", "Charizard ex", "sv3pt5", "199", "SIR");
        when(cardRepository.findByNameContainingIgnoreCase("charizard")).thenReturn(List.of(card));

        ResolverResponse response = service.resolve("charizard ex");
        assertThat(response.matches()).hasSize(1);
        assertThat(response.ambiguity()).isEqualTo("low");
    }

    @Test
    void resolve_multipleCandidates_isMediumAmbiguity() {
        // 2-3 matches always → "medium" per plan spec (no clear-leader exception)
        PokemonCardSummary best = card("sv3pt5-199", "Charizard ex", "sv3pt5", "199", "SIR");
        PokemonCardSummary weak = card("base1-4", "Charizard", "base1", "4", "Holo Rare");
        when(cardRepository.findByNameContainingIgnoreCase("charizard"))
                .thenReturn(List.of(best, weak));

        ResolverResponse response = service.resolve("charizard ex");
        assertThat(response.matches()).isNotEmpty();
        assertThat(response.ambiguity()).isEqualTo("medium");
    }

    // ---- sealed product detection ----

    @Test
    void resolve_boosterBoxQuery_redirectsSealedResolver() {
        ResolverResponse response = service.resolve("charizard 151 booster box");
        assertThat(response.matches()).isEmpty();
        assertThat(response.ambiguity()).isEqualTo("high");
        assertThat(response.noMatchReason()).contains("sealed product");
        assertThat(response.suggestedNext().endpoint()).isEqualTo("/api/agent/sealed/resolve");
    }

    @Test
    void resolve_etbQuery_redirectsSealedResolver() {
        ResolverResponse response = service.resolve("scarlet violet etb");
        assertThat(response.matches()).isEmpty();
        assertThat(response.suggestedNext().endpoint()).isEqualTo("/api/agent/sealed/resolve");
    }

    @Test
    void resolve_bundleQuery_redirectsSealedResolver() {
        ResolverResponse response = service.resolve("151 bundle");
        assertThat(response.matches()).isEmpty();
        assertThat(response.suggestedNext().endpoint()).isEqualTo("/api/agent/sealed/resolve");
    }

    @Test
    void resolve_normalQuery_notDetectedAsSealed() {
        // "charizard ex" has no sealed phrase — should pass through to card search
        when(cardRepository.findByNameContainingIgnoreCase("charizard")).thenReturn(List.of());
        ResolverResponse response = service.resolve("charizard ex");
        // Would be a card no-match, not a sealed redirect
        assertThat(response.suggestedNext().endpoint()).isNotEqualTo("/api/agent/sealed/resolve");
    }

    // ---- helper ----

    private PokemonCardSummary card(String id, String name, String setId,
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
