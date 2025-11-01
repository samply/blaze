package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Attachment extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 byte - extension data reference
     * 4 byte - contentType reference
     * 4 byte - language reference
     * 4 byte - data reference
     * 4 byte - url reference
     * 4 byte - size reference
     * 4 byte - hash reference
     * 4 byte - title reference
     * 4 byte - creation reference
     * 4 byte - padding
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 40;

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Attachment");

    private static final Keyword CONTENT_TYPE = Keyword.intern("contentType");
    private static final Keyword LANGUAGE = Keyword.intern("language");
    private static final Keyword DATA = Keyword.intern("data");
    private static final Keyword URL = Keyword.intern("url");
    private static final Keyword SIZE = Keyword.intern("size");
    private static final Keyword HASH = Keyword.intern("hash");
    private static final Keyword TITLE = Keyword.intern("title");
    private static final Keyword CREATION = Keyword.intern("creation");

    private static final Keyword[] FIELDS = {ID, EXTENSION, CONTENT_TYPE, LANGUAGE, DATA, URL, SIZE, HASH, TITLE, CREATION};

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

    private static final Attachment EMPTY = new Attachment(ExtensionData.EMPTY, null, null, null, null, null, null, null, null);

    private final Code contentType;
    private final Code language;
    private final Base64Binary data;
    private final Url url;
    private final UnsignedInt size;
    private final Base64Binary hash;
    private final String title;
    private final DateTime creation;

    private Attachment(ExtensionData extensionData, Code contentType, Code language, Base64Binary data, Url url,
                       UnsignedInt size, Base64Binary hash, String title, DateTime creation) {
        super(extensionData);
        this.contentType = contentType;
        this.language = language;
        this.data = data;
        this.url = url;
        this.size = size;
        this.hash = hash;
        this.title = title;
        this.creation = creation;
    }

    public static Attachment create(IPersistentMap m) {
        return new Attachment(ExtensionData.fromMap(m), (Code) m.valAt(CONTENT_TYPE), (Code) m.valAt(LANGUAGE),
                (Base64Binary) m.valAt(DATA), (Url) m.valAt(URL), (UnsignedInt) m.valAt(SIZE),
                (Base64Binary) m.valAt(HASH), (String) m.valAt(TITLE), (DateTime) m.valAt(CREATION));
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

    public UnsignedInt sizeValue() {
        return size;
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
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == CONTENT_TYPE) return contentType;
        if (key == LANGUAGE) return language;
        if (key == DATA) return data;
        if (key == URL) return url;
        if (key == SIZE) return size;
        if (key == HASH) return hash;
        if (key == TITLE) return title;
        if (key == CREATION) return creation;
        return extensionData.valAt(key, notFound);
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
        return extensionData.append(seq);
    }

    @Override
    public Attachment empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Attachment assoc(Object key, Object val) {
        if (key == ID)
            return new Attachment(extensionData.withId((java.lang.String) val), contentType, language, data, url, size,
                    hash, title, creation);
        if (key == EXTENSION)
            return new Attachment(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)),
                    contentType, language, data, url, size, hash, title, creation);
        if (key == CONTENT_TYPE)
            return new Attachment(extensionData, (Code) val, language, data, url, size, hash, title, creation);
        if (key == LANGUAGE)
            return new Attachment(extensionData, contentType, (Code) val, data, url, size, hash, title, creation);
        if (key == DATA)
            return new Attachment(extensionData, contentType, language, (Base64Binary) val, url, size, hash, title, creation);
        if (key == URL)
            return new Attachment(extensionData, contentType, language, data, (Url) val, size, hash, title, creation);
        if (key == SIZE)
            return new Attachment(extensionData, contentType, language, data, url, (UnsignedInt) val, hash, title, creation);
        if (key == HASH)
            return new Attachment(extensionData, contentType, language, data, url, size, (Base64Binary) val, title, creation);
        if (key == TITLE)
            return new Attachment(extensionData, contentType, language, data, url, size, hash, (String) val, creation);
        if (key == CREATION)
            return new Attachment(extensionData, contentType, language, data, url, size, hash, title, (DateTime) val);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Attachment.");
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
        extensionData.hashInto(sink);
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
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(contentType) + Base.memSize(language) +
                Base.memSize(data) + Base.memSize(url) + Base.memSize(size) + Base.memSize(hash) + Base.memSize(title) +
                Base.memSize(creation);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Attachment that &&
                extensionData.equals(that.extensionData) &&
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
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(contentType);
        result = 31 * result + Objects.hashCode(language);
        result = 31 * result + Objects.hashCode(data);
        result = 31 * result + Objects.hashCode(url);
        result = 31 * result + Objects.hashCode(size);
        result = 31 * result + Objects.hashCode(hash);
        result = 31 * result + Objects.hashCode(title);
        result = 31 * result + Objects.hashCode(creation);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "Attachment{" +
                extensionData +
                ", contentType=" + contentType +
                ", language=" + language +
                ", data=" + data +
                ", url=" + url +
                ", size=" + size +
                ", hash=" + hash +
                ", title=" + title +
                ", creation=" + creation +
                '}';
    }
}
