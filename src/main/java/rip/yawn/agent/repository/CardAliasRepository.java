package rip.yawn.agent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rip.yawn.agent.model.CardAlias;

import java.util.List;

@Repository
public interface CardAliasRepository extends JpaRepository<CardAlias, String> {

    List<CardAlias> findByAlias(String alias);

    List<CardAlias> findByAliasType(String aliasType);
}
