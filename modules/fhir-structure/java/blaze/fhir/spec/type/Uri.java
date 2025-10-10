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
import java.lang.String;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public sealed abstract class Uri extends PrimitiveElement permits Uri.Normal, Uri.Interned {

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "uri");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Uri ? FHIR_TYPE : this;
        }
    };

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueUri");

    private static final byte HASH_MARKER = 5;

    private Uri(ExtensionData extensionData) {
        super(extensionData);
    }

    private static Uri maybeIntern(ExtensionData extensionData, String value) {
        return extensionData.isInterned() && (value == null || value.length() <= 4)
                ? Interned.intern(extensionData, value)
                : new Normal(extensionData, value);
    }

    public static Uri create(String value) {
        return value.length() <= 4 ? Interned.intern(ExtensionData.EMPTY, value) : new Normal(ExtensionData.EMPTY, value);
    }

    public static Uri create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (String) m.valAt(VALUE));
    }

    public static Uri createForceIntern(String value) {
        return Interned.intern(ExtensionData.EMPTY, requireNonNull(value));
    }

    public static Uri createForceIntern(IPersistentMap m) {
        var extensionData = ExtensionData.fromMap(m);
        var value = (String) m.valAt(VALUE);
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
    public static void hashIntoValue(PrimitiveSink sink, String value) {
        sink.putByte(HASH_MARKER);
        if (value != null) {
            sink.putByte((byte) 2);
            Strings.hashInto(value, sink);
        }
    }

    @Override
    public abstract String value();

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
        return o instanceof Uri that &&
                Objects.equals(extensionData, that.extensionData) &&
                Objects.equals(value(), that.value());
    }

    @Override
    public final int hashCode() {
        return 31 * extensionData.hashCode() + Objects.hashCode(value());
    }

    @Override
    public final String toString() {
        return "Uri{" +
                extensionData +
                ", value=" + (value() == null ? null : '\'' + value() + '\'') +
                '}';
    }

    public static final class Normal extends Uri {

        private static final Normal EMPTY = new Normal(ExtensionData.EMPTY, null);

        private final String value;

        private Normal(ExtensionData extensionData, String value) {
            super(extensionData);
            this.value = value;
        }

        public static Normal create(IPersistentMap m) {
            return new Normal(ExtensionData.fromMap(m), (String) m.valAt(VALUE));
        }

        @Override
        public String value() {
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
        public Uri assoc(Object key, Object val) {
            if (key == VALUE) return maybeIntern(extensionData, (String) val);
            if (key == EXTENSION) return maybeIntern(extensionData.withExtension(val), value);
            if (key == ID) return maybeIntern(extensionData.withId(val), value);
            return this;
        }

        @Override
        public Uri withMeta(IPersistentMap meta) {
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

    public static final class Interned extends Uri {

        private static final Interned EMPTY = new Interned(ExtensionData.EMPTY, null);
        private static final Interner<InternerKey, Interned> INTERNER = Interners.weakInterner(k -> create(k.extensionData, k.value));

        private final SerializedString value;

        private Interned(ExtensionData extensionData, SerializedString value) {
            super(extensionData);
            this.value = value;
        }

        private static Interned create(ExtensionData extensionData, String value) {
            return new Interned(extensionData, value == null ? null : new SerializedString(value));
        }

        private static Uri maybeIntern(ExtensionData extensionData, String value) {
            return extensionData.isInterned() ? intern(extensionData, value) : new Normal(extensionData, value);
        }

        private static Interned intern(ExtensionData extensionData, String value) {
            return INTERNER.intern(new InternerKey(extensionData, value));
        }

        public static Interned create(IPersistentMap m) {
            var extensionData = ExtensionData.fromMap(m);
            var value = (String) m.valAt(VALUE);
            if (extensionData.isInterned()) return intern(extensionData, value);
            throw new IllegalArgumentException("Can't create an interned FHIR.Uri using non-interned extension data.");
        }

        @Override
        public boolean isInterned() {
            return true;
        }

        @Override
        public String value() {
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
        public Uri assoc(Object key, Object val) {
            if (key == VALUE) return maybeIntern(extensionData, (String) val);
            if (key == EXTENSION) return maybeIntern(extensionData.withExtension(val), value());
            if (key == ID) return maybeIntern(extensionData.withId(val), value());
            return this;
        }

        @Override
        public Uri withMeta(IPersistentMap meta) {
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

    private record InternerKey(ExtensionData extensionData, String value) {
        private InternerKey {
            requireNonNull(extensionData);
        }
    }
}
