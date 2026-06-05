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
        when(cardRepository.findById("swsh7-243"))
                .thenReturn(Optional.of(card("swsh7-243", "Umbreon VMAX", "swsh7", "243", "Alternate Art Rare")));

        ResolverResponse response = service.resolve("moonbreon");
        assertThat(response.matches()).hasSize(1);
        assertThat(response.matches().getFirst().cardId()).isEqualTo("swsh7-243");
        assertThat(response.matches().getFirst().confidence()).isEqualTo(0.95);
        assertThat(response.ambiguity()).isEqualTo("none");
    }

    @Test
    void resolve_aliasHit_twoCards_lowAmbiguity() {
        when(aliasService.resolveAlias("zard")).thenReturn(List.of("base1-4", "sv3pt5-199"));
        when(cardRepository.findById("base1-4"))
                .thenReturn(Optional.of(card("base1-4", "Charizard", "base1", "4", "Holo Rare")));
        when(cardRepository.findById("sv3pt5-199"))
                .thenReturn(Optional.of(card("sv3pt5-199", "Charizard ex", "sv3pt5", "199", "Special Illustration Rare")));

        ResolverResponse response = service.resolve("zard");
        assertThat(response.matches()).hasSize(2);
        assertThat(response.ambiguity()).isEqualTo("low");
    }

    // ---- resolve — ambiguity buckets ----

    @Test
    void resolve_singleHighConfidenceCandidate_noneAmbiguity() {
        // "pikachu v promo": nameTokens=["pikachu","v"], rarityTokens=["promo"]
        // Score: exact name match (pikachu v) → 0.60 + 0.15 bonus + rarity hit → 0.05 = 0.80
        // scored.size()==1 && topScore>=0.8 → ambiguity="none"
        PokemonCardSummary card = card("sv1-01", "Pikachu V", "sv1", "001", "Promo");
        when(cardRepository.findByNameContainingIgnoreCase("pikachu")).thenReturn(List.of(card));

        ResolverResponse response = service.resolve("pikachu v promo");
        assertThat(response.matches()).hasSize(1);
        assertThat(response.ambiguity()).isEqualTo("none");
    }

    @Test
    void resolve_multipleCandidates_clearLeader_lowAmbiguity() {
        // "charizard ex": nameTokens=["charizard","ex"]
        // best "Charizard ex": ratio 2/2=1.0 → 0.60 + exact name bonus 0.15 = 0.75
        // weak "Charizard":   ratio 1/2=0.5 → 0.30, no exact bonus
        // topScore=0.75 >= 0.70, diff=0.45 > 0.15 → "low"
        PokemonCardSummary best = card("sv3pt5-199", "Charizard ex", "sv3pt5", "199", "SIR");
        PokemonCardSummary weak = card("base1-4", "Charizard", "base1", "4", "Holo Rare");
        when(cardRepository.findByNameContainingIgnoreCase("charizard"))
                .thenReturn(List.of(best, weak));

        ResolverResponse response = service.resolve("charizard ex");
        assertThat(response.matches()).isNotEmpty();
        assertThat(response.ambiguity()).isEqualTo("low");
    }

    // ---- helper ----

    private PokemonCardSummary card(String id, String name, String setId,
                                    String number, String rarity) {
        PokemonCardSummary c = new PokemonCardSummary();
        try {
            setField(c, "id", id);
            setField(c, "name", name);
            setField(c, "setId", setId);
            setField(c, "number", number);
            setField(c, "rarity", rarity);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return c;
    }

    private void setField(Object obj, String fieldName, String value) throws Exception {
        var field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
