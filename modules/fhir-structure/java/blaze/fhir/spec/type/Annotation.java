package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Annotation extends Element implements Complex, ExtensionValue {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Annotation");

    private static final Keyword AUTHOR = Keyword.intern("author");
    private static final Keyword TIME = Keyword.intern("time");
    private static final Keyword TEXT = Keyword.intern("text");

    private static final Keyword[] FIELDS = {ID, EXTENSION, AUTHOR, TIME, TEXT};

    private static final SerializedString FIELD_NAME_AUTHOR_REFERENCE = new SerializedString("authorReference");
    private static final FieldName FIELD_NAME_AUTHOR_STRING = FieldName.of("authorString");
    private static final FieldName FIELD_NAME_TIME = FieldName.of("time");
    private static final FieldName FIELD_NAME_TEXT = FieldName.of("text");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueAnnotation");

    private static final byte HASH_MARKER = 48;

    private final Base author;
    private final DateTime time;
    private final Markdown text;

    public Annotation(java.lang.String id, PersistentVector extension, Base author, DateTime time, Markdown text) {
        super(id, extension);
        this.author = author;
        this.time = time;
        this.text = text;
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public Base author() {
        return author;
    }

    public DateTime time() {
        return time;
    }

    public Markdown text() {
        return text;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == AUTHOR) return author;
        if (key == TIME) return time;
        if (key == TEXT) return text;
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, TEXT, text);
        seq = appendElement(seq, TIME, time);
        seq = appendElement(seq, AUTHOR, author);
        return appendBase(seq);
    }

    @Override
    public IPersistentCollection empty() {
        return new Annotation(null, null, null, null, null);
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Annotation assoc(Object key, Object val) {
        if (key == ID) return new Annotation((java.lang.String) val, extension, author, time, text);
        if (key == EXTENSION)
            return new Annotation(id, (PersistentVector) val, author, time, text);
        if (key == AUTHOR) return new Annotation(id, extension, (Base) val, time, text);
        if (key == TIME) return new Annotation(id, extension, author, (DateTime) val, text);
        if (key == TEXT) return new Annotation(id, extension, author, time, (Markdown) val);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Annotation.");
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
        if (author != null) {
            if (author instanceof Reference referenceAuthor) {
                generator.writeFieldName(FIELD_NAME_AUTHOR_REFERENCE);
                referenceAuthor.serializeAsJsonValue(generator);
            } else if (author instanceof String stringAuthor) {
                stringAuthor.serializeAsJsonProperty(generator, FIELD_NAME_AUTHOR_STRING);
            }
        }
        if (time != null) {
            time.serializeAsJsonProperty(generator, FIELD_NAME_TIME);
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
        if (author != null) {
            sink.putByte((byte) 2);
            author.hashInto(sink);
        }
        if (time != null) {
            sink.putByte((byte) 3);
            time.hashInto(sink);
        }
        if (text != null) {
            sink.putByte((byte) 4);
            text.hashInto(sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Annotation that = (Annotation) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(extension, that.extension) &&
                Objects.equals(author, that.author) &&
                Objects.equals(time, that.time) &&
                Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, author, time, text);
    }

    @Override
    public java.lang.String toString() {
        return "Annotation{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", author=" + author +
                ", time=" + time +
                ", text=" + text +
                '}';
    }
}
