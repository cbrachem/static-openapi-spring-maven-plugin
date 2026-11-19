package de.cbrachem.staticopenapi;

import io.swagger.v3.oas.models.PathItem.HttpMethod;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.web.bind.annotation.RequestMapping;

record Mapping(List<String> paths, List<HttpMethod> methods, List<String> consumes, List<String> produces) {

    private static final List<HttpMethod> ALL_METHODS = List.of(
            HttpMethod.GET, HttpMethod.PUT, HttpMethod.POST, HttpMethod.DELETE,
            HttpMethod.PATCH, HttpMethod.HEAD, HttpMethod.OPTIONS);

    static Mapping of(RequestMapping type, RequestMapping method) {
        var typePaths = type == null ? List.of("") : orEmptyPath(type.path());
        var paths = typePaths.stream()
                .flatMap(prefix -> orEmptyPath(method.path()).stream().map(suffix -> combine(prefix, suffix)))
                .distinct()
                .toList();

        var methods = Stream.concat(Arrays.stream(method.method()), type == null ? Stream.empty() : Arrays.stream(type.method()))
                .map(m -> HttpMethod.valueOf(m.name()))
                .distinct()
                .toList();

        return new Mapping(
                paths,
                methods.isEmpty() ? ALL_METHODS : methods,
                override(type == null ? new String[0] : type.consumes(), method.consumes()),
                override(type == null ? new String[0] : type.produces(), method.produces()));
    }

    private static List<String> orEmptyPath(String[] paths) {
        return paths.length == 0 ? List.of("") : List.of(paths);
    }

    private static List<String> override(String[] typeLevel, String[] methodLevel) {
        return Stream.of(methodLevel.length > 0 ? methodLevel : typeLevel)
                .filter(mediaType -> !mediaType.startsWith("!"))
                .toList();
    }

    private static String combine(String prefix, String suffix) {
        var path = Stream.of(prefix, suffix)
                .flatMap(part -> Arrays.stream(stripPatterns(part).split("/")))
                .filter(segment -> !segment.isEmpty())
                .reduce("", (left, right) -> left + "/" + right);

        return path.isEmpty() ? "/" : path;
    }

    // "{id:[0-9]{3}}" and "{*rest}" become "{id}" and "{rest}"
    private static String stripPatterns(String path) {
        var out = new StringBuilder();
        int depth = 0;
        boolean inPattern = false;

        for (char c : path.toCharArray()) {
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) inPattern = false;
            if (depth == 1 && c == ':') inPattern = true;
            boolean catchAllMarker = depth == 1 && c == '*' && out.charAt(out.length() - 1) == '{';
            if (!inPattern && !catchAllMarker) out.append(c);
        }

        return out.toString();
    }
}
