package blaze.fhir.spec.type;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

public interface Complex extends Base {

    @Override
    default boolean isInterned() {
        return false;
    }

    void serializeAsJsonValue(JsonGenerator generator) throws IOException;

    @Override
    default void serializeJsonField(JsonGenerator generator, FieldName fieldName) throws IOException {
        generator.writeFieldName(fieldName.normal());
        serializeAsJsonValue(generator);
    }
}
