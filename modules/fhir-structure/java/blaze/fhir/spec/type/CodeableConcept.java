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

public final class CodeableConcept extends Element {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "CodeableConcept");

    private static final Keyword CODING = Keyword.intern("coding");
    private static final Keyword TEXT = Keyword.intern("text");

    private static final SerializedString FIELD_NAME_CODING = new SerializedString("coding");
    private static final SerializedString FIELD_NAME_TEXT = new SerializedString("text");

    private static final byte HASH_MARKER = 39;

    private final PersistentVector coding;
    private final String text;

    public CodeableConcept(java.lang.String id, PersistentVector extension, PersistentVector coding, String text) {
        super(id, extension);
        this.coding = coding;
        this.text = text;
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public PersistentVector coding() {
        return coding;
    }

    public String text() {
        return text;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == CODING) return coding;
        if (key == TEXT) return text;
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, TEXT, text);
        if (coding != null && !coding.isEmpty()) {
            seq = appendElement(seq, CODING, coding);
        }
        return appendBase(seq);
    }

    @Override
    public void serializeJson(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (coding != null && !coding.isEmpty()) {
            generator.writeFieldName(FIELD_NAME_CODING);
            generator.writeStartArray();
            for (Object c : coding) {
                ((Coding) c).serializeJson(generator);
            }
            generator.writeEndArray();
        }
        if (text != null && text.value() != null) {
            generator.writeFieldName(FIELD_NAME_TEXT);
            text.serializeJson(generator);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        hashIntoBase(sink);
        if (coding != null && !coding.isEmpty()) {
            sink.putByte((byte) 2);
            sink.putByte((byte) 36);
            for (Object c : coding) {
                ((Coding) c).hashInto(sink);
            }
        }
        if (text != null) {
            sink.putByte((byte) 3);
            text.hashInto(sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CodeableConcept that = (CodeableConcept) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(extension, that.extension) &&
                Objects.equals(coding, that.coding) &&
                Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, coding, text);
    }

    @Override
    public java.lang.String toString() {
        return "CodeableConcept{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", coding=" + coding +
                ", text='" + text + '\'' +
                '}';
    }
}
