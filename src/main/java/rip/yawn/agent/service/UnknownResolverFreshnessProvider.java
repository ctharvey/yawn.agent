package rip.yawn.agent.service;

import org.springframework.stereotype.Component;
import rip.yawn.agent.dto.Freshness;

/**
 * Default until an authoritative catalog/price metadata source is wired.
 */
@Component
public class UnknownResolverFreshnessProvider implements ResolverFreshnessProvider {

    private static final Freshness UNKNOWN = new Freshness(null, null);

    @Override
    public Freshness currentFreshness() {
        return UNKNOWN;
    }
}
