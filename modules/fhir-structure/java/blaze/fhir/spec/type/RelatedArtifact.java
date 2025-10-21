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

public final class RelatedArtifact extends AbstractElement implements Complex, ExtensionValue {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "RelatedArtifact");

    private static final Keyword TYPE = Keyword.intern("type");
    private static final Keyword LABEL = Keyword.intern("label");
    private static final Keyword DISPLAY = Keyword.intern("display");
    private static final Keyword CITATION = Keyword.intern("citation");
    private static final Keyword URL = Keyword.intern("url");
    private static final Keyword DOCUMENT = Keyword.intern("document");
    private static final Keyword RESOURCE = Keyword.intern("resource");

    private static final Keyword[] FIELDS = {ID, EXTENSION, TYPE, LABEL, DISPLAY, CITATION, URL, DOCUMENT, RESOURCE};

    private static final FieldName FIELD_NAME_TYPE = FieldName.of("type");
    private static final FieldName FIELD_NAME_LABEL = FieldName.of("label");
    private static final FieldName FIELD_NAME_DISPLAY = FieldName.of("display");
    private static final FieldName FIELD_NAME_CITATION = FieldName.of("citation");
    private static final FieldName FIELD_NAME_URL = FieldName.of("url");
    private static final SerializedString FIELD_NAME_DOCUMENT = new SerializedString("document");
    private static final FieldName FIELD_NAME_RESOURCE = FieldName.of("resource");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueRelatedArtifact");

    private static final byte HASH_MARKER = 54;

    private static final RelatedArtifact EMPTY = new RelatedArtifact(ExtensionData.EMPTY, null, null, null, null, null, null, null);

    private final Code type;
    private final String label;
    private final String display;
    private final Markdown citation;
    private final Url url;
    private final Attachment document;
    private final Canonical resource;

    private RelatedArtifact(ExtensionData extensionData, Code type, String label, String display, Markdown citation,
                            Url url, Attachment document, Canonical resource) {
        super(extensionData);
        this.type = type;
        this.label = label;
        this.display = display;
        this.citation = citation;
        this.url = url;
        this.document = document;
        this.resource = resource;
    }

    public static RelatedArtifact create(IPersistentMap m) {
        return new RelatedArtifact(ExtensionData.fromMap(m), (Code) m.valAt(TYPE), (String) m.valAt(LABEL),
                (String) m.valAt(DISPLAY), (Markdown) m.valAt(CITATION), (Url) m.valAt(URL),
                (Attachment) m.valAt(DOCUMENT), (Canonical) m.valAt(RESOURCE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public Code type() {
        return type;
    }

    public String label() {
        return label;
    }

    public String display() {
        return display;
    }

    public Markdown citation() {
        return citation;
    }

    public Url url() {
        return url;
    }

    public Attachment document() {
        return document;
    }

    public Canonical resource() {
        return resource;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == TYPE) return type;
        if (key == LABEL) return label;
        if (key == DISPLAY) return display;
        if (key == CITATION) return citation;
        if (key == URL) return url;
        if (key == DOCUMENT) return document;
        if (key == RESOURCE) return resource;
        return extensionData.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, RESOURCE, resource);
        seq = appendElement(seq, DOCUMENT, document);
        seq = appendElement(seq, URL, url);
        seq = appendElement(seq, CITATION, citation);
        seq = appendElement(seq, DISPLAY, display);
        seq = appendElement(seq, LABEL, label);
        seq = appendElement(seq, TYPE, type);
        return extensionData.append(seq);
    }

    @Override
    public RelatedArtifact empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public RelatedArtifact assoc(Object key, Object val) {
        if (key == TYPE)
            return new RelatedArtifact(extensionData, (Code) val, label, display, citation, url, document, resource);
        if (key == LABEL)
            return new RelatedArtifact(extensionData, type, (String) val, display, citation, url, document, resource);
        if (key == DISPLAY)
            return new RelatedArtifact(extensionData, type, label, (String) val, citation, url, document, resource);
        if (key == CITATION)
            return new RelatedArtifact(extensionData, type, label, display, (Markdown) val, url, document, resource);
        if (key == URL)
            return new RelatedArtifact(extensionData, type, label, display, citation, (Url) val, document, resource);
        if (key == DOCUMENT)
            return new RelatedArtifact(extensionData, type, label, display, citation, url, (Attachment) val, resource);
        if (key == RESOURCE)
            return new RelatedArtifact(extensionData, type, label, display, citation, url, document, (Canonical) val);
        if (key == EXTENSION)
            return new RelatedArtifact(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), type, label, display, citation, url, document, resource);
        if (key == ID)
            return new RelatedArtifact(extensionData.withId((java.lang.String) val), type, label, display, citation, url, document, resource);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.RelatedArtifact.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (type != null) {
            type.serializeAsJsonProperty(generator, FIELD_NAME_TYPE);
        }
        if (label != null) {
            label.serializeAsJsonProperty(generator, FIELD_NAME_LABEL);
        }
        if (display != null) {
            display.serializeAsJsonProperty(generator, FIELD_NAME_DISPLAY);
        }
        if (citation != null) {
            citation.serializeAsJsonProperty(generator, FIELD_NAME_CITATION);
        }
        if (url != null) {
            url.serializeAsJsonProperty(generator, FIELD_NAME_URL);
        }
        if (document != null) {
            generator.writeFieldName(FIELD_NAME_DOCUMENT);
            document.serializeAsJsonValue(generator);
        }
        if (resource != null) {
            resource.serializeAsJsonProperty(generator, FIELD_NAME_RESOURCE);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (type != null) {
            sink.putByte((byte) 2);
            type.hashInto(sink);
        }
        if (label != null) {
            sink.putByte((byte) 3);
            label.hashInto(sink);
        }
        if (display != null) {
            sink.putByte((byte) 4);
            display.hashInto(sink);
        }
        if (citation != null) {
            sink.putByte((byte) 5);
            citation.hashInto(sink);
        }
        if (url != null) {
            sink.putByte((byte) 6);
            url.hashInto(sink);
        }
        if (document != null) {
            sink.putByte((byte) 7);
            document.hashInto(sink);
        }
        if (resource != null) {
            sink.putByte((byte) 8);
            resource.hashInto(sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof RelatedArtifact that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(type, that.type) &&
                Objects.equals(label, that.label) &&
                Objects.equals(display, that.display) &&
                Objects.equals(citation, that.citation) &&
                Objects.equals(url, that.url) &&
                Objects.equals(document, that.document) &&
                Objects.equals(resource, that.resource);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(type);
        result = 31 * result + Objects.hashCode(label);
        result = 31 * result + Objects.hashCode(display);
        result = 31 * result + Objects.hashCode(citation);
        result = 31 * result + Objects.hashCode(url);
        result = 31 * result + Objects.hashCode(document);
        result = 31 * result + Objects.hashCode(resource);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "RelatedArtifact{" +
                extensionData +
                ", type=" + type +
                ", label=" + label +
                ", display=" + display +
                ", citation=" + citation +
                ", url=" + url +
                ", document=" + document +
                ", resource=" + resource +
                '}';
    }
}
