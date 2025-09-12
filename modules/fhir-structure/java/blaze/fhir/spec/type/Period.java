package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Period extends Element implements Complex, ExtensionValue {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Period");

    private static final Keyword START = Keyword.intern("start");
    private static final Keyword END = Keyword.intern("end");

    private static final FieldName FIELD_NAME_START = FieldName.of("start");
    private static final FieldName FIELD_NAME_END = FieldName.of("end");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valuePeriod");

    private static final byte HASH_MARKER = 41;

    private final DateTime start;
    private final DateTime end;

    public Period(java.lang.String id, PersistentVector extension, DateTime start, DateTime end) {
        super(id, extension);
        this.start = start;
        this.end = end;
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public DateTime start() {
        return start;
    }

    public DateTime end() {
        return end;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == START) return start;
        if (key == END) return end;
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, END, end);
        seq = appendElement(seq, START, start);
        return appendBase(seq);
    }

    @Override
    public IPersistentCollection empty() {
        return new Period(null, null, null, null);
    }

    @Override
    public Period assoc(Object key, Object val) {
        if (key == ID) return new Period((java.lang.String) val, extension, start, end);
        if (key == EXTENSION) return new Period(id, (PersistentVector) val, start, end);
        if (key == START) return new Period(id, extension, (DateTime) val, end);
        if (key == END) return new Period(id, extension, start, (DateTime) val);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Period.");
    }

    @Override
    public boolean equiv(Object o) {
        return equals(o);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (start != null) {
            start.serializeAsJsonProperty(generator, FIELD_NAME_START);
        }
        if (end != null) {
            end.serializeAsJsonProperty(generator, FIELD_NAME_END);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        hashIntoBase(sink);
        if (start != null) {
            sink.putByte((byte) 2);
            start.hashInto(sink);
        }
        if (end != null) {
            sink.putByte((byte) 3);
            end.hashInto(sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Period that = (Period) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(extension, that.extension) &&
                Objects.equals(start, that.start) &&
                Objects.equals(end, that.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, start, end);
    }

    @Override
    public java.lang.String toString() {
        return "Period{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", start=" + start +
                ", end=" + end +
                '}';
    }
}
