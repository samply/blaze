package blaze.fhir.writing;

import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;

import java.io.IOException;
import java.util.List;

/**
 * Property handler for a repeating type that is represented as map, namely a
 * backbone element or a complex type without a Java implementation.
 * <p>
 * The cardinality is taken from the element definition, so the shape of the
 * value doesn't have to be checked at write time. See
 * {@link MapPropertyHandler} for the single-valued variant.
 */
public final class MapListPropertyHandler extends AbstractMapPropertyHandler {

    public MapListPropertyHandler(Keyword key, Keyword type, SerializableString fieldName) {
        super(key, type, fieldName);
    }

    @Override
    void writeValue(JsonGenerator generator, Object value) throws IOException {
        if (!(value instanceof List<?> list)) {
            throw invalidValue(value);
        }
        generator.writeFieldName(fieldName);
        generator.writeStartArray();
        for (Object element : list) {
            typeHandler.write(generator, element);
        }
        generator.writeEndArray();
    }

    /**
     * Diagnoses why {@code value} can't be written.
     * <p>
     * Separate method, so that {@link #writeValue} needs only a single type
     * check.
     */
    private IllegalArgumentException invalidValue(Object value) {
        return value instanceof IPersistentMap ? listExpected() : noFhirType(value);
    }
}
