package rip.yawn.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import rip.yawn.agent.dto.Freshness;

import static org.assertj.core.api.Assertions.assertThat;

class FreshnessSerializationTest {

    @Test
    void unknownFreshness_serializesExistingFieldsAsJsonNull() throws Exception {
        String json = new ObjectMapper().writeValueAsString(new Freshness(null, null));

        assertThat(json).isEqualTo("{\"lastSeedSync\":null,\"lastPriceUpdate\":null}");
    }
}
