package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Strings;
import clojure.lang.ISeq;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.List;

import static blaze.fhir.spec.type.Base.appendElement;

abstract class Element implements Base {

    private static final SerializedString FIELD_NAME_ID = new SerializedString("id");
    private static final SerializedString FIELD_NAME_EXTENSION = new SerializedString("extension");

    protected final java.lang.String id;
    protected final List<Extension> extension;

    @SuppressWarnings("unchecked")
    Element(java.lang.String id, List<Extension> extension) {
        this.id = id;
        this.extension = extension == null ? PersistentVector.EMPTY : extension;
    }

    public boolean isBaseInterned() {
        return id == null && Base.areAllInterned(extension);
    }

    public boolean isExtended() {
        return id != null || !extension.isEmpty();
    }

    public java.lang.String id() {
        return id;
    }

    public List<Extension> extension() {
        return extension;
    }

    protected ISeq appendBase(ISeq seq) {
        seq = appendElement(seq, EXTENSION, extension);
        seq = appendElement(seq, ID, id);
        return seq.count() == 0 ? null : seq;
    }

    protected void serializeJsonBase(JsonGenerator generator) throws IOException {
        if (id != null) {
            generator.writeFieldName(FIELD_NAME_ID);
            generator.writeString(id);
        }
        if (!extension.isEmpty()) {
            generator.writeFieldName(FIELD_NAME_EXTENSION);
            generator.writeStartArray();
            for (Extension extension : extension) {
                extension.serializeAsJsonValue(generator);
            }
            generator.writeEndArray();
        }
    }

    @Override
    public void serializeJsonPrimitiveExtension(JsonGenerator generator) throws IOException {
        if (id != null || !extension.isEmpty()) {
            generator.writeStartObject();
            serializeJsonBase(generator);
            generator.writeEndObject();
        } else {
            generator.writeNull();
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    protected void hashIntoBase(PrimitiveSink sink) {
        if (id != null) {
            sink.putByte((byte) 0);
            Strings.hashInto(id, sink);
        }
        if (!extension.isEmpty()) {
            sink.putByte((byte) 1);
            sink.putByte((byte) 36);
            for (Extension extension : extension) {
                extension.hashInto(sink);
            }
        }
    }
}
