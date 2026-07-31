package blaze.fhir.spec.type;

import clojure.lang.ILookupThunk;
import clojure.lang.Keyword;
import clojure.lang.RT;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.lang.String;
import java.util.Iterator;
import java.util.List;

public interface Primitive extends ExtensionValue {

    Keyword VALUE = RT.keyword(null, "value");

    ILookupThunk VALUE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Primitive p ? p.value() : this;
        }
    };

    Keyword[] FIELDS = {ID, EXTENSION, VALUE};

    /**
     * Writes all `values` as property `fieldName`, the values themselves and
     * their extensions in a separate `_fieldName` property.
     * <p>
     * Checks the type of all values and whether any of them has a value or is
     * extended in one pass, because a list is usually short and both properties
     * have to be known before anything is written.
     */
    static void serializeJsonPrimitiveList(List<?> values, JsonGenerator generator, FieldName fieldName) throws IOException {
        boolean hasValue = false;
        boolean isExtended = false;
        for (Object value : values) {
            if (!(value instanceof Primitive primitive)) {
                throw new IllegalArgumentException("Value `%s` is no FHIR type.".formatted(value));
            }
            hasValue |= primitive.hasValue();
            isExtended |= primitive.isExtended();
        }
        if (hasValue) {
            generator.writeFieldName(fieldName.normal());
            generator.writeStartArray();
            for (Object value : values) {
                ((Primitive) value).serializeJsonPrimitiveValue(generator);
            }
            generator.writeEndArray();
        }
        if (isExtended) {
            generator.writeFieldName(fieldName.extended());
            generator.writeStartArray();
            for (Object value : values) {
                ((Primitive) value).serializeJsonPrimitiveExtension(generator);
            }
            generator.writeEndArray();
        }
    }

    default boolean hasValue() {
        return value() != null;
    }

    Object value();

    default String valueAsString() {
        var value = value();
        return value == null ? null : value.toString();
    }

    boolean isExtended();

    @Override
    default ILookupThunk getLookupThunk(Keyword key) {
        return key == VALUE ? VALUE_LOOKUP_THUNK : ExtensionValue.super.getLookupThunk(key);
    }

    @Override
    default Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException;
}
