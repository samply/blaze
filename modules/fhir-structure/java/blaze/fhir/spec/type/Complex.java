package blaze.fhir.spec.type;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;

import java.io.IOException;
import java.util.List;

public interface Complex extends Base {

    static void serializeJsonComplexList(List<? extends Complex> values, JsonGenerator generator, SerializableString fieldName) throws IOException {
        generator.writeFieldName(fieldName);
        generator.writeStartArray();
        for (Complex element : values) {
            element.serializeAsJsonValue(generator);
        }
        generator.writeEndArray();
    }

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
