package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import blaze.fhir.spec.type.system.DateTimes;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.RT;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.time.temporal.Temporal;
import java.util.Objects;

@SuppressWarnings("DuplicatedCode")
public final class DateTime extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "dateTime");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueDateTime");

    private static final byte HASH_MARKER = 11;

    private static final Interner<ExtensionData, DateTime> INTERNER = Interners.weakInterner(k -> new DateTime(k, null));
    private static final DateTime EMPTY = new DateTime(ExtensionData.EMPTY, null);

    private final Temporal value;

    private DateTime(ExtensionData extensionData, Temporal value) {
        super(extensionData);
        this.value = value;
    }

    private static DateTime maybeIntern(ExtensionData extensionData, Temporal value) {
        return extensionData.isInterned() && value == null ? INTERNER.intern(extensionData) : new DateTime(extensionData, value);
    }

    public static DateTime create(Temporal value) {
        return value == null ? EMPTY : new DateTime(ExtensionData.EMPTY, value);
    }

    public static DateTime create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (Temporal) m.valAt(VALUE));
    }

    public Temporal value() {
        return value;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE : super.valAt(key, notFound);
    }

    @Override
    public String valueAsString() {
        return value == null ? null : DateTimes.toString(value);
    }

    @Override
    public DateTime empty() {
        return EMPTY;
    }

    @Override
    public DateTime assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, (Temporal) val);
        if (key == EXTENSION) return maybeIntern(extensionData.withExtension(val), value);
        if (key == ID) return maybeIntern(extensionData.withId(val), value);
        return this;
    }

    @Override
    public DateTime withMeta(IPersistentMap meta) {
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
            DateTimes.writeTo(value, generator);
        }
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (value != null) {
            sink.putByte((byte) 2);
            DateTimes.hashInto(value, sink);
        }
    }

    @Override
    public int memSize() {
        return super.memSize() + DateTimes.memSize(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof DateTime that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return 31 * extensionData.hashCode() + Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "DateTime{" + extensionData + ", value=" + value + '}';
    }
}
