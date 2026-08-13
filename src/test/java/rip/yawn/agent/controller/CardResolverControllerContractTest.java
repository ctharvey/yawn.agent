package rip.yawn.agent.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import rip.yawn.agent.dto.Freshness;
import rip.yawn.agent.dto.ResolverMatch;
import rip.yawn.agent.dto.ResolverResponse;
import rip.yawn.agent.dto.SuggestedNext;
import rip.yawn.agent.service.CardResolverService;

/** Preserves the public resolver JSON contract while correcting its values. */
@WebMvcTest(CardResolverController.class)
class CardResolverControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CardResolverService resolverService;

    @Test
    void matchedResponseKeepsFieldsAndAbsoluteEncodedSuggestion() throws Exception {
        when(resolverService.resolve("pikachu promo")).thenReturn(new ResolverResponse(
            "pikachu promo",
            "none",
            List.of(new ResolverMatch("set/id ?1", "Pikachu", "1", null,
                "set", "Promo", 0.95, "matched name and set")),
            null,
            new Freshness(null, null),
            new SuggestedNext("https://yawn.rip/api/pokemon/cards/set%2Fid%20%3F1",
                "Fetch full card details")
        ));

        mockMvc.perform(get("/api/agent/card/resolve").queryParam("q", "pikachu promo"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.query").value("pikachu promo"))
            .andExpect(jsonPath("$.ambiguity").value("none"))
            .andExpect(jsonPath("$.matches[0].cardId").value("set/id ?1"))
            .andExpect(jsonPath("$.freshness.lastSeedSync").value(nullValue()))
            .andExpect(jsonPath("$.freshness.lastPriceUpdate").value(nullValue()))
            .andExpect(jsonPath("$.suggestedNext.endpoint")
                .value("https://yawn.rip/api/pokemon/cards/set%2Fid%20%3F1"));
    }

    @Test
    void noMatchResponseKeepsAbsoluteSearchSuggestion() throws Exception {
        when(resolverService.resolve("missing")).thenReturn(ResolverResponse.noMatch(
            "missing", "No cards matched", "https://yawn.rip/api/pokemon/cards/search"));

        mockMvc.perform(get("/api/agent/card/resolve").queryParam("q", "missing"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matches").isArray())
            .andExpect(jsonPath("$.noMatchReason").value("No cards matched"))
            .andExpect(jsonPath("$.suggestedNext.endpoint")
                .value("https://yawn.rip/api/pokemon/cards/search"));
    }

    @Test
    void sealedResponseAdvertisesNoNonexistentRoute() throws Exception {
        when(resolverService.resolve("151 etb"))
            .thenReturn(ResolverResponse.sealedMisfire("151 etb"));

        mockMvc.perform(get("/api/agent/card/resolve").queryParam("q", "151 etb"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ambiguity").value("high"))
            .andExpect(jsonPath("$.suggestedNext").value(nullValue()));
    }

    @Test
    void oversizedPublicQuery_isRejectedBeforeResolverWork() throws Exception {
        mockMvc.perform(get("/api/agent/card/resolve").queryParam("q", "x".repeat(201)))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(resolverService);
    }
}
