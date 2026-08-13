package rip.yawn.agent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Builds reachable public yawn.rip navigation links for resolver clients.
 */
@Service
public class ResolverLinkService {

    private final String publicOrigin;

    public ResolverLinkService(
        @Value("${yawn.rip.base-url:https://yawn.rip}") String publicOrigin
    ) {
        String normalizedOrigin = stripTrailingSlashes(publicOrigin);
        URI parsedOrigin = URI.create(normalizedOrigin);
        if (!("http".equalsIgnoreCase(parsedOrigin.getScheme())
            || "https".equalsIgnoreCase(parsedOrigin.getScheme()))
            || parsedOrigin.getHost() == null
            || parsedOrigin.getUserInfo() != null
            || parsedOrigin.getQuery() != null
            || parsedOrigin.getFragment() != null
            || (parsedOrigin.getPath() != null && !parsedOrigin.getPath().isEmpty())) {
            throw new IllegalArgumentException("yawn.rip.base-url must be an absolute HTTP(S) origin");
        }
        try {
            this.publicOrigin = new URI(
                parsedOrigin.getScheme().toLowerCase(Locale.ROOT),
                null,
                parsedOrigin.getHost().toLowerCase(Locale.ROOT),
                parsedOrigin.getPort(),
                null,
                null,
                null
            ).toString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(
                "yawn.rip.base-url must be an absolute HTTP(S) origin", exception);
        }
    }

    public String cardDetails(String cardId) {
        return publicOrigin + "/api/pokemon/cards/"
            + UriUtils.encodePathSegment(cardId, StandardCharsets.UTF_8);
    }

    public String cardSearch() {
        return publicOrigin + "/api/pokemon/cards/search";
    }

    private static String stripTrailingSlashes(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
