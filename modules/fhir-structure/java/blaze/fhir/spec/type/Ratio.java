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

public final class Ratio extends Element implements Complex, ExtensionValue {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Ratio");

    private static final Keyword NUMERATOR = Keyword.intern("numerator");
    private static final Keyword DENOMINATOR = Keyword.intern("denominator");

    private static final Keyword[] FIELDS = {ID, EXTENSION, NUMERATOR, DENOMINATOR};

    private static final SerializedString FIELD_NAME_NUMERATOR = new SerializedString("numerator");
    private static final SerializedString FIELD_NAME_DENOMINATOR = new SerializedString("denominator");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueRatio");

    private static final byte HASH_MARKER = 48;

    private final Quantity numerator;
    private final Quantity denominator;

    public Ratio(java.lang.String id, List<Extension> extension, Quantity numerator, Quantity denominator) {
        super(id, extension);
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public static Ratio create(IPersistentMap m) {
        return new Ratio((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION),
                (Quantity) m.valAt(NUMERATOR), (Quantity) m.valAt(DENOMINATOR));
    }

    public static IPersistentVector getBasis() {
        return RT.vector(Symbol.intern(null, "id"), Symbol.intern(null, "extension"), Symbol.intern(null, "numerator"),
                Symbol.intern(null, "denominator"));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return isBaseInterned() && Base.isInterned(numerator) && Base.isInterned(denominator);
    }

    public Quantity numerator() {
        return numerator;
    }

    public Quantity denominator() {
        return denominator;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == NUMERATOR) return numerator;
        if (key == DENOMINATOR) return denominator;
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    @SuppressWarnings("unchecked")
    public IPersistentCollection empty() {
        return new Ratio(null, PersistentVector.EMPTY, null, null);
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Ratio assoc(Object key, Object val) {
        if (key == ID)
            return new Ratio((java.lang.String) val, extension, numerator, denominator);
        if (key == EXTENSION)
            return new Ratio(id, (List<Extension>) val, numerator, denominator);
        if (key == NUMERATOR)
            return new Ratio(id, extension, (Quantity) val, denominator);
        if (key == DENOMINATOR)
            return new Ratio(id, extension, numerator, (Quantity) val);
        throw new UnsupportedOperationException("The key `''' + key + '''` isn't supported on FHIR.Ratio.");
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, DENOMINATOR, denominator);
        seq = appendElement(seq, NUMERATOR, numerator);
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
        if (numerator != null) {
            generator.writeFieldName(FIELD_NAME_NUMERATOR);
            numerator.serializeAsJsonValue(generator);
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
        if (numerator != null) {
            sink.putByte((byte) 2);
            numerator.hashInto(sink);
        }
        if (denominator != null) {
            sink.putByte((byte) 3);
            denominator.hashInto(sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ratio that = (Ratio) o;
        return Objects.equals(id, that.id) &&
                extension.equals(that.extension) &&
                Objects.equals(numerator, that.numerator) &&
                Objects.equals(denominator, that.denominator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, numerator, denominator);
    }

    @Override
    public java.lang.String toString() {
        return "Ratio{" +
                "id=" + (id == null ? null : "'''" + id + "'''") +
                ", extension=" + extension +
                ", numerator=" + numerator +
                ", denominator=" + denominator +
                '}';
    }
}
