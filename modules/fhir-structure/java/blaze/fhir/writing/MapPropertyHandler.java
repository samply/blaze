package blaze.fhir.writing;

import clojure.lang.ILookup;
import clojure.lang.Keyword;
import clojure.lang.Sequential;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

/**
 * Property handler for types that are represented as maps, namely backbone
 * elements and complex types without a Java implementation.
 */
public final class MapPropertyHandler extends PropertyHandler {

    private final Keyword type;
    private final SerializableString fieldName;
    private MapTypeHandler typeHandler;

    public MapPropertyHandler(Keyword key, Keyword type, SerializableString fieldName) {
        super(key);
        this.type = requireNonNull(type);
        this.fieldName = requireNonNull(fieldName);
    }

    @Override
    public void link(ILookup typeHandlers) {
        var typeHandler = typeHandlers.valAt(type);
        if (typeHandler == null) {
            throw new IllegalStateException("Missing handler for type `%s`.".formatted(type));
        }
        this.typeHandler = (MapTypeHandler) typeHandler;
    }

    @Override
    void writeValue(JsonGenerator generator, Object value) throws IOException {
        generator.writeFieldName(fieldName);
        if (value instanceof Sequential) {
            generator.writeStartArray();
            for (Object element : (Iterable<?>) value) {
                typeHandler.write(generator, element);
            }
            generator.writeEndArray();
        } else {
            typeHandler.write(generator, value);
        }
    }
}
