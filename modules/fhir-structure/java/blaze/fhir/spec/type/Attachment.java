package blaze.fhir.spec.type;

import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.PersistentList;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Attachment extends Element implements Complex, ExtensionValue {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Attachment");

    private static final Keyword CONTENT_TYPE = Keyword.intern("contentType");
    private static final Keyword LANGUAGE = Keyword.intern("language");
    private static final Keyword DATA = Keyword.intern("data");
    private static final Keyword URL = Keyword.intern("url");
    private static final Keyword SIZE = Keyword.intern("size");
    private static final Keyword HASH = Keyword.intern("hash");
    private static final Keyword TITLE = Keyword.intern("title");
    private static final Keyword CREATION = Keyword.intern("creation");

    private static final FieldName FIELD_NAME_CONTENT_TYPE = FieldName.of("contentType");
    private static final FieldName FIELD_NAME_LANGUAGE = FieldName.of("language");
    private static final FieldName FIELD_NAME_DATA = FieldName.of("data");
    private static final FieldName FIELD_NAME_URL = FieldName.of("url");
    private static final FieldName FIELD_NAME_SIZE = FieldName.of("size");
    private static final FieldName FIELD_NAME_HASH = FieldName.of("hash");
    private static final FieldName FIELD_NAME_TITLE = FieldName.of("title");
    private static final FieldName FIELD_NAME_CREATION = FieldName.of("creation");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueAttachment");

    private static final byte HASH_MARKER = 46;

    private final Code contentType;
    private final Code language;
    private final Base64Binary data;
    private final Url url;
    private final UnsignedInt size;
    private final Base64Binary hash;
    private final String title;
    private final DateTime creation;

    public Attachment(java.lang.String id, PersistentVector extension, Code contentType, Code language, Base64Binary data, Url url, UnsignedInt size, Base64Binary hash, String title, DateTime creation) {
        super(id, extension);
        this.contentType = contentType;
        this.language = language;
        this.data = data;
        this.url = url;
        this.size = size;
        this.hash = hash;
        this.title = title;
        this.creation = creation;
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public Code contentType() {
        return contentType;
    }

    public Code language() {
        return language;
    }

    public Base64Binary data() {
        return data;
    }

    public Url url() {
        return url;
    }

    public Base64Binary hash() {
        return hash;
    }

    public String title() {
        return title;
    }

    public DateTime creation() {
        return creation;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == CONTENT_TYPE) return contentType;
        if (key == LANGUAGE) return language;
        if (key == DATA) return data;
        if (key == URL) return url;
        if (key == SIZE) return size;
        if (key == HASH) return hash;
        if (key == TITLE) return title;
        if (key == CREATION) return creation;
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, CREATION, creation);
        seq = appendElement(seq, TITLE, title);
        seq = appendElement(seq, HASH, hash);
        seq = appendElement(seq, SIZE, size);
        seq = appendElement(seq, URL, url);
        seq = appendElement(seq, DATA, data);
        seq = appendElement(seq, LANGUAGE, language);
        seq = appendElement(seq, CONTENT_TYPE, contentType);
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
        if (contentType != null) {
            contentType.serializeAsJsonProperty(generator, FIELD_NAME_CONTENT_TYPE);
        }
        if (language != null) {
            language.serializeAsJsonProperty(generator, FIELD_NAME_LANGUAGE);
        }
        if (data != null) {
            data.serializeAsJsonProperty(generator, FIELD_NAME_DATA);
        }
        if (url != null) {
            url.serializeAsJsonProperty(generator, FIELD_NAME_URL);
        }
        if (size != null) {
            size.serializeAsJsonProperty(generator, FIELD_NAME_SIZE);
        }
        if (hash != null) {
            hash.serializeAsJsonProperty(generator, FIELD_NAME_HASH);
        }
        if (title != null) {
            title.serializeAsJsonProperty(generator, FIELD_NAME_TITLE);
        }
        if (creation != null) {
            creation.serializeAsJsonProperty(generator, FIELD_NAME_CREATION);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        hashIntoBase(sink);
        if (contentType != null) {
            sink.putByte((byte) 2);
            contentType.hashInto(sink);
        }
        if (language != null) {
            sink.putByte((byte) 3);
            language.hashInto(sink);
        }
        if (data != null) {
            sink.putByte((byte) 4);
            data.hashInto(sink);
        }
        if (url != null) {
            sink.putByte((byte) 5);
            url.hashInto(sink);
        }
        if (size != null) {
            sink.putByte((byte) 6);
            size.hashInto(sink);
        }
        if (hash != null) {
            sink.putByte((byte) 7);
            hash.hashInto(sink);
        }
        if (title != null) {
            sink.putByte((byte) 8);
            title.hashInto(sink);
        }
        if (creation != null) {
            sink.putByte((byte) 9);
            creation.hashInto(sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Attachment that = (Attachment) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(extension, that.extension) &&
                Objects.equals(contentType, that.contentType) &&
                Objects.equals(language, that.language) &&
                Objects.equals(data, that.data) &&
                Objects.equals(url, that.url) &&
                Objects.equals(size, that.size) &&
                Objects.equals(hash, that.hash) &&
                Objects.equals(title, that.title) &&
                Objects.equals(creation, that.creation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, contentType, language, data, url, size, hash, title, creation);
    }

    @Override
    public java.lang.String toString() {
        return "Attachment{" +
                "id=" + (id == null ? null : "'" + id + "'") +
                ", extension=" + extension +
                ", contentType=" + contentType +
                ", language=" + language +
                ", data=" + data +
                ", url=" + url +
                ", size=" + size +
                ", hash=" + hash +
                ", title=" + (title == null ? null : "'" + title + "'") +
                ", creation=" + creation +
                '}';
    }
}
