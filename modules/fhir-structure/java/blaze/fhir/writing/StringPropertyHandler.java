package blaze.fhir.writing;

import clojure.lang.Keyword;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

/**
 * Property handler for the FHIRPath type {@code System.String}, which is
 * represented as plain Java string.
 */
public final class StringPropertyHandler extends PropertyHandler {

    private final SerializableString fieldName;

    public StringPropertyHandler(Keyword key, SerializableString fieldName) {
        super(key);
        this.fieldName = requireNonNull(fieldName);
    }

    @Override
    void writeValue(JsonGenerator generator, Object value) throws IOException {
        generator.writeFieldName(fieldName);
        generator.writeString((String) value);
    }
}
