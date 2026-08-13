package rip.yawn.agent.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResolverLinkServiceTest {

    @Test
    void configuredOrigin_buildsAbsoluteSearchAndEncodedCardLinks() {
        ResolverLinkService links = new ResolverLinkService("https://example.test/");

        assertThat(links.cardSearch())
            .isEqualTo("https://example.test/api/pokemon/cards/search");
        assertThat(links.cardDetails("set/id ?1"))
            .isEqualTo("https://example.test/api/pokemon/cards/set%2Fid%20%3F1");
    }

    @Test
    void relativeOrigin_isRejected() {
        assertThatThrownBy(() -> new ResolverLinkService("/relative"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("absolute HTTP(S) origin");
    }

    @Test
    void credentialPathQueryAndFragmentOrigins_areRejected() {
        assertThatThrownBy(() -> new ResolverLinkService("https://user:pass@example.test"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResolverLinkService("https://example.test/base"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResolverLinkService("https://example.test?next=evil"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResolverLinkService("https://example.test#fragment"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
