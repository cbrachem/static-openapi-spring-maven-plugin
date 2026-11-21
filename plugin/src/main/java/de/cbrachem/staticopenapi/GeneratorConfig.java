package de.cbrachem.staticopenapi;

import java.util.List;
import org.springframework.util.AntPathMatcher;

/**
 * @param jackson2Defaults true when the application sets {@code spring.jackson.use-jackson2-defaults=true}
 * @param pathsToMatch Ant patterns of the paths to include. Empty includes all paths.
 * @param pathsToExclude Ant patterns of the paths to exclude
 */
record GeneratorConfig(String title, String version, String defaultMediaType, boolean jackson2Defaults,
                       List<String> pathsToMatch, List<String> pathsToExclude) {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    GeneratorConfig {
        defaultMediaType = defaultMediaType == null ? "application/json" : defaultMediaType;
        pathsToMatch = pathsToMatch == null ? List.of() : List.copyOf(pathsToMatch);
        pathsToExclude = pathsToExclude == null ? List.of() : List.copyOf(pathsToExclude);
    }

    GeneratorConfig(String title, String version, String defaultMediaType, boolean jackson2Defaults) {
        this(title, version, defaultMediaType, jackson2Defaults, List.of(), List.of());
    }

    List<String> mediaTypesOrDefault(List<String> declared) {
        return declared.isEmpty() ? List.of(defaultMediaType) : declared;
    }

    boolean includesPath(String path) {
        return (pathsToMatch.isEmpty() || pathsToMatch.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path)))
                && pathsToExclude.stream().noneMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }
}
