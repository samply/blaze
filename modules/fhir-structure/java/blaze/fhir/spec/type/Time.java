package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import blaze.fhir.spec.type.system.Times;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

public final class Time extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "time");

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

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public LocalTime value() {
        return value;
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
    @SuppressWarnings("unchecked")
    public Time assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, (LocalTime) val);
        if (key == EXTENSION)
            return maybeIntern(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), value);
        if (key == ID) return maybeIntern(extensionData.withId((String) val), value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Time.");
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
