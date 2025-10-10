package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import blaze.fhir.spec.type.system.Times;
import clojure.lang.ILookupThunk;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.RT;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.time.LocalTime;
import java.util.Objects;

public final class Time extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "time");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Time ? FHIR_TYPE : this;
        }
    };

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueTime");

    private static final byte HASH_MARKER = 12;

    private static final Interner<ExtensionData, Time> INTERNER = Interners.weakInterner(k -> new Time(k, null));
    private static final Time EMPTY = new Time(ExtensionData.EMPTY, null);

    private final LocalTime value;

    private Time(ExtensionData extensionData, LocalTime value) {
        super(extensionData);
        this.value = value;
    }

    private static Time maybeIntern(ExtensionData extensionData, LocalTime value) {
        return extensionData.isInterned() && value == null ? INTERNER.intern(extensionData) : new Time(extensionData, value);
    }

    public static Time create(LocalTime value) {
        return value == null ? EMPTY : new Time(ExtensionData.EMPTY, value);
    }

    public static Time create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (LocalTime) m.valAt(VALUE));
    }

    public LocalTime value() {
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
        return value == null ? null : Times.toString(value);
    }

    @Override
    public Time empty() {
        return EMPTY;
    }

    @Override
    public Time assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, (LocalTime) val);
        if (key == EXTENSION) return maybeIntern(extensionData.withExtension(val), value);
        if (key == ID) return maybeIntern(extensionData.withId(val), value);
        return this;
    }

    @Override
    public Time withMeta(IPersistentMap meta) {
        return maybeIntern(extensionData.withMeta(meta), value);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            generator.writeString(valueAsString());
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
            sink.putByte((byte) 7);
            sink.putInt(value.getHour());
            sink.putInt(value.getMinute());
            sink.putInt(value.getSecond());
            sink.putInt(value.getNano());
        }
    }

    @Override
    public int memSize() {
        return super.memSize() + Times.memSize(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Time that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return 31 * extensionData.hashCode() + Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "Time{" + extensionData + ", value=" + value + '}';
    }
}
