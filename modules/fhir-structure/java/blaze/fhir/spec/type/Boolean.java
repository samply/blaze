package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import blaze.fhir.spec.type.system.Booleans;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public final class Boolean extends PrimitiveElement {

    public static final Boolean TRUE = new Boolean(null, null, java.lang.Boolean.TRUE);
    public static final Boolean FALSE = new Boolean(null, null, java.lang.Boolean.FALSE);

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "boolean");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueBoolean");

    private static final byte HASH_MARKER = 0;

    private static final Interner<InternerKey, Boolean> INTERNER = Interners.weakInterner(k -> new Boolean(null, k.extension, k.value));
    private static final Boolean EMPTY = new Boolean(null, null, null);

    private final java.lang.Boolean value;

    public Boolean(java.lang.String id, List<Extension> extension, java.lang.Boolean value) {
        super(id, extension);
        this.value = value;
    }

    private static Boolean intern(List<Extension> extension, java.lang.Boolean value) {
        return INTERNER.intern(new InternerKey(extension, value));
    }

    private static Boolean maybeIntern(java.lang.String id, List<Extension> extension, java.lang.Boolean value) {
        return id == null && Base.areAllInterned(extension) ? intern(extension, value) : new Boolean(id, extension, value);
    }

    public static Boolean create(IPersistentMap m) {
        return maybeIntern((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION), (java.lang.Boolean) m.valAt(VALUE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return isBaseInterned();
    }

    public java.lang.Boolean value() {
        return value;
    }

    @Override
    public Boolean empty() {
        return EMPTY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Boolean assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(id, extension, (java.lang.Boolean) val);
        if (key == EXTENSION) return maybeIntern(id, (List<Extension>) val, value);
        if (key == ID) return maybeIntern((java.lang.String) val, extension, value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Boolean.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            generator.writeBoolean(value);
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
            Booleans.hashInto(value, sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Boolean c = (Boolean) o;
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
        return "Boolean{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value=" + value +
                '}';
    }

    private record InternerKey(List<Extension> extension, java.lang.Boolean value) {
        private InternerKey {
            requireNonNull(extension);
        }
    }
}
