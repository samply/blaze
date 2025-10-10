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
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public sealed abstract class String extends PrimitiveElement permits String.Normal, String.Interned {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "string");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueString");

    private static final byte HASH_MARKER = 3;

    private String(java.lang.String id, List<Extension> extension) {
        super(id, extension);
    }

    private static String maybeIntern(java.lang.String id, List<Extension> extension, java.lang.String value) {
        return id == null && Base.areAllInterned(extension) && (value == null || value.length() <= 4)
                ? Interned.intern(extension, value)
                : new Normal(id, extension, value);
    }

    @SuppressWarnings("unchecked")
    public static String create(java.lang.String value) {
        return value.length() <= 4
                ? Interned.intern(PersistentVector.EMPTY, value)
                : new Normal(null, null, value);
    }

    public static String create(IPersistentMap m) {
        return maybeIntern((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION), (java.lang.String) m.valAt(VALUE));
    }

    @SuppressWarnings("unchecked")
    public static String createForceIntern(java.lang.String value) {
        return Interned.intern(PersistentVector.EMPTY, requireNonNull(value));
    }

    public static String createForceIntern(IPersistentMap m) {
        var id = (java.lang.String) m.valAt(ID);
        List<Extension> extension = Base.listFrom(m, EXTENSION);
        var value = (java.lang.String) m.valAt(VALUE);
        return id == null && Base.areAllInterned(extension)
                ? Interned.intern(extension, value)
                : new Normal(id, extension, value);
    }

    /**
     * Creates the hash of a FHIR string with {@code value} without creating the
     * FHIR string first.
     *
     * @param sink  the sink for the hash bytes
     * @param value the string value to hash
     */
    @SuppressWarnings("UnstableApiUsage")
    public static void hashIntoValue(PrimitiveSink sink, java.lang.String value) {
        sink.putByte(HASH_MARKER);
        if (value != null) {
            sink.putByte((byte) 2);
            Strings.hashInto(value, sink);
        }
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public abstract java.lang.String value();

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        hashIntoBase(sink);
        if (hasValue()) {
            sink.putByte((byte) 2);
            Strings.hashInto(value(), sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof String s)) return false;
        return Objects.equals(id, s.id) &&
                Objects.equals(extension, s.extension) &&
                Objects.equals(value(), s.value());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, value());
    }

    @Override
    public java.lang.String toString() {
        return "String{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value=" + (value() == null ? null : '\'' + value() + '\'') +
                '}';
    }

    public static final class Normal extends String {

        private static final Normal EMPTY = new Normal(null, null, null);

        private final java.lang.String value;

        private Normal(java.lang.String id, List<Extension> extension, java.lang.String value) {
            super(id, extension);
            this.value = value;
        }

        public static Normal create(IPersistentMap m) {
            return new Normal((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION), (java.lang.String) m.valAt(VALUE));
        }

        @Override
        public java.lang.String value() {
            return value;
        }

        @Override
        public Normal empty() {
            return EMPTY;
        }

        @Override
        @SuppressWarnings("unchecked")
        public String assoc(Object key, Object val) {
            if (key == VALUE) return maybeIntern(id, extension, (java.lang.String) val);
            if (key == EXTENSION) return maybeIntern(id, (List<Extension>) val, value);
            if (key == ID) return maybeIntern((java.lang.String) val, extension, value);
            throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.String.");
        }

        @Override
        public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
            if (hasValue()) {
                generator.writeString(value);
            } else {
                generator.writeNull();
            }
        }
    }

    public static final class Interned extends String {

        private static final Interned EMPTY = new Interned(null, null, null);
        private static final Interner<InternerKey, Interned> INTERNER = Interners.weakInterner(k -> create(null, k.extension, k.value));

        private final SerializedString value;

        private Interned(java.lang.String id, List<Extension> extension, SerializedString value) {
            super(id, extension);
            this.value = value;
        }

        private static Interned create(java.lang.String id, List<Extension> extension, java.lang.String value) {
            return new Interned(id, extension, value == null ? null : new SerializedString(value));
        }

        private static String maybeIntern(java.lang.String id, List<Extension> extension, java.lang.String value) {
            return id == null && Base.areAllInterned(extension)
                    ? intern(extension, value)
                    : new Normal(id, extension, value);
        }

        private static Interned intern(List<Extension> extension, java.lang.String value) {
            return INTERNER.intern(new InternerKey(extension, value));
        }

        public static Interned create(IPersistentMap m) {
            var id = (java.lang.String) m.valAt(ID);
            List<Extension> extension = Base.listFrom(m, EXTENSION);
            var value = (java.lang.String) m.valAt(VALUE);
            return id == null && Base.areAllInterned(extension)
                    ? intern(extension, value)
                    : create(id, extension, value);
        }

        @Override
        public boolean isInterned() {
            return isBaseInterned();
        }

        @Override
        public java.lang.String value() {
            return value == null ? null : value.getValue();
        }

        @Override
        public Interned empty() {
            return EMPTY;
        }

        @Override
        @SuppressWarnings("unchecked")
        public String assoc(Object key, Object val) {
            if (key == VALUE) return maybeIntern(id, extension, (java.lang.String) val);
            if (key == EXTENSION) return maybeIntern(id, (List<Extension>) val, value());
            if (key == ID) return maybeIntern((java.lang.String) val, extension, value());
            throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.String.");
        }

        @Override
        public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
            if (hasValue()) {
                generator.writeString(value);
            } else {
                generator.writeNull();
            }
        }
    }

    private record InternerKey(List<Extension> extension, java.lang.String value) {
        private InternerKey {
            requireNonNull(extension);
        }
    }
}
