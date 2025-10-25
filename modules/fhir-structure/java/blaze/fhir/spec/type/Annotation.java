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

public final class Annotation extends AbstractElement implements Complex, ExtensionValue {

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

    private static final byte HASH_MARKER = 49;

    private static final Annotation EMPTY = new Annotation(ExtensionData.EMPTY, null, null, null);

    private final Base author;
    private final DateTime time;
    private final Markdown text;

    private Annotation(ExtensionData extensionData, Base author, DateTime time, Markdown text) {
        super(extensionData);
        this.author = author;
        this.time = time;
        this.text = text;
    }

    public static Annotation create(IPersistentMap m) {
        return new Annotation(ExtensionData.fromMap(m), (Base) m.valAt(AUTHOR), (DateTime) m.valAt(TIME),
                (Markdown) m.valAt(TEXT));
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
        return extensionData.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, TEXT, text);
        seq = appendElement(seq, TIME, time);
        seq = appendElement(seq, AUTHOR, author);
        return extensionData.append(seq);
    }

    @Override
    public Annotation empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Annotation assoc(Object key, Object val) {
        if (key == ID) return new Annotation(extensionData.withId((java.lang.String) val), author, time, text);
        if (key == EXTENSION)
            return new Annotation(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), author, time, text);
        if (key == AUTHOR) return new Annotation(extensionData, (Base) val, time, text);
        if (key == TIME) return new Annotation(extensionData, author, (DateTime) val, text);
        if (key == TEXT) return new Annotation(extensionData, author, time, (Markdown) val);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Annotation.");
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
        extensionData.hashInto(sink);
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
        return o instanceof Annotation that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(author, that.author) &&
                Objects.equals(time, that.time) &&
                Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(author);
        result = 31 * result + Objects.hashCode(time);
        result = 31 * result + Objects.hashCode(text);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "Annotation{" +
                extensionData +
                ", author=" + author +
                ", time=" + time +
                ", text=" + text +
                '}';
    }
}
