package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

@SuppressWarnings("DuplicatedCode")
public final class Annotation extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - author reference
     * 4 or 8 byte - time reference
     * 4 or 8 byte - text reference
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 4 * MEM_SIZE_REFERENCE;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "Annotation");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Annotation ? FHIR_TYPE : this;
        }
    };

    private static final Keyword AUTHOR = RT.keyword(null, "author");
    private static final Keyword TIME = RT.keyword(null, "time");
    private static final Keyword TEXT = RT.keyword(null, "text");

    private static final Keyword[] FIELDS = {ID, EXTENSION, AUTHOR, TIME, TEXT};

    private static final FieldName FIELD_NAME_AUTHOR_REFERENCE = FieldName.of("authorReference");
    private static final FieldName FIELD_NAME_AUTHOR_STRING = FieldName.of("authorString");
    private static final FieldName FIELD_NAME_TIME = FieldName.of("time");
    private static final FieldName FIELD_NAME_TEXT = FieldName.of("text");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueAnnotation");

    private static final byte HASH_MARKER = 49;

    private static final Annotation EMPTY = new Annotation(ExtensionData.EMPTY, null, null, null);

    private final Element author;
    private final DateTime time;
    private final Markdown text;

    private Annotation(ExtensionData extensionData, Element author, DateTime time, Markdown text) {
        super(extensionData);
        this.author = author;
        this.time = time;
        this.text = text;
    }

    public static Annotation create(IPersistentMap m) {
        return new Annotation(ExtensionData.fromMap(m), (Element) m.valAt(AUTHOR), (DateTime) m.valAt(TIME),
                (Markdown) m.valAt(TEXT));
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
    public ILookupThunk getLookupThunk(Keyword key) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE_LOOKUP_THUNK : super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == AUTHOR) return author;
        if (key == TIME) return time;
        if (key == TEXT) return text;
        return super.valAt(key, notFound);
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
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Annotation assoc(Object key, Object val) {
        if (key == AUTHOR) return new Annotation(extensionData, (Element) val, time, text);
        if (key == TIME) return new Annotation(extensionData, author, (DateTime) val, text);
        if (key == TEXT) return new Annotation(extensionData, author, time, (Markdown) val);
        if (key == EXTENSION) return new Annotation(extensionData.withExtension(val), author, time, text);
        if (key == ID) return new Annotation(extensionData.withId(val), author, time, text);
        return this;
    }

    @Override
    public Annotation withMeta(IPersistentMap meta) {
        return new Annotation(extensionData.withMeta(meta), author, time, text);
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
            switch (author) {
                case Reference authorReference ->
                        authorReference.serializeJsonField(generator, FIELD_NAME_AUTHOR_REFERENCE);
                case String authorString ->
                        authorString.serializeAsJsonProperty(generator, FIELD_NAME_AUTHOR_STRING);
                default -> {
                }
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
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(author) + Base.memSize(time) + Base.memSize(text);
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
