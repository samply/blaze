package blaze.fhir.writing;

import clojure.lang.ILookup;
import clojure.lang.Keyword;
import com.fasterxml.jackson.core.SerializableString;

import static java.util.Objects.requireNonNull;

/**
 * Common base of the property handlers for types that are represented as maps,
 * namely backbone elements and complex types without a Java implementation.
 */
abstract class AbstractMapPropertyHandler extends PropertyHandler {

    private final Keyword type;

    final SerializableString fieldName;

    /**
     * Resolved in {@link #link}, before the writing context is published.
     */
    MapTypeHandler typeHandler;

    AbstractMapPropertyHandler(Keyword key, Keyword type, SerializableString fieldName) {
        super(key);
        this.type = requireNonNull(type);
        this.fieldName = requireNonNull(fieldName);
    }

    @Override
    public final void link(ILookup typeHandlers) {
        var typeHandler = typeHandlers.valAt(type);
        if (typeHandler == null) {
            throw new IllegalStateException("Missing handler for type `%s`.".formatted(type));
        }
        this.typeHandler = (MapTypeHandler) typeHandler;
    }
}
