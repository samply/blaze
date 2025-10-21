package blaze.fhir.spec.type;

import clojure.lang.Keyword;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.lang.String;
import java.util.Iterator;
import java.util.List;

public interface Primitive extends ExtensionValue {

    Keyword[] FIELDS = {ID, EXTENSION, VALUE};

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
