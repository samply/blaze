package blaze.fhir.writing;

import blaze.fhir.spec.type.Complex;
import clojure.lang.Keyword;
import clojure.lang.Sequential;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;

import java.io.IOException;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Property handler for complex types with a Java implementation, which are able
 * to write themselves.
 */
public final class ComplexPropertyHandler extends PropertyHandler {

    private final SerializableString fieldName;

    public ComplexPropertyHandler(Keyword key, SerializableString fieldName) {
        super(key);
        this.fieldName = requireNonNull(fieldName);
    }

    @Override
    @SuppressWarnings("unchecked")
    void writeValue(JsonGenerator generator, Object value) throws IOException {
        if (value instanceof Sequential) {
            Complex.serializeJsonComplexList((List<? extends Complex>) value, generator, fieldName);
        } else {
            generator.writeFieldName(fieldName);
            ((Complex) value).serializeAsJsonValue(generator);
        }
    }
}
