package blaze.fhir.writing;

import clojure.lang.ILookup;
import clojure.lang.Keyword;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

/**
 * Writes one property of a FHIR value that is represented as map.
 */
public abstract class PropertyHandler {

    /**
     * The key of the property in the map to write.
     * <p>
     * Only used to build the {@link PropertyIndex} of the type handler.
     */
    final Keyword key;

    PropertyHandler(Keyword key) {
        this.key = requireNonNull(key);
    }

    /**
     * Resolves the type handlers this property handler needs at write time.
     * <p>
     * Has to be called after all type handlers are created, because type
     * handlers can reference each other recursively.
     * <p>
     * Property handlers are only mutated here, while the writing context is
     * built. The context is published to all other threads afterwards, as part
     * of the system map.
     */
    public void link(ILookup typeHandlers) {
    }

    /**
     * Writes the property this handler is responsible for, including its field
     * name.
     */
    abstract void writeValue(JsonGenerator generator, Object value) throws IOException;
}
