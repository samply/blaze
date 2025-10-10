package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Strings;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Xhtml extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "xhtml");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueXhtml");

    private static final byte HASH_MARKER = 20;

    private final java.lang.String value;

    public Xhtml(java.lang.String id, List<Extension> extension, java.lang.String value) {
        super(id, extension);
        this.value = value;
    }

    public static Xhtml create(IPersistentMap m) {
        return new Xhtml((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION), (java.lang.String) m.valAt(VALUE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public java.lang.String value() {
        return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Xhtml empty() {
        return new Xhtml(null, PersistentVector.EMPTY, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Xhtml assoc(Object key, Object val) {
        if (key == VALUE) return new Xhtml(id, extension, (java.lang.String) val);
        if (key == EXTENSION) return new Xhtml(id, (List<Extension>) val, value);
        if (key == ID) return new Xhtml((java.lang.String) val, extension, value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Xhtml.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            generator.writeString(value);
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
            Strings.hashInto(value, sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Xhtml c = (Xhtml) o;
        return Objects.equals(id, c.id) &&
                Objects.equals(extension, c.extension) &&
                Objects.equals(value, c.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, value);
    }

    @Override
    public java.lang.String toString() {
        return "Xhtml{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value=" + (value == null ? null : '\'' + value + '\'') +
                '}';
    }
}
