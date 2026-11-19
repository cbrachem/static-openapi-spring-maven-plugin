package de.cbrachem.staticopenapi;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.ResolvableType;

final class ReturnTypes {

  private static final Set<String> EMPTY = Set.of("void", "java.lang.Void", "kotlin.Unit");

  private static final Set<String> WRAPPERS = Set.of(
      "org.springframework.http.ResponseEntity",
      "org.springframework.http.HttpEntity",
      "org.springframework.web.context.request.async.DeferredResult",
      "org.springframework.web.context.request.async.WebAsyncTask",
      "java.util.Optional",
      "java.util.concurrent.Callable",
      "java.util.concurrent.CompletionStage",
      "java.util.concurrent.CompletableFuture",
      "reactor.core.publisher.Mono");

  private static final Set<String> STREAMS = Set.of("reactor.core.publisher.Flux", "kotlinx.coroutines.flow.Flow");

  private ReturnTypes() {
  }

  static Optional<Type> bodyType(Type type) {
    var resolvable = ResolvableType.forType(type);
    var name = resolvable.toClass().getName();

    if (EMPTY.contains(name)) {
      return Optional.empty();
    }
    if (WRAPPERS.contains(name)) {
      return resolvable.hasGenerics() ? bodyType(resolvable.getGeneric(0).getType()) : Optional.empty();
    }
    if (STREAMS.contains(name)) {
      return Optional.of(ResolvableType.forClassWithGenerics(List.class, resolvable.getGeneric(0)).getType());
    }

    return Optional.of(resolvable.toClass() == Object.class ? Object.class : type);
  }
}
