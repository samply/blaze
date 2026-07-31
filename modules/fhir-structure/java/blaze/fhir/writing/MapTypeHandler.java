package blaze.fhir.writing;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

/**
 * Type handler for backbone elements and complex types without a Java
 * implementation.
 */
public final class MapTypeHandler extends AbstractMapTypeHandler {

    public MapTypeHandler(PropertyHandler[] propertyHandlers) {
        super(propertyHandlers);
    }

    @Override
    public void write(JsonGenerator generator, Object value) throws IOException {
        var map = checkMap(value);
        generator.writeStartObject();
        writeProperties(generator, map);
        generator.writeEndObject();
    }
}
