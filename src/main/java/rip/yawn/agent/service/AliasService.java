package rip.yawn.agent.service;

import org.springframework.stereotype.Service;
import rip.yawn.agent.model.CardAlias;
import rip.yawn.agent.repository.CardAliasRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds non-overlapping typed alias evidence in a query. Longer phrases win,
 * while all V138 targets for the winning phrase are retained.
 */
@Service
public class AliasService {

    private static final Comparator<CardAlias> TARGET_ORDER = Comparator
        .comparing((CardAlias alias) -> alias.targetType().name())
        .thenComparing(CardAlias::canonicalTarget);

    private final CardAliasRepository aliasRepository;

    public AliasService(CardAliasRepository aliasRepository) {
        this.aliasRepository = aliasRepository;
    }

    public AliasResolution resolve(String query) {
        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return new AliasResolution(List.of(), List.of());
        }

        Map<String, List<CardAlias>> aliasesByPhrase = new LinkedHashMap<>();
        aliasRepository.findAllLongestFirst().stream()
            .sorted(Comparator
                .comparingInt((CardAlias alias) -> normalize(alias.alias()).length()).reversed()
                .thenComparing(alias -> normalize(alias.alias()))
                .thenComparing(TARGET_ORDER))
            .forEach(alias -> aliasesByPhrase
                .computeIfAbsent(normalize(alias.alias()), ignored -> new ArrayList<>())
                .add(alias));

        boolean[] consumed = new boolean[queryTokens.size()];
        List<CardAlias> matches = new ArrayList<>();

        for (Map.Entry<String, List<CardAlias>> entry : aliasesByPhrase.entrySet()) {
            List<String> aliasTokens = tokenize(entry.getKey());
            int start = findUnconsumedPhrase(queryTokens, aliasTokens, consumed);
            if (start < 0) {
                continue;
            }

            entry.getValue().stream().sorted(TARGET_ORDER).forEach(matches::add);
            for (int index = start; index < start + aliasTokens.size(); index++) {
                consumed[index] = true;
            }
        }

        List<String> remainingTokens = new ArrayList<>();
        for (int index = 0; index < queryTokens.size(); index++) {
            if (!consumed[index]) {
                remainingTokens.add(queryTokens.get(index));
            }
        }

        return new AliasResolution(matches, remainingTokens);
    }

    private static int findUnconsumedPhrase(List<String> queryTokens,
                                             List<String> aliasTokens,
                                             boolean[] consumed) {
        if (aliasTokens.isEmpty() || aliasTokens.size() > queryTokens.size()) {
            return -1;
        }

        for (int start = 0; start <= queryTokens.size() - aliasTokens.size(); start++) {
            boolean matches = true;
            for (int offset = 0; offset < aliasTokens.size(); offset++) {
                if (consumed[start + offset]
                    || !queryTokens.get(start + offset).equals(aliasTokens.get(offset))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return start;
            }
        }
        return -1;
    }

    private static List<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(normalize(value)
                .replaceAll("[,\\.;:!\\?\"\\(\\)\\[\\]{}]", " ")
                .split("\\s+"))
            .filter(token -> !token.isBlank())
            .toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record AliasResolution(
        List<CardAlias> matches,
        List<String> remainingTokens
    ) {
        public AliasResolution {
            matches = List.copyOf(matches);
            remainingTokens = List.copyOf(remainingTokens);
        }
    }
}
