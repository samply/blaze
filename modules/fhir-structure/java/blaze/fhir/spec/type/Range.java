package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Range extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 byte - extension data reference
     * 4 byte - low reference
     * 4 byte - high boolean
     * 1 byte - interned boolean
     * 3 byte - padding
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 16;

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Range");

    private static final Keyword LOW = Keyword.intern("low");
    private static final Keyword HIGH = Keyword.intern("high");

    private static final Keyword[] FIELDS = {ID, EXTENSION, LOW, HIGH};

    private static final SerializedString FIELD_NAME_LOW = new SerializedString("low");
    private static final SerializedString FIELD_NAME_HIGH = new SerializedString("high");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueRange");

    private static final byte HASH_MARKER = 50;

    private static final Interner<ExtensionData, Range> INTERNER = Interners.weakInterner(k -> new Range(k, null, null, true));
    private static final Range EMPTY = new Range(ExtensionData.EMPTY, null, null, true);

    private final Quantity low;
    private final Quantity high;
    private final boolean interned;

    private Range(ExtensionData extensionData, Quantity low, Quantity high, boolean interned) {
        super(extensionData);
        this.low = low;
        this.high = high;
        this.interned = interned;
    }

    private static Range maybeIntern(ExtensionData extensionData, Quantity low, Quantity high) {
        return extensionData.isInterned() && low == null && high == null
                ? INTERNER.intern(extensionData)
                : new Range(extensionData, low, high, false);
    }

    public static Range create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (Quantity) m.valAt(LOW), (Quantity) m.valAt(HIGH));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return interned;
    }

    public Quantity low() {
        return low;
    }

    public Quantity high() {
        return high;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == LOW) return low;
        if (key == HIGH) return high;
        return extensionData.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, HIGH, high);
        seq = appendElement(seq, LOW, low);
        return extensionData.append(seq);
    }

    @Override
    public Range empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Range assoc(Object key, Object val) {
        if (key == LOW) return maybeIntern(extensionData, (Quantity) val, high);
        if (key == HIGH) return maybeIntern(extensionData, low, (Quantity) val);
        if (key == EXTENSION)
            return maybeIntern(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), low, high);
        if (key == ID) return maybeIntern(extensionData.withId((String) val), low, high);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Range.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (low != null) {
            generator.writeFieldName(FIELD_NAME_LOW);
            low.serializeAsJsonValue(generator);
        }
        if (high != null) {
            generator.writeFieldName(FIELD_NAME_HIGH);
            high.serializeAsJsonValue(generator);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (low != null) {
            sink.putByte((byte) 2);
            low.hashInto(sink);
        }
        if (high != null) {
            sink.putByte((byte) 3);
            high.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return isInterned() ? 0 : MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(low) + Base.memSize(high);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Range that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(low, that.low) &&
                Objects.equals(high, that.high);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(low);
        result = 31 * result + Objects.hashCode(high);
        return result;
    }

    @Override
    public String toString() {
        return "Range{" + extensionData + ", low=" + low + ", high=" + high + '}';
    }
}
