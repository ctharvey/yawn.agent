package rip.yawn.agent.service;

import org.junit.jupiter.api.Test;
import rip.yawn.agent.model.CardAlias;
import rip.yawn.agent.repository.CardAliasRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AliasServiceTest {

    private final CardAliasRepository repository = mock(CardAliasRepository.class);
    private final AliasService service = new AliasService(repository);

    @Test
    void resolve_maps151ToCanonicalSet() {
        when(repository.findAllLongestFirst()).thenReturn(List.of(
            alias("151", CardAlias.TargetType.SET, "sv3pt5")
        ));

        AliasService.AliasResolution result = service.resolve("charizard 151");

        assertThat(result.matches()).extracting(CardAlias::canonicalTarget)
            .containsExactly("sv3pt5");
        assertThat(result.remainingTokens()).containsExactly("charizard");
    }

    @Test
    void resolve_mapsSirToCanonicalRarity() {
        when(repository.findAllLongestFirst()).thenReturn(List.of(
            alias("sir", CardAlias.TargetType.RARITY, "Special Illustration Rare")
        ));

        AliasService.AliasResolution result = service.resolve("charizard sir");

        assertThat(result.matches()).singleElement().satisfies(match -> {
            assertThat(match.targetType()).isEqualTo(CardAlias.TargetType.RARITY);
            assertThat(match.targetRarity()).isEqualTo("Special Illustration Rare");
        });
    }

    @Test
    void resolve_prefersLongestOverlappingMultiwordAlias() {
        when(repository.findAllLongestFirst()).thenReturn(List.of(
            alias("alt", CardAlias.TargetType.RARITY, "Alternate Art Rare"),
            alias("alt art", CardAlias.TargetType.RARITY, "Alternate Art Rare")
        ));

        AliasService.AliasResolution result = service.resolve("umbreon alt art");

        assertThat(result.matches()).extracting(CardAlias::alias)
            .containsExactly("alt art");
        assertThat(result.remainingTokens()).containsExactly("umbreon");
    }

    @Test
    void resolve_preservesEveryCardTargetForSameAlias() {
        when(repository.findAllLongestFirst()).thenReturn(List.of(
            alias("zard", CardAlias.TargetType.CARD, "sv3pt5-199"),
            alias("zard", CardAlias.TargetType.CARD, "base1-4")
        ));

        AliasService.AliasResolution result = service.resolve("zard");

        assertThat(result.matches()).extracting(CardAlias::canonicalTarget)
            .containsExactly("base1-4", "sv3pt5-199");
        assertThat(result.remainingTokens()).isEmpty();
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
}
