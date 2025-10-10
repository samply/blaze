package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Date extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "date");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueDate");

    private static final byte HASH_MARKER = 10;

    private final blaze.fhir.spec.type.system.Date value;

    public Date(java.lang.String id, List<Extension> extension, blaze.fhir.spec.type.system.Date value) {
        super(id, extension);
        this.value = value;
    }

    public static Date create(IPersistentMap m) {
        return new Date((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION), 
                (blaze.fhir.spec.type.system.Date) m.valAt(VALUE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public blaze.fhir.spec.type.system.Date value() {
        return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Date empty() {
        return new Date(null, PersistentVector.EMPTY, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Date assoc(Object key, Object val) {
        if (key == VALUE) return new Date(id, extension, (blaze.fhir.spec.type.system.Date) val);
        if (key == EXTENSION) return new Date(id, (List<Extension>) val, value);
        if (key == ID) return new Date((java.lang.String) val, extension, value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Date.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            generator.writeString(value.toString());
        } else {
            generator.writeNull();
        }
    }


    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        hashIntoBase(sink);
        if (value != null) {
            sink.putByte((byte) 2);
            value.hashInto(sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Date that = (Date) o;
        return Objects.equals(id, that.id) &&
                extension.equals(that.extension) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, value);
    }

    @Override
    public java.lang.String toString() {
        return "Date{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value=" + value +
                '}';
    }
}
