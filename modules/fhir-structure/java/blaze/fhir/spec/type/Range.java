package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Range extends Element implements Complex, ExtensionValue {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Range");

    private static final Keyword LOW = Keyword.intern("low");
    private static final Keyword HIGH = Keyword.intern("high");

    private static final Keyword[] FIELDS = {ID, EXTENSION, LOW, HIGH};

    private static final SerializedString FIELD_NAME_LOW = new SerializedString("low");
    private static final SerializedString FIELD_NAME_HIGH = new SerializedString("high");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueRange");

    private static final byte HASH_MARKER = 50;

    private final Quantity low;
    private final Quantity high;

    public Range(java.lang.String id, List<Extension> extension, Quantity low, Quantity high) {
        super(id, extension);
        this.low = low;
        this.high = high;
    }

    public static Range create(IPersistentMap m) {
        return new Range((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION),
                (Quantity) m.valAt(LOW), (Quantity) m.valAt(HIGH));
    }

    public static IPersistentVector getBasis() {
        return RT.vector(Symbol.intern(null, "id"), Symbol.intern(null, "extension"), Symbol.intern(null, "low"),
                Symbol.intern(null, "high"));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return isBaseInterned() && Base.isInterned(low) && Base.isInterned(high);
    }

    public Quantity low() {
        return low;
    }

    public Quantity high() {
        return high;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == LOW) return low;
        if (key == HIGH) return high;
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    @SuppressWarnings("unchecked")
    public IPersistentCollection empty() {
        return new Range(null, PersistentVector.EMPTY, null, null);
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Range assoc(Object key, Object val) {
        if (key == ID)
            return new Range((java.lang.String) val, extension, low, high);
        if (key == EXTENSION)
            return new Range(id, (List<Extension>) val, low, high);
        if (key == LOW)
            return new Range(id, extension, (Quantity) val, high);
        if (key == HIGH)
            return new Range(id, extension, low, (Quantity) val);
        throw new UnsupportedOperationException("The key `''' + key + '''` isn't supported on FHIR.Range.");
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, HIGH, high);
        seq = appendElement(seq, LOW, low);
        return appendBase(seq);
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
        hashIntoBase(sink);
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Range that = (Range) o;
        return Objects.equals(id, that.id) &&
                extension.equals(that.extension) &&
                Objects.equals(low, that.low) &&
                Objects.equals(high, that.high);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, low, high);
    }

    @Override
    public java.lang.String toString() {
        return "Range{" +
                "id=" + (id == null ? null : "'''" + id + "'''") +
                ", extension=" + extension +
                ", low=" + low +
                ", high=" + high +
                '}';
    }
}
