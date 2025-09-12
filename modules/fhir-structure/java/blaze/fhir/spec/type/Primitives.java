package blaze.fhir.spec.type;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.util.List;

public interface Primitives {

    static void serializeJsonPrimitiveList(List<? extends Primitive> values, JsonGenerator generator, FieldName fieldName) throws IOException {
        generator.writeFieldName(fieldName.normal());
        generator.writeStartArray();
        for (Primitive value : values) {
            value.serializeJsonPrimitiveValue(generator);
        }
        generator.writeEndArray();
        generator.writeFieldName(fieldName.extended());
        generator.writeStartArray();
        for (Primitive value : values) {
            value.serializeJsonPrimitiveExtension(generator);
        }
        generator.writeEndArray();
    }
}
