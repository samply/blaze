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

public final class CodeableConcept extends Element implements Complex, ExtensionValue {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "CodeableConcept");

    private static final Keyword CODING = Keyword.intern("coding");
    private static final Keyword TEXT = Keyword.intern("text");

    private static final Keyword[] FIELDS = {ID, EXTENSION, CODING, TEXT};

    private static final SerializedString FIELD_NAME_CODING = new SerializedString("coding");
    private static final FieldName FIELD_NAME_TEXT = FieldName.of("text");
    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueCodeableConcept");

    private static final byte HASH_MARKER = 39;

    private final List<Coding> coding;
    private final String text;

    @SuppressWarnings("unchecked")
    public CodeableConcept(java.lang.String id, List<Extension> extension, List<Coding> coding, String text) {
        super(id, extension);
        this.coding = coding == null ? PersistentVector.EMPTY : coding;
        this.text = text;
    }

    public static CodeableConcept create(IPersistentMap m) {
        return new CodeableConcept((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION), Base.listFrom(m, CODING),
                (String) m.valAt(TEXT));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return isBaseInterned() && Base.areAllInterned(coding) && Base.isInterned(text);
    }

    public List<Coding> coding() {
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
        if (!coding.isEmpty()) {
            seq = appendElement(seq, CODING, coding);
        }
        return appendBase(seq);
    }

    @Override
    @SuppressWarnings("unchecked")
    public CodeableConcept empty() {
        return new CodeableConcept(null, PersistentVector.EMPTY, PersistentVector.EMPTY, null);
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public CodeableConcept assoc(Object key, Object val) {
        if (key == ID)
            return new CodeableConcept((java.lang.String) val, extension, coding, text);
        if (key == EXTENSION)
            return new CodeableConcept(id, (List<Extension>) val, coding, text);
        if (key == CODING)
            return new CodeableConcept(id, extension, (List<Coding>) val, text);
        if (key == TEXT)
            return new CodeableConcept(id, extension, coding, (String) val);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.CodeableConcept.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (!coding.isEmpty()) {
            generator.writeFieldName(FIELD_NAME_CODING);
            generator.writeStartArray();
            for (Coding coding : coding) {
                coding.serializeAsJsonValue(generator);
            }
            generator.writeEndArray();
        }
        if (text != null) {
            text.serializeAsJsonProperty(generator, FIELD_NAME_TEXT);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        hashIntoBase(sink);
        if (!coding.isEmpty()) {
            sink.putByte((byte) 2);
            sink.putByte((byte) 36);
            for (Coding coding : coding) {
                coding.hashInto(sink);
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
                extension.equals(that.extension) &&
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
                ", text=" + text +
                '}';
    }
}
