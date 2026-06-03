package rip.yawn.agent.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps collector shorthand or nicknames to a card ID.
 * Data is seeded via Flyway migration in the yawn.db project.
 */
@Entity
@Table(name = "card_aliases")
public class CardAlias {

    @Id
    @Column(name = "alias", length = 256)
    private String alias;

    @Column(name = "card_id", nullable = false, length = 64)
    private String cardId;

    @Column(name = "alias_type", nullable = false, length = 32)
    private String aliasType;

    public CardAlias() {}

    public CardAlias(String alias, String cardId, String aliasType) {
        this.alias = alias;
        this.cardId = cardId;
        this.aliasType = aliasType;
    }

    public String getAlias() { return alias; }
    public String getCardId() { return cardId; }
    public String getAliasType() { return aliasType; }
}
