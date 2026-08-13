package rip.yawn.agent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import rip.yawn.agent.model.CardAlias;

/**
 * Real-boundary proof that Agent consumes the migrated V138 typed alias contract.
 */
@Testcontainers(disabledWithoutDocker = true)
class CardAliasRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("yawn_agent_test")
        .withUsername("yawn")
        .withPassword("password");

    private static JdbcTemplate jdbc;
    private static CardAliasRepository repository;

    @BeforeAll
    static void migrateDatabase() {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        repository = new CardAliasRepository(jdbc);
    }

    @Test
    void migratedCatalogExposesCanonicalSetAndRarityTargetsLongestFirst() {
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version='138' AND success",
            Integer.class)).isEqualTo(1);

        List<CardAlias> aliases = repository.findAllLongestFirst();
        assertThat(aliases).anySatisfy(alias -> {
            assertThat(alias.alias()).isEqualTo("151");
            assertThat(alias.targetType()).isEqualTo(CardAlias.TargetType.SET);
            assertThat(alias.canonicalTarget()).isEqualTo("sv3pt5");
        }).anySatisfy(alias -> {
            assertThat(alias.alias()).isEqualTo("sir");
            assertThat(alias.targetType()).isEqualTo(CardAlias.TargetType.RARITY);
            assertThat(alias.canonicalTarget()).isEqualTo("Special Illustration Rare");
        });

        int multiword = indexOf(aliases, "rainbow rare");
        int singleWord = indexOf(aliases, "rainbow");
        assertThat(multiword).isGreaterThanOrEqualTo(0).isLessThan(singleWord);
    }

    @Test
    void migratedPrimaryKeyPreservesTwoCardTargetsForOneAlias() {
        jdbc.update("DELETE FROM card_aliases_typed WHERE alias = 'agent-fixture-zard'");
        jdbc.update("""
            INSERT INTO card_aliases_typed (alias, target_type, target_card_id, source)
            VALUES ('agent-fixture-zard', 'CARD', 'base1-4', 'TEST'),
                   ('agent-fixture-zard', 'CARD', 'sv3pt5-199', 'TEST')
            """);

        assertThat(repository.findAllLongestFirst().stream()
            .filter(alias -> alias.alias().equals("agent-fixture-zard")))
            .extracting(CardAlias::canonicalTarget)
            .containsExactly("base1-4", "sv3pt5-199");
    }

    private static int indexOf(List<CardAlias> aliases, String phrase) {
        for (int index = 0; index < aliases.size(); index++) {
            if (aliases.get(index).alias().equals(phrase)) {
                return index;
            }
        }
        return -1;
    }
}
