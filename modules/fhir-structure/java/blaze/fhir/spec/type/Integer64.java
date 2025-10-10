package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Longs;
import clojure.lang.ILookupThunk;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.RT;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.Objects;

public final class Integer64 extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "integer64");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Integer64 ? FHIR_TYPE : this;
        }
    };

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueInteger64");

    private static final byte HASH_MARKER = 2;

    private static final Integer64 EMPTY = new Integer64(ExtensionData.EMPTY, null);

    private final Long value;

    private Integer64(ExtensionData extensionData, Long value) {
        super(extensionData);
        this.value = value;
    }

    public static Integer64 create(IPersistentMap m) {
        return new Integer64(ExtensionData.fromMap(m), (Long) m.valAt(VALUE));
    }

    public Long value() {
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
    public Integer64 empty() {
        return EMPTY;
    }

    @Override
    public Integer64 assoc(Object key, Object val) {
        if (key == VALUE) return new Integer64(extensionData, (Long) val);
        if (key == EXTENSION) return new Integer64(extensionData.withExtension(val), value);
        if (key == ID) return new Integer64(extensionData.withId(val), value);
        return this;
    }

    @Override
    public Integer64 withMeta(IPersistentMap meta) {
        return new Integer64(extensionData.withMeta(meta), value);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            generator.writeNumber(value);
        } else {
            generator.writeNull();
        }
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (value != null) {
            sink.putByte((byte) 2);
            Longs.hashInto(value, sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Integer64 that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return 31 * extensionData.hashCode() + Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "Integer64{" + extensionData + ", value=" + value + '}';
    }
}
