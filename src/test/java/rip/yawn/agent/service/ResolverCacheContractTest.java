package rip.yawn.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

import rip.yawn.agent.dto.Freshness;
import rip.yawn.agent.model.PokemonCardSummary;
import rip.yawn.agent.repository.PokemonCardSummaryRepository;

/** Proves freshness is source metadata and follows the resolver cache lifecycle. */
class ResolverCacheContractTest {

    @Test
    void activeCachePreservesSourceTimestampUntilExplicitInvalidation() {
        PokemonCardSummaryRepository cards = mock(PokemonCardSummaryRepository.class);
        AliasService aliases = mock(AliasService.class);
        ResolverFreshnessProvider freshness = mock(ResolverFreshnessProvider.class);
        CacheManager cacheManager = new CaffeineCacheManager("resolver");

        when(aliases.resolve(anyString())).thenReturn(
            new AliasService.AliasResolution(List.of(), List.of("charizard")));
        when(cards.findTop50ByNameContainingIgnoreCaseOrderByIdAsc("charizard"))
            .thenReturn(List.of(card()));

        Freshness firstSource = new Freshness(
            Instant.parse("2026-08-10T12:00:00Z"), Instant.parse("2026-08-11T12:00:00Z"));
        Freshness nextSource = new Freshness(
            Instant.parse("2026-08-12T12:00:00Z"), Instant.parse("2026-08-13T12:00:00Z"));
        when(freshness.currentFreshness()).thenReturn(firstSource, nextSource);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CacheConfiguration.class);
            context.registerBean(CacheManager.class, () -> cacheManager);
            context.registerBean(PokemonCardSummaryRepository.class, () -> cards);
            context.registerBean(AliasService.class, () -> aliases);
            context.registerBean(ResolverFreshnessProvider.class, () -> freshness);
            context.registerBean(ResolverLinkService.class,
                () -> new ResolverLinkService("https://yawn.rip"));
            context.registerBean(CardResolverService.class);
            context.refresh();

            CardResolverService resolver = context.getBean(CardResolverService.class);
            assertThat(resolver.resolve("charizard").freshness()).isEqualTo(firstSource);
            assertThat(resolver.resolve("charizard").freshness()).isEqualTo(firstSource);
            verify(freshness, times(1)).currentFreshness();

            cacheManager.getCache("resolver").clear();
            assertThat(resolver.resolve("charizard").freshness()).isEqualTo(nextSource);
            verify(freshness, times(2)).currentFreshness();
        }
    }

    private static PokemonCardSummary card() {
        return new PokemonCardSummary() {
            @Override public String getId() { return "base1-4"; }
            @Override public String getName() { return "Charizard"; }
            @Override public String getNumber() { return "4"; }
            @Override public String getRarity() { return "Rare Holo"; }
            @Override public String getSetId() { return "base1"; }
        };
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class CacheConfiguration {}
}
