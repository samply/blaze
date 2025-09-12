package blaze.fhir.spec.type;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

public interface Primitive extends ExtensionValue {

    default boolean hasValue() {
        return value() != null;
    }

    Object value();

    boolean isExtended();

    void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException;

    default void serializeAsJsonProperty(JsonGenerator generator, FieldName fieldName) throws IOException {
        if (hasValue()) {
            generator.writeFieldName(fieldName.normal());
            serializeJsonPrimitiveValue(generator);
        }
        if (isExtended()) {
            generator.writeFieldName(fieldName.extended());
            serializeJsonPrimitiveExtension(generator);
        }
    }
}
