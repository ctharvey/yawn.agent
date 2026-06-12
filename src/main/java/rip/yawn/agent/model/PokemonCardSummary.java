package rip.yawn.agent.model;

/**
 * Lightweight read-only Spring Data interface projection over the canonical
 * {@code rip.yawn.db.model.card.PokemonCard} entity (pokemon_cards table).
 *
 * <p>Using a projection (rather than a second narrow {@code @Entity} on the
 * table) satisfies the single-canonical-entity boundary rule while still
 * fetching only the five columns the agent resolver needs — Spring Data emits
 * a SELECT limited to these properties for derived queries.
 */
public interface PokemonCardSummary {

    String getId();

    String getName();

    String getNumber();

    String getRarity();

    String getSetId();
}
