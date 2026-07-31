package blaze.fhir.writing;

import blaze.fhir.spec.type.FieldName;
import blaze.fhir.spec.type.Primitive;
import clojure.lang.Keyword;
import clojure.lang.Sequential;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Property handler for primitive types.
 */
public final class PrimitivePropertyHandler extends PropertyHandler {

    private final FieldName fieldName;

    public PrimitivePropertyHandler(Keyword key, FieldName fieldName) {
        super(key);
        this.fieldName = requireNonNull(fieldName);
    }

    @Override
    void writeValue(JsonGenerator generator, Object value) throws IOException {
        if (value instanceof Sequential) {
            Primitive.serializeJsonPrimitiveList((List<?>) value, generator, fieldName);
        } else if (value instanceof Primitive primitive) {
            primitive.serializeAsJsonProperty(generator, fieldName);
        } else {
            throw new IllegalArgumentException("Value `%s` is no FHIR type.".formatted(value));
        }
    }
}
