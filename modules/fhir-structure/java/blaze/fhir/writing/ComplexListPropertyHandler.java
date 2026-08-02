package blaze.fhir.writing;

import blaze.fhir.spec.type.Base;
import blaze.fhir.spec.type.Complex;
import clojure.lang.Keyword;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;

import java.io.IOException;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Property handler for a repeating value that is able to write itself, namely a
 * complex type with a Java implementation, a backbone element or a resource.
 * <p>
 * The cardinality is taken from the element definition, so only the shape of
 * the value has to be checked here. See {@link ComplexPropertyHandler} for the
 * single-valued variant.
 */
public final class ComplexListPropertyHandler extends PropertyHandler {

    private final SerializableString fieldName;

    public ComplexListPropertyHandler(Keyword key, SerializableString fieldName) {
        super(key);
        this.fieldName = requireNonNull(fieldName);
    }

    @Override
    public void writeValue(JsonGenerator generator, Object value) throws IOException {
        if (!(value instanceof List<?> list)) {
            throw invalidValue(value);
        }
        generator.writeFieldName(fieldName);
        generator.writeStartArray();
        for (Object element : list) {
            // checked explicitly, because the implicit cast of an enhanced for
            // loop would report a class cast instead of the offending value
            if (!(element instanceof Complex complex)) {
                throw Base.noFhirType(element);
            }
            complex.serializeAsJsonValue(generator);
        }
        generator.writeEndArray();
    }

    /**
     * Diagnoses why {@code value} can't be written.
     * <p>
     * Separate method, so that {@link #writeValue} needs only the single type
     * check it has to do anyway for the cast.
     */
    private IllegalArgumentException invalidValue(Object value) {
        return value instanceof Complex ? listExpected() : Base.noFhirType(value);
    }
}
