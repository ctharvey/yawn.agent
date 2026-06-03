package rip.yawn.agent.dto;

import java.time.Instant;

public record Freshness(
    Instant lastSeedSync,
    Instant lastPriceUpdate
) {}
