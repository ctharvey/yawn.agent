package rip.yawn.agent.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Lightweight JPA projection onto the pokemon_cards table for
 * agent resolver queries. Read-only; never written by yawn.agent.
 */
@Entity
@Table(name = "pokemon_cards")
public class PokemonCardSummary {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", length = 256)
    private String name;

    @Column(name = "number", length = 32)
    private String number;

    @Column(name = "rarity", length = 64)
    private String rarity;

    @Column(name = "set_id", length = 64)
    private String setId;

    public PokemonCardSummary() {}

    public String getId() { return id; }
    public String getName() { return name; }
    public String getNumber() { return number; }
    public String getRarity() { return rarity; }
    public String getSetId() { return setId; }
}
