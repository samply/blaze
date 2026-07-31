package blaze.fhir.writing;

import blaze.fhir.spec.type.Complex;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

/**
 * Type handler for complex types with a Java implementation, which are able to
 * write themselves.
 */
public enum ComplexTypeHandler implements TypeHandler {

    INSTANCE;

    @Override
    public void write(JsonGenerator generator, Object value) throws IOException {
        ((Complex) value).serializeAsJsonValue(generator);
    }
}
