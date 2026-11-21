package de.cbrachem.staticopenapi;

import io.swagger.v3.core.util.Json31;
import io.swagger.v3.core.util.Yaml31;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Generates an OpenAPI document from the compiled Spring MVC controllers
 * without starting up a Spring context
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${project.build.directory}/openapi", property = "staticOpenapi.outputDirectory")
  private File outputDirectory;

  @Parameter(defaultValue = "openapi", property = "staticOpenapi.fileName")
  private String fileName;

  /** Output formats: "yaml", "json" or both. */
  @Parameter(defaultValue = "yaml", property = "staticOpenapi.formats")
  private List<String> formats;

  /** Fallback for info.title when not set by @OpenAPIDefinition. */
  @Parameter(defaultValue = "${project.name}", property = "staticOpenapi.title")
  private String title;

  /** Fallback for info.version when not set by @OpenAPIDefinition. */
  @Parameter(defaultValue = "${project.version}", property = "staticOpenapi.version")
  private String version;

  /**
   * Packages to scan. When empty, the plugin scans only the classes of this
   * module.
   * When set, the plugin scans these packages on the full compile classpath.
   */
  @Parameter(property = "staticOpenapi.basePackages")
  private List<String> basePackages = List.of();

  /**
   * Media type for request and response bodies when the mapping declares no
   * "consumes" or "produces".
   */
  @Parameter(defaultValue = "application/json", property = "staticOpenapi.defaultMediaType")
  private String defaultMediaType;

  /**
   * Set to true when the application sets
   * spring.jackson.use-jackson2-defaults=true.
   * Then a primitive creator parameter is not required, and enum values use
   * name().
   */
  @Parameter(defaultValue = "false", property = "staticOpenapi.jackson2Defaults")
  private boolean jackson2Defaults;

  /**
   * Ant patterns of the paths to include, for example {@code /api/**}. When empty,
   * the plugin includes all paths.
   */
  @Parameter(property = "staticOpenapi.pathsToMatch")
  private List<String> pathsToMatch = List.of();

  /** Ant patterns of the paths to exclude, for example {@code /internal/**}. */
  @Parameter(property = "staticOpenapi.pathsToExclude")
  private List<String> pathsToExclude = List.of();

  @Parameter(defaultValue = "false", property = "staticOpenapi.skip")
  private boolean skip;

  @Override
  public void execute() throws MojoExecutionException {
    if (skip) {
      getLog().info("Skip OpenAPI generation.");
      return;
    }

    var classpath = compileClasspath();
    var scanPath = basePackages.isEmpty() ? List.of(Path.of(project.getBuild().getOutputDirectory())) : classpath;
    var contextLoader = Thread.currentThread().getContextClassLoader();

    // The plugin class loader is the parent, therefore project classes and the
    // plugin share one set of annotation types.
    try (var loader = new URLClassLoader(urls(classpath), getClass().getClassLoader())) {
      Thread.currentThread().setContextClassLoader(loader);

      var classes = ClassScanner.scan(scanPath, basePackages, loader);
      var config = new GeneratorConfig(title, version, defaultMediaType, jackson2Defaults, pathsToMatch, pathsToExclude);
      var openApi = OpenApiGenerator.generate(classes, config);

      getLog().info("Found " + openApi.getPaths().size() + " paths in " + classes.size() + " classes.");

      for (var format : formats) {
        write(format, format.equals("json") ? Json31.pretty(openApi) : Yaml31.pretty(openApi));
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Cannot write the OpenAPI document", e);
    } finally {
      Thread.currentThread().setContextClassLoader(contextLoader);
    }
  }

  private void write(String format, String document) throws IOException {
    var file = outputDirectory.toPath().resolve(fileName + "." + format);

    Files.createDirectories(file.getParent());
    Files.writeString(file, document);

    getLog().info("Wrote " + file);
  }

  private List<Path> compileClasspath() throws MojoExecutionException {
    try {
      return project.getCompileClasspathElements().stream().map(Path::of).toList();
    } catch (DependencyResolutionRequiredException e) {
      throw new MojoExecutionException("Cannot resolve the compile classpath", e);
    }
  }

  private static URL[] urls(List<Path> classpath) {
    return classpath.stream().map(GenerateMojo::url).toArray(URL[]::new);
  }

  private static URL url(Path path) {
    try {
      return path.toUri().toURL();
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
