package blaze.fhir.spec.type;

import clojure.lang.Keyword;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public interface Primitive extends ExtensionValue {

    Keyword[] FIELDS = {ID, EXTENSION, VALUE};

    boolean isBaseInterned();

    static boolean isBaseInterned(Primitive x) {
        return x == null || x.isBaseInterned();
    }

    @Override
    default boolean isInterned() {
        return isBaseInterned() && value() == null;
    }

    default boolean hasValue() {
        return value() != null;
    }

    java.lang.String id();

    Object value();

    default java.lang.String valueAsString() {
        var value = value();
        return value == null ? null : value.toString();
    }

    boolean isExtended();

    List<Extension> extension();

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
