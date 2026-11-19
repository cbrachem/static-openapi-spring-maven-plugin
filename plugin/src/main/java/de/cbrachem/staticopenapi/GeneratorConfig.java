package de.cbrachem.staticopenapi;

import java.util.List;

public record GeneratorConfig(String title, String version, String defaultMediaType) {

    public GeneratorConfig {
        defaultMediaType = defaultMediaType == null ? "application/json" : defaultMediaType;
    }

    List<String> mediaTypesOrDefault(List<String> declared) {
        return declared.isEmpty() ? List.of(defaultMediaType) : declared;
    }
}
