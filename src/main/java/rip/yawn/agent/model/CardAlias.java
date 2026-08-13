package rip.yawn.agent.model;

/**
 * One authoritative typed alias row from yawn.db migration V138.
 */
public record CardAlias(
    String alias,
    TargetType targetType,
    String targetCardId,
    String targetSetId,
    String targetRarity,
    String canonicalTarget
) {
    public enum TargetType {
        CARD,
        SET,
        RARITY
    }
}
