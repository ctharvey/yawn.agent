package rip.yawn.agent.service;

import rip.yawn.agent.dto.Freshness;

/**
 * Narrow boundary for authoritative resolver metadata timestamps.
 */
@FunctionalInterface
public interface ResolverFreshnessProvider {

    Freshness currentFreshness();
}
