package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import blaze.fhir.spec.type.system.DateTime;
import clojure.lang.ILookupThunk;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.RT;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.time.OffsetDateTime;
import java.util.Objects;

public final class Instant extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "instant");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Instant ? FHIR_TYPE : this;
        }
    };

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueInstant");

    private static final byte HASH_MARKER = 9;

    private static final Interner<ExtensionData, Instant> INTERNER = Interners.weakInterner(k -> new Instant(k, null));
    private static final Instant EMPTY = new Instant(ExtensionData.EMPTY, null);

    private final OffsetDateTime value;

    private Instant(ExtensionData extensionData, OffsetDateTime value) {
        super(extensionData);
        this.value = value;
    }

    private static Instant maybeIntern(ExtensionData extensionData, OffsetDateTime value) {
        return extensionData.isInterned() && value == null ? INTERNER.intern(extensionData) : new Instant(extensionData, value);
    }

    public static Instant create(OffsetDateTime value) {
        return value == null ? EMPTY : new Instant(ExtensionData.EMPTY, value);
    }

    public static Instant create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (OffsetDateTime) m.valAt(VALUE));
    }

    public OffsetDateTime value() {
        return value;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE_LOOKUP_THUNK : super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE : super.valAt(key, notFound);
    }

    @Override
    public String valueAsString() {
        return value == null ? null : DateTime.toString(value);
    }

    @Override
    public Instant empty() {
        return EMPTY;
    }

    @Override
    public Instant assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, (OffsetDateTime) val);
        if (key == EXTENSION) return maybeIntern(extensionData.withExtension(val), value);
        if (key == ID) return maybeIntern(extensionData.withId(val), value);
        return this;
    }

    @Override
    public Instant withMeta(IPersistentMap meta) {
        return maybeIntern(extensionData.withMeta(meta), value);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (value == null) {
            generator.writeNull();
        } else {
            DateTime.writeTo(value, generator);
        }
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (value != null) {
            sink.putByte((byte) 2);
            DateTime.hashInto(value, sink);
        }
    }

    @Override
    public int memSize() {
        return super.memSize() + DateTime.memSize(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Instant that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return 31 * extensionData.hashCode() + Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "Instant{" + extensionData + ", value=" + value + '}';
    }
}
