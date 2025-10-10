package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import blaze.fhir.spec.type.system.Strings;
import clojure.lang.ILookupThunk;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.RT;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public sealed abstract class String extends PrimitiveElement permits String.Normal, String.Interned {

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "string");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof String ? FHIR_TYPE : this;
        }
    };

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueString");

    private static final byte HASH_MARKER = 3;

    private String(ExtensionData extensionData) {
        super(extensionData);
    }

    private static String maybeIntern(ExtensionData extensionData, java.lang.String value) {
        return extensionData.isInterned() && (value == null || value.length() <= 4)
                ? Interned.intern(extensionData, value)
                : new Normal(extensionData, value);
    }

    public static String create(java.lang.String value) {
        return value.length() <= 4 ? Interned.intern(ExtensionData.EMPTY, value) : new Normal(ExtensionData.EMPTY, value);
    }

    public static String create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (java.lang.String) m.valAt(VALUE));
    }

    public static String createForceIntern(java.lang.String value) {
        return Interned.intern(ExtensionData.EMPTY, requireNonNull(value));
    }

    public static String createForceIntern(IPersistentMap m) {
        var extensionData = ExtensionData.fromMap(m);
        var value = (java.lang.String) m.valAt(VALUE);
        return extensionData.isInterned() ? Interned.intern(extensionData, value) : new Normal(extensionData, value);
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
    public abstract java.lang.String value();

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE_LOOKUP_THUNK : super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE : super.valAt(key, notFound);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (hasValue()) {
            sink.putByte((byte) 2);
            Strings.hashInto(value(), sink);
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof String that &&
                Objects.equals(extensionData, that.extensionData) &&
                Objects.equals(value(), that.value());
    }

    @Override
    public final int hashCode() {
        return 31 * extensionData.hashCode() + Objects.hashCode(value());
    }

    @Override
    public final java.lang.String toString() {
        return "String{" +
                extensionData +
                ", value=" + (value() == null ? null : '\'' + value() + '\'') +
                '}';
    }

    public static final class Normal extends String {

        private static final Normal EMPTY = new Normal(ExtensionData.EMPTY, null);

        private final java.lang.String value;

        private Normal(ExtensionData extensionData, java.lang.String value) {
            super(extensionData);
            this.value = value;
        }

        public static Normal create(IPersistentMap m) {
            return new Normal(ExtensionData.fromMap(m), (java.lang.String) m.valAt(VALUE));
        }

        @Override
        public java.lang.String value() {
            return value;
        }

        @Override
        public Object valAt(Object key, Object notFound) {
            return key == FHIR_TYPE_KEY ? FHIR_TYPE : super.valAt(key, notFound);
        }

        @Override
        public Normal empty() {
            return EMPTY;
        }

        @Override
        public String assoc(Object key, Object val) {
            if (key == VALUE) return maybeIntern(extensionData, (java.lang.String) val);
            if (key == EXTENSION) return maybeIntern(extensionData.withExtension(val), value);
            if (key == ID) return maybeIntern(extensionData.withId(val), value);
            return this;
        }

        @Override
        public String withMeta(IPersistentMap meta) {
            return maybeIntern(extensionData.withMeta(meta), value);
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
        public int memSize() {
            return super.memSize() + Strings.memSize(value);
        }
    }

    public static final class Interned extends String {

        private static final Interned EMPTY = new Interned(ExtensionData.EMPTY, null);
        private static final Interner<InternerKey, Interned> INTERNER = Interners.weakInterner(k -> create(k.extensionData, k.value));

        private final SerializedString value;

        private Interned(ExtensionData extensionData, SerializedString value) {
            super(extensionData);
            this.value = value;
        }

        private static Interned create(ExtensionData extensionData, java.lang.String value) {
            return new Interned(extensionData, value == null ? null : new SerializedString(value));
        }

        private static String maybeIntern(ExtensionData extensionData, java.lang.String value) {
            return extensionData.isInterned() ? intern(extensionData, value) : new Normal(extensionData, value);
        }

        private static Interned intern(ExtensionData extensionData, java.lang.String value) {
            return INTERNER.intern(new InternerKey(extensionData, value));
        }

        public static Interned create(IPersistentMap m) {
            var extensionData = ExtensionData.fromMap(m);
            var value = (java.lang.String) m.valAt(VALUE);
            if (extensionData.isInterned()) return intern(extensionData, value);
            throw new IllegalArgumentException("Can't create an interned FHIR.String using non-interned extension data.");
        }

        @Override
        public boolean isInterned() {
            return true;
        }

        @Override
        public java.lang.String value() {
            return value == null ? null : value.getValue();
        }

        @Override
        public Object valAt(Object key, Object notFound) {
            return key == FHIR_TYPE_KEY ? FHIR_TYPE : super.valAt(key, notFound);
        }

        @Override
        public Interned empty() {
            return EMPTY;
        }

        @Override
        public String assoc(Object key, Object val) {
            if (key == VALUE) return maybeIntern(extensionData, (java.lang.String) val);
            if (key == EXTENSION) return maybeIntern(extensionData.withExtension(val), value());
            if (key == ID) return maybeIntern(extensionData.withId(val), value());
            return this;
        }

        @Override
        public String withMeta(IPersistentMap meta) {
            return maybeIntern(extensionData.withMeta(meta), value());
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
        public int memSize() {
            return 0;
        }
    }

    private record InternerKey(ExtensionData extensionData, java.lang.String value) {
        private InternerKey {
            requireNonNull(extensionData);
        }
    }
}
