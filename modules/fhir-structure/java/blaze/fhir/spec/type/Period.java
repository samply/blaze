package blaze.fhir.spec.type;

import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.PersistentList;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Period extends Element {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Period");

    private static final Keyword START = Keyword.intern("start");
    private static final Keyword END = Keyword.intern("end");

    private static final SerializedString FIELD_NAME_START = new SerializedString("start");
    private static final SerializedString FIELD_NAME_END = new SerializedString("end");

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
    public void serializeJson(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (start != null && start.value() != null) {
            generator.writeFieldName(FIELD_NAME_START);
            start.serializeJson(generator);
        }
        if (end != null && end.value() != null) {
            generator.writeFieldName(FIELD_NAME_END);
            end.serializeJson(generator);
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
