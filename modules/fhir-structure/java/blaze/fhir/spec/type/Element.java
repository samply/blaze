package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Strings;
import clojure.lang.ISeq;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;

import static blaze.fhir.spec.type.Base.appendElement;

abstract class Element implements Base {

    private static final SerializedString FIELD_NAME_ID = new SerializedString("id");
    private static final SerializedString FIELD_NAME_EXTENSION = new SerializedString("extension");

    protected final java.lang.String id;
    protected final PersistentVector extension;

    Element(java.lang.String id, PersistentVector extension) {
        this.id = id;
        this.extension = extension;
    }

    public boolean isExtended() {
        return id != null || extension != null && !extension.isEmpty();
    }

    public java.lang.String id() {
        return id;
    }

    public PersistentVector extension() {
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
        if (extension != null && !extension.isEmpty()) {
            generator.writeFieldName(FIELD_NAME_EXTENSION);
            generator.writeStartArray();
            for (Object extension : extension) {
                ((Extension) extension).serializeAsJsonValue(generator);
            }
            generator.writeEndArray();
        }
    }

    @Override
    public void serializeJsonPrimitiveExtension(JsonGenerator generator) throws IOException {
        if (id != null || extension != null && !extension.isEmpty()) {
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
        if (extension != null && !extension.isEmpty()) {
            sink.putByte((byte) 1);
            sink.putByte((byte) 36);
            for (Object extension : extension) {
                ((Extension) extension).hashInto(sink);
            }
        }
    }
}
