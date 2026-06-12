package rip.yawn.agent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rip.yawn.agent.model.PokemonCardSummary;
import rip.yawn.db.model.card.PokemonCard;

import java.util.List;
import java.util.Optional;

/**
 * Read-only access to pokemon_cards via the canonical PokemonCard entity,
 * returning the narrow {@link PokemonCardSummary} projection so only the
 * resolver's five columns are fetched.
 */
@Repository
public interface PokemonCardSummaryRepository extends JpaRepository<PokemonCard, String> {

    List<PokemonCardSummary> findByNameContainingIgnoreCase(String name);

    List<PokemonCardSummary> findBySetIdAndNumber(String setId, String number);

    List<PokemonCardSummary> findBySetId(String setId);

    Optional<PokemonCardSummary> findSummaryById(String id);
}
