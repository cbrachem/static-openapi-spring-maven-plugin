package de.cbrachem.staticopenapi;

import com.fasterxml.jackson.databind.JavaType;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Fails when two types define a schema with the same name. swagger-core names
 * schemas by simple class name and silently keeps one of them.
 */
final class SchemaNameGuard implements ModelConverter {

    private final Map<String, JavaType> definedTypes = new HashMap<>();

    @Override
    @SuppressWarnings("rawtypes")
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        return chain.hasNext() ? chain.next().resolve(type, new GuardedContext(context), chain) : null;
    }

    private void register(String name, Type type) {
        if (name == null || type == null) {
            return;
        }
        var javaType = Schemas.mapper().constructType(type);
        var known = definedTypes.putIfAbsent(name, javaType);
        if (known != null && !known.equals(javaType)) {
            throw new GenerationException("Schema name \"" + name + "\" belongs to " + known.toCanonical()
                    + " and " + javaType.toCanonical() + ". Set @Schema(name = ...) on one of them.");
        }
    }

    @SuppressWarnings("rawtypes")
    private final class GuardedContext implements ModelConverterContext {

        private final ModelConverterContext delegate;

        GuardedContext(ModelConverterContext delegate) {
            this.delegate = delegate;
        }

        @Override
        public void defineModel(String name, Schema model) {
            delegate.defineModel(name, model);
        }

        @Override
        public void defineModel(String name, Schema model, AnnotatedType type, String prevName) {
            register(name, type == null ? null : type.getType());
            delegate.defineModel(name, model, type, prevName);
        }

        @Override
        public void defineModel(String name, Schema model, Type type, String prevName) {
            register(name, type);
            delegate.defineModel(name, model, type, prevName);
        }

        @Override
        public Schema resolve(AnnotatedType type) {
            return delegate.resolve(type);
        }

        @Override
        public Map<String, Schema> getDefinedModels() {
            return delegate.getDefinedModels();
        }

        @Override
        public Iterator<ModelConverter> getConverters() {
            return delegate.getConverters();
        }
    }
}
