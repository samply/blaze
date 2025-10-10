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

public final class RatioRange extends Element implements Complex, ExtensionValue {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "RatioRange");

    private static final Keyword LOW_NUMERATOR = Keyword.intern("lowNumerator");
    private static final Keyword HIGH_NUMERATOR = Keyword.intern("highNumerator");
    private static final Keyword DENOMINATOR = Keyword.intern("denominator");

    private static final Keyword[] FIELDS = {ID, EXTENSION, LOW_NUMERATOR, HIGH_NUMERATOR, DENOMINATOR};

    private static final SerializedString FIELD_NAME_LOW_NUMERATOR = new SerializedString("lowNumerator");
    private static final SerializedString FIELD_NAME_HIGH_NUMERATOR = new SerializedString("highNumerator");
    private static final SerializedString FIELD_NAME_DENOMINATOR = new SerializedString("denominator");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueRatioRange");

    private static final byte HASH_MARKER = 51;

    private final Quantity lowNumerator;
    private final Quantity highNumerator;
    private final Quantity denominator;

    public RatioRange(java.lang.String id, List<Extension> extension, Quantity lowNumerator, Quantity highNumerator, Quantity denominator) {
        super(id, extension);
        this.lowNumerator = lowNumerator;
        this.highNumerator = highNumerator;
        this.denominator = denominator;
    }

    public static RatioRange create(IPersistentMap m) {
        return new RatioRange((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION),
                (Quantity) m.valAt(LOW_NUMERATOR), (Quantity) m.valAt(HIGH_NUMERATOR), (Quantity) m.valAt(DENOMINATOR));
    }

    public static IPersistentVector getBasis() {
        return RT.vector(Symbol.intern(null, "id"), Symbol.intern(null, "extension"), Symbol.intern(null, "lowNumerator"),
                Symbol.intern(null, "highNumerator"), Symbol.intern(null, "denominator"));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return isBaseInterned() && Base.isInterned(lowNumerator) &&
                Base.isInterned(highNumerator) && Base.isInterned(denominator);
    }

    public Quantity lowNumerator() {
        return lowNumerator;
    }

    public Quantity highNumerator() {
        return highNumerator;
    }

    public Quantity denominator() {
        return denominator;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == LOW_NUMERATOR) return lowNumerator;
        if (key == HIGH_NUMERATOR) return highNumerator;
        if (key == DENOMINATOR) return denominator;
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    @SuppressWarnings("unchecked")
    public IPersistentCollection empty() {
        return new RatioRange(null, PersistentVector.EMPTY, null, null, null);
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public RatioRange assoc(Object key, Object val) {
        if (key == ID)
            return new RatioRange((java.lang.String) val, extension, lowNumerator, highNumerator, denominator);
        if (key == EXTENSION)
            return new RatioRange(id, (List<Extension>) val, lowNumerator, highNumerator, denominator);
        if (key == LOW_NUMERATOR)
            return new RatioRange(id, extension, (Quantity) val, highNumerator, denominator);
        if (key == HIGH_NUMERATOR)
            return new RatioRange(id, extension, lowNumerator, (Quantity) val, denominator);
        if (key == DENOMINATOR)
            return new RatioRange(id, extension, lowNumerator, highNumerator, (Quantity) val);
        throw new UnsupportedOperationException("The key `''' + key + '''` isn't supported on FHIR.RatioRange.");
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, DENOMINATOR, denominator);
        seq = appendElement(seq, HIGH_NUMERATOR, highNumerator);
        seq = appendElement(seq, LOW_NUMERATOR, lowNumerator);
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
        if (lowNumerator != null) {
            generator.writeFieldName(FIELD_NAME_LOW_NUMERATOR);
            lowNumerator.serializeAsJsonValue(generator);
        }
        if (highNumerator != null) {
            generator.writeFieldName(FIELD_NAME_HIGH_NUMERATOR);
            highNumerator.serializeAsJsonValue(generator);
        }
        if (denominator != null) {
            generator.writeFieldName(FIELD_NAME_DENOMINATOR);
            denominator.serializeAsJsonValue(generator);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        hashIntoBase(sink);
        if (lowNumerator != null) {
            sink.putByte((byte) 2);
            lowNumerator.hashInto(sink);
        }
        if (highNumerator != null) {
            sink.putByte((byte) 3);
            highNumerator.hashInto(sink);
        }
        if (denominator != null) {
            sink.putByte((byte) 4);
            denominator.hashInto(sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RatioRange that = (RatioRange) o;
        return Objects.equals(id, that.id) &&
                extension.equals(that.extension) &&
                Objects.equals(lowNumerator, that.lowNumerator) &&
                Objects.equals(highNumerator, that.highNumerator) &&
                Objects.equals(denominator, that.denominator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, lowNumerator, highNumerator, denominator);
    }

    @Override
    public java.lang.String toString() {
        return "RatioRange{" +
                "id=" + (id == null ? null : "'''" + id + "'''") +
                ", extension=" + extension +
                ", lowNumerator=" + lowNumerator +
                ", highNumerator=" + highNumerator +
                ", denominator=" + denominator +
                '}';
    }
}
