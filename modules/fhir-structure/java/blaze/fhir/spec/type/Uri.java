package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import blaze.fhir.spec.type.system.Strings;
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

public final class Uri extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "uri");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueUri");

    private static final byte HASH_MARKER = 5;

    private static final Interner<InternerKey, Uri> INTERNER = Interners.weakInterner(k -> create(null, k.extension, k.value));
    private static final Uri EMPTY = new Uri(null, null, null);

    private final SerializedString value;

    private Uri(String id, List<Extension> extension, SerializedString value) {
        super(id, extension);
        this.value = value;
    }

    private static Uri create(String id, List<Extension> extension, String value) {
        return new Uri(id, extension, value == null ? null : new SerializedString(value));
    }

    private static Uri intern(List<Extension> extension, String value) {
        return INTERNER.intern(new InternerKey(extension, value));
    }

    private static Uri maybeIntern(String id, List<Extension> extension, String value) {
        return id == null && Base.areAllInterned(extension) ? intern(extension, value) : create(id, extension, value);
    }

    @SuppressWarnings("unchecked")
    public static Uri create(String value) {
        return intern(PersistentVector.EMPTY, requireNonNull(value));
    }

    public static Uri create(IPersistentMap m) {
        return maybeIntern((String) m.valAt(ID), Base.listFrom(m, EXTENSION), (String) m.valAt(VALUE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return isBaseInterned();
    }

    public String value() {
        return value == null ? null : value.getValue();
    }

    @Override
    public Uri empty() {
        return EMPTY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Uri assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(id, extension, (String) val);
        if (key == EXTENSION) return maybeIntern(id, (List<Extension>) (val == null ? PersistentVector.EMPTY : val), value());
        if (key == ID) return maybeIntern((String) val, extension, value());
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Uri.");
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
        Uri c = (Uri) o;
        return Objects.equals(id, c.id) &&
                Objects.equals(extension, c.extension) &&
                Objects.equals(value, c.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, value);
    }

    @Override
    public String toString() {
        return "Uri{" +
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
