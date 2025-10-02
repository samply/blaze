package blaze.fhir.spec.type;

import clojure.lang.Keyword;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.util.Iterator;

public interface Primitive extends ExtensionValue {

    Keyword[] FIELDS = {ID, EXTENSION, VALUE};

    default boolean hasValue() {
        return value() != null;
    }

    Object value();

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
}
