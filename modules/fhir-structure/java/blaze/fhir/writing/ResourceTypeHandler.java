package blaze.fhir.writing;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

/**
 * Type handler for resources, which are represented as maps and are written
 * with their type as {@code resourceType} property.
 */
public final class ResourceTypeHandler extends AbstractMapTypeHandler {

    private static final SerializableString RESOURCE_TYPE = new SerializedString("resourceType");

    private final SerializableString type;

    public ResourceTypeHandler(String type, PropertyHandler[] propertyHandlers) {
        super(propertyHandlers);
        this.type = new SerializedString(requireNonNull(type));
    }

    @Override
    public void write(JsonGenerator generator, Object value) throws IOException {
        var map = checkMap(value);
        generator.writeStartObject();
        generator.writeFieldName(RESOURCE_TYPE);
        generator.writeString(type);
        writeProperties(generator, map);
        generator.writeEndObject();
    }
}
