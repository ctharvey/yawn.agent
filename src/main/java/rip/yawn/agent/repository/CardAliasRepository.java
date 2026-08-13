package rip.yawn.agent.repository;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import rip.yawn.agent.model.CardAlias;

import java.util.List;

/**
 * Read-only access to the authoritative V138 typed alias contract.
 */
@Repository
public class CardAliasRepository {

    private static final String FIND_ALL_LONGEST_FIRST = """
        SELECT alias,
               target_type,
               target_card_id,
               target_set_id,
               target_rarity,
               canonical_target
        FROM card_aliases_typed
        ORDER BY char_length(alias) DESC,
                 alias ASC,
                 target_type ASC,
                 canonical_target ASC
        """;

    private final JdbcTemplate jdbcTemplate;

    public CardAliasRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Cacheable(cacheNames = "aliases", key = "'typed-catalog-v138'")
    public List<CardAlias> findAllLongestFirst() {
        return jdbcTemplate.query(FIND_ALL_LONGEST_FIRST, (resultSet, rowNumber) -> new CardAlias(
            resultSet.getString("alias"),
            CardAlias.TargetType.valueOf(resultSet.getString("target_type")),
            resultSet.getString("target_card_id"),
            resultSet.getString("target_set_id"),
            resultSet.getString("target_rarity"),
            resultSet.getString("canonical_target")
        ));
    }
}
