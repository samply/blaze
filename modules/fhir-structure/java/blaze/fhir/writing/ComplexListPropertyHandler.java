package blaze.fhir.writing;

import blaze.fhir.spec.type.Complex;
import clojure.lang.Keyword;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;

import java.io.IOException;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Property handler for a repeating complex type with a Java implementation,
 * which is able to write itself.
 * <p>
 * The cardinality is taken from the element definition, so no check of the
 * shape of the value is needed here. See {@link ComplexPropertyHandler} for the
 * single-valued variant.
 */
public final class ComplexListPropertyHandler extends PropertyHandler {

    private final SerializableString fieldName;

    public ComplexListPropertyHandler(Keyword key, SerializableString fieldName) {
        super(key);
        this.fieldName = requireNonNull(fieldName);
    }

    @Override
    @SuppressWarnings("unchecked")
    void writeValue(JsonGenerator generator, Object value) throws IOException {
        if (!(value instanceof List<?> list)) {
            throw invalidValue(value);
        }
        Complex.serializeJsonComplexList((List<? extends Complex>) list, generator, fieldName);
    }

    /**
     * Diagnoses why {@code value} can't be written.
     * <p>
     * Separate method, so that {@link #writeValue} needs only the single type
     * check it has to do anyway for the cast.
     */
    private IllegalArgumentException invalidValue(Object value) {
        return value instanceof Complex ? listExpected() : noFhirType(value);
    }
}
