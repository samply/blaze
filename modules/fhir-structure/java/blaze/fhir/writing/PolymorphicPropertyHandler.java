package blaze.fhir.writing;

import blaze.fhir.spec.type.Base;
import blaze.fhir.spec.type.FieldName;
import clojure.lang.ILookup;
import clojure.lang.Keyword;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

/**
 * Property handler for polymorphic properties like {@code Observation.value[x]}.
 * <p>
 * The field name depends on the type of the value, so both the field name and
 * the type handler are only known at write time. The possible types are looked
 * up by identity, because keywords are interned.
 */
public final class PolymorphicPropertyHandler extends PropertyHandler {

    private final Keyword[] types;
    private final FieldName[] fieldNames;

    /**
     * Holds the type handler of the type at the same index or null if that type
     * is a primitive one, which writes itself.
     */
    private TypeHandler[] typeHandlers;

    public PolymorphicPropertyHandler(Keyword key, Keyword[] types, FieldName[] fieldNames) {
        super(key);
        if (types.length != fieldNames.length) {
            throw new IllegalArgumentException("Different number of types and field names.");
        }
        this.types = types;
        this.fieldNames = fieldNames;
    }

    @Override
    public void link(ILookup typeHandlers) {
        var handlers = new TypeHandler[types.length];
        for (int i = 0; i < types.length; i++) {
            handlers[i] = (TypeHandler) typeHandlers.valAt(types[i]);
        }
        this.typeHandlers = handlers;
    }

    @Override
    void writeValue(JsonGenerator generator, Object value) throws IOException {
        var type = value instanceof ILookup lookup ? lookup.valAt(Base.FHIR_TYPE_KEY) : null;
        if (type == null) {
            throw new IllegalArgumentException("Value `%s` is no FHIR type.".formatted(value));
        }
        for (int i = 0; i < types.length; i++) {
            if (types[i] == type) {
                var typeHandler = typeHandlers[i];
                if (typeHandler == null) {
                    ((Base) value).serializeJsonField(generator, fieldNames[i]);
                } else {
                    generator.writeFieldName(fieldNames[i].normal());
                    typeHandler.write(generator, value);
                }
                return;
            }
        }
        throw new IllegalArgumentException("Unsupported type `%s` for polymorphic property `%s`.".formatted(type, key.getName()));
    }
}
