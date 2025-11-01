package blaze.fhir.spec.type;

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

public final class RatioRange extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 byte - extension data reference
     * 4 byte - lowNumerator reference
     * 4 byte - highNumerator reference
     * 4 byte - denominator reference
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 16;

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

    private static final RatioRange EMPTY = new RatioRange(ExtensionData.EMPTY, null, null, null);

    private final Quantity lowNumerator;
    private final Quantity highNumerator;
    private final Quantity denominator;

    private RatioRange(ExtensionData extensionData, Quantity lowNumerator, Quantity highNumerator, Quantity denominator) {
        super(extensionData);
        this.lowNumerator = lowNumerator;
        this.highNumerator = highNumerator;
        this.denominator = denominator;
    }

    public static RatioRange create(IPersistentMap m) {
        return new RatioRange(ExtensionData.fromMap(m), (Quantity) m.valAt(LOW_NUMERATOR), (Quantity) m.valAt(HIGH_NUMERATOR),
                (Quantity) m.valAt(DENOMINATOR));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return extensionData.isInterned() && Base.isInterned(lowNumerator) && Base.isInterned(highNumerator) &&
                Base.isInterned(denominator);
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
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == LOW_NUMERATOR) return lowNumerator;
        if (key == HIGH_NUMERATOR) return highNumerator;
        if (key == DENOMINATOR) return denominator;
        return extensionData.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, DENOMINATOR, denominator);
        seq = appendElement(seq, HIGH_NUMERATOR, highNumerator);
        seq = appendElement(seq, LOW_NUMERATOR, lowNumerator);
        return extensionData.append(seq);
    }

    @Override
    public RatioRange empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public RatioRange assoc(Object key, Object val) {
        if (key == ID)
            return new RatioRange(extensionData.withId((String) val), lowNumerator, highNumerator, denominator);
        if (key == EXTENSION)
            return new RatioRange(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), lowNumerator, highNumerator, denominator);
        if (key == LOW_NUMERATOR)
            return new RatioRange(extensionData, (Quantity) val, highNumerator, denominator);
        if (key == HIGH_NUMERATOR)
            return new RatioRange(extensionData, lowNumerator, (Quantity) val, denominator);
        if (key == DENOMINATOR)
            return new RatioRange(extensionData, lowNumerator, highNumerator, (Quantity) val);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.RatioRange.");
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
        extensionData.hashInto(sink);
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
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(lowNumerator) + Base.memSize(highNumerator) +
                Base.memSize(denominator);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof RatioRange that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(lowNumerator, that.lowNumerator) &&
                Objects.equals(highNumerator, that.highNumerator) &&
                Objects.equals(denominator, that.denominator);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(lowNumerator);
        result = 31 * result + Objects.hashCode(highNumerator);
        result = 31 * result + Objects.hashCode(denominator);
        return result;
    }

    @Override
    public String toString() {
        return "RatioRange{" +
                extensionData +
                ", lowNumerator=" + lowNumerator +
                ", highNumerator=" + highNumerator +
                ", denominator=" + denominator +
                '}';
    }
}
