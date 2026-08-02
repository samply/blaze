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
 * The field name depends on the type of the value, so it's only known at write
 * time. The possible types are looked up by identity, because keywords are
 * interned.
 */
public final class PolymorphicPropertyHandler extends PropertyHandler {

    private final Keyword[] types;
    private final FieldName[] fieldNames;

    public PolymorphicPropertyHandler(Keyword key, Keyword[] types, FieldName[] fieldNames) {
        super(key);
        if (types.length != fieldNames.length) {
            throw new IllegalArgumentException("Different number of types and field names.");
        }
        this.types = types;
        this.fieldNames = fieldNames;
    }

    @Override
    public void writeValue(JsonGenerator generator, Object value) throws IOException {
        var type = value instanceof ILookup lookup ? lookup.valAt(Base.FHIR_TYPE_KEY) : null;
        if (type == null) {
            throw Base.noFhirType(value);
        }
        for (int i = 0; i < types.length; i++) {
            if (types[i] == type) {
                ((Base) value).serializeJsonField(generator, fieldNames[i]);
                return;
            }
        }
        throw new IllegalArgumentException("Unsupported type `%s` for polymorphic property `%s`.".formatted(type, key.getName()));
    }
}
