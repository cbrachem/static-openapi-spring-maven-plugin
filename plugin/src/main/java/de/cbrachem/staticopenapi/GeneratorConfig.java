package de.cbrachem.staticopenapi;

import java.util.List;

/**
 * @param jackson2Defaults true when the application sets {@code spring.jackson.use-jackson2-defaults=true}
 */
record GeneratorConfig(String title, String version, String defaultMediaType, boolean jackson2Defaults) {

    GeneratorConfig {
        defaultMediaType = defaultMediaType == null ? "application/json" : defaultMediaType;
    }

    List<String> mediaTypesOrDefault(List<String> declared) {
        return declared.isEmpty() ? List.of(defaultMediaType) : declared;
    }
}
