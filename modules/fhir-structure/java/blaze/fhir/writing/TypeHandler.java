package blaze.fhir.writing;

import clojure.lang.ILookup;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

/**
 * Writes FHIR values of one particular type.
 * <p>
 * Type handlers are created from the StructureDefinition of that type and don't
 * change after the writing context is built.
 */
public interface TypeHandler {

    /**
     * Resolves the type handlers this type handler needs at write time.
     * <p>
     * Has to be called after all type handlers are created, because type
     * handlers can reference each other recursively.
     */
    default void link(ILookup typeHandlers) {
    }

    void write(JsonGenerator generator, Object value) throws IOException;
}
