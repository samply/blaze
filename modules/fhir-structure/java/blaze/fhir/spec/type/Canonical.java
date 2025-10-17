package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import blaze.fhir.spec.type.system.Strings;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public final class Canonical extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "canonical");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueCanonical");

    private static final byte HASH_MARKER = 7;
    
    private static final Interner<InternerKey, Canonical> INTERNER = Interners.weakInterner(k -> create(null, k.extension, k.value));
    private static final Canonical EMPTY = new Canonical(null, null, null);

    private final SerializedString value;

    private Canonical(java.lang.String id, List<Extension> extension, SerializedString value) {
        super(id, extension);
        this.value = value;
    }

    private static Canonical create(java.lang.String id, List<Extension> extension, java.lang.String value) {
        return new Canonical(id, extension, value == null ? null : new SerializedString(value));
    }

    private static Canonical intern(List<Extension> extension, String value) {
        return INTERNER.intern(new InternerKey(extension, value));
    }

    private static Canonical maybeIntern(String id, List<Extension> extension, String value) {
        return id == null && Base.areAllInterned(extension) ? intern(extension, value) : create(id, extension, value);
    }

    @SuppressWarnings("unchecked")
    public static Canonical create(String value) {
        return intern(PersistentVector.EMPTY, requireNonNull(value));
    }

    public static Canonical create(IPersistentMap m) {
        return maybeIntern((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION), (java.lang.String) m.valAt(VALUE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return isBaseInterned();
    }

    public java.lang.String value() {
        return value == null ? null : value.getValue();
    }

    @Override
    public Canonical empty() {
        return EMPTY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Canonical assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(id, extension, (String) val);
        if (key == EXTENSION) return maybeIntern(id, (List<Extension>) (val == null ? PersistentVector.EMPTY : val), value());
        if (key == ID) return maybeIntern((String) val, extension, value());
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Canonical.");
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
        if (hasValue()) {
            sink.putByte((byte) 2);
            Strings.hashInto(value.getValue(), sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Canonical c = (Canonical) o;
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
        return "Canonical{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value=" + (value == null ? null : '\'' + value() + '\'') +
                '}';
    }

    private record InternerKey(List<Extension> extension, String value) {
        private InternerKey {
            requireNonNull(extension);
        }
    }
}
