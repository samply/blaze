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

    static void serializeJsonPrimitiveList(List<?> values, JsonGenerator generator, FieldName fieldName) throws IOException {
        if (values.stream().anyMatch(value -> ((Primitive) value).hasValue())) {
            generator.writeFieldName(fieldName.normal());
            generator.writeStartArray();
            for (Object value : values) {
                ((Primitive) value).serializeJsonPrimitiveValue(generator);
            }
            generator.writeEndArray();
        }
        if (values.stream().anyMatch(value -> ((Primitive) value).isExtended())) {
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

    @Override
    default void serializeJsonField(JsonGenerator generator, FieldName fieldName) throws IOException {
        serializeAsJsonProperty(generator, fieldName);
    }
}
