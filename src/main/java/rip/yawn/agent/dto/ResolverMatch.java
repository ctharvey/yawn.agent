package rip.yawn.agent.dto;

public record ResolverMatch(
    String cardId,
    String name,
    String number,
    String setName,
    String setId,
    String rarity,
    double confidence,
    String why
) {}
