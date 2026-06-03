package rip.yawn.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import rip.yawn.agent.model.CardAlias;
import rip.yawn.agent.repository.CardAliasRepository;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves collector shorthand and nicknames to card IDs.
 * Looks up aliases from the shared card_aliases table.
 */
@Service
public class AliasService {

    private static final Logger log = LoggerFactory.getLogger(AliasService.class);

    private final CardAliasRepository aliasRepository;

    public AliasService(CardAliasRepository aliasRepository) {
        this.aliasRepository = aliasRepository;
    }

    /**
     * Find card IDs that match a given alias (nickname, shorthand).
     */
    @Cacheable("aliases")
    public List<String> resolveAlias(String token) {
        List<CardAlias> aliases = aliasRepository.findByAlias(token.toLowerCase().trim());
        return aliases.stream()
            .map(CardAlias::getCardId)
            .distinct()
            .toList();
    }

    /**
     * Return the set of rarity shorthand tokens that should be stripped
     * during token matching (e.g. "sir", "alt", "rainbow").
     */
    public Set<String> getRarityAliases() {
        List<CardAlias> rarities = aliasRepository.findByAliasType("rarity");
        Set<String> tokens = new HashSet<>();
        for (CardAlias alias : rarities) {
            String a = alias.getAlias().toLowerCase().trim();
            if (!a.isBlank()) {
                tokens.add(a);
            }
        }
        return Collections.unmodifiableSet(tokens);
    }

    /**
     * Return the set of set shorthand tokens (e.g. "151", "es", "sv").
     */
    public Set<String> getSetAliases() {
        List<CardAlias> sets = aliasRepository.findByAliasType("set");
        Set<String> tokens = new HashSet<>();
        for (CardAlias alias : sets) {
            String a = alias.getAlias().toLowerCase().trim();
            if (!a.isBlank()) {
                tokens.add(a);
            }
        }
        return Collections.unmodifiableSet(tokens);
    }
}
