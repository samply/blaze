package blaze.fhir.writing;

import blaze.fhir.spec.type.Base;
import clojure.lang.ILookup;
import clojure.lang.Keyword;
import clojure.lang.Sequential;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

/**
 * Property handler for properties holding resources like {@code contained}.
 * <p>
 * The type of each value is only known at write time. Each value has to be
 * written according to its own type.
 */
public final class ResourcePropertyHandler extends PropertyHandler {

    private final SerializableString fieldName;
    private ILookup typeHandlers;

    public ResourcePropertyHandler(Keyword key, SerializableString fieldName) {
        super(key);
        this.fieldName = requireNonNull(fieldName);
    }

    @Override
    public void link(ILookup typeHandlers) {
        this.typeHandlers = requireNonNull(typeHandlers);
    }

    @Override
    void writeValue(JsonGenerator generator, Object value) throws IOException {
        generator.writeFieldName(fieldName);
        if (value instanceof Sequential) {
            generator.writeStartArray();
            for (Object element : (Iterable<?>) value) {
                writeResource(generator, element);
            }
            generator.writeEndArray();
        } else {
            writeResource(generator, value);
        }
    }

    private void writeResource(JsonGenerator generator, Object value) throws IOException {
        var type = value instanceof ILookup lookup ? lookup.valAt(Base.FHIR_TYPE_KEY) : null;
        if (type == null) {
            throw new IllegalArgumentException("Value `%s` is no FHIR type.".formatted(value));
        }
        var typeHandler = (TypeHandler) typeHandlers.valAt(type);
        if (typeHandler == null) {
            throw new IllegalArgumentException("Missing handler for type `%s`.".formatted(type));
        }
        typeHandler.write(generator, value);
    }
}
