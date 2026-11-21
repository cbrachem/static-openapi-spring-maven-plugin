package de.cbrachem.staticopenapi;

import io.github.classgraph.ClassGraph;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ClassScanner {

    private static final Logger log = LoggerFactory.getLogger(ClassScanner.class);

    private ClassScanner() {}

    static List<Class<?>> scan(List<Path> classpath, List<String> basePackages, ClassLoader loader) {
        var graph = new ClassGraph()
                .overrideClasspath(classpath)
                .enableClassInfo()
                .ignoreClassVisibility()
                .acceptPackages(basePackages.toArray(String[]::new));

        try (var result = graph.scan()) {
            return result.getAllClasses().getNames().stream()
                    .sorted()
                    .flatMap(name -> load(name, loader).stream())
                    .toList();
        }
    }

    private static Optional<Class<?>> load(String name, ClassLoader loader) {
        try {
            return Optional.of(Class.forName(name, false, loader));
        } catch (ClassNotFoundException | LinkageError e) {
            log.debug("Skip class {}: {}", name, e.toString());
            return Optional.empty();
        }
    }
}
