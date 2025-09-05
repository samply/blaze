package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Strings;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;

import static blaze.fhir.spec.type.Base.appendElement;

abstract class Element implements Base {

    protected final java.lang.String id;
    protected final PersistentVector extension;

    Element(java.lang.String id, PersistentVector extension) {
        this.id = id;
        this.extension = extension;
    }

    public java.lang.String id() {
        return id;
    }

    public PersistentVector extension() {
        return extension;
    }

    protected ISeq appendBase(ISeq seq) {
        seq = appendElement(seq, EXTENSION, extension);
        return appendElement(seq, ID, id);
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
                ((Extension) extension).serializeJson(generator);
            }
            generator.writeEndArray();
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
