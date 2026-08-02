package blaze.fhir.writing;

import blaze.fhir.spec.type.Base;
import blaze.fhir.spec.type.Complex;
import clojure.lang.Keyword;
import clojure.lang.Sequential;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

/**
 * Property handler for a single-valued complex type with a Java
 * implementation, which is able to write itself.
 * <p>
 * The cardinality is taken from the element definition, so no check of the
 * shape of the value is needed here. See {@link ComplexListPropertyHandler}
 * for the repeating variant.
 */
public final class ComplexPropertyHandler extends PropertyHandler {

    private final SerializableString fieldName;

    public ComplexPropertyHandler(Keyword key, SerializableString fieldName) {
        super(key);
        this.fieldName = requireNonNull(fieldName);
    }

    @Override
    public void writeValue(JsonGenerator generator, Object value) throws IOException {
        if (!(value instanceof Complex complex)) {
            throw invalidValue(value);
        }
        generator.writeFieldName(fieldName);
        complex.serializeAsJsonValue(generator);
    }

    /**
     * Diagnoses why {@code value} can't be written.
     * <p>
     * Separate method, so that {@link #writeValue} needs only the single type
     * check it has to do anyway for the cast.
     */
    private IllegalArgumentException invalidValue(Object value) {
        return value instanceof Sequential ? singleValueExpected() : Base.noFhirType(value);
    }
}
