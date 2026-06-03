package rip.yawn.agent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rip.yawn.agent.model.PokemonCardSummary;

import java.util.List;

@Repository
public interface PokemonCardSummaryRepository extends JpaRepository<PokemonCardSummary, String> {

    List<PokemonCardSummary> findByNameContainingIgnoreCase(String name);

    List<PokemonCardSummary> findBySetIdAndNumber(String setId, String number);

    List<PokemonCardSummary> findBySetId(String setId);
}
