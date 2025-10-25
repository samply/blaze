package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Strings;
import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.*;
import static java.util.Objects.requireNonNull;

public final class ExtensionData {

    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 8;
    private static final SerializedString FIELD_NAME_ID = new SerializedString("id");
    private static final SerializedString FIELD_NAME_EXTENSION = new SerializedString("extension");

    final String id;
    final List<Extension> extension;

    @SuppressWarnings("unchecked")
    public static final ExtensionData EMPTY = new ExtensionData(null, PersistentVector.EMPTY);

    private ExtensionData(String id, List<Extension> extension) {
        this.id = id;
        this.extension = requireNonNull(extension);
    }

    static ExtensionData fromMap(IPersistentMap m) {
        String id = (String) m.valAt(ID);
        List<Extension> extension = Base.listFrom(m, EXTENSION);
        return id == null && extension.isEmpty() ? EMPTY : new ExtensionData(id, extension);
    }

    Object valAt(Object key, Object notFound) {
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    boolean isNotEmpty() {
        return this != EMPTY;
    }

    boolean isInterned() {
        return this == EMPTY || (id == null && Base.areAllInterned(extension));
    }

    public int memSize() {
        return isInterned() ? 0 : MEM_SIZE_OBJECT + (id == null ? 0 : Strings.memSize(id)) + Base.memSize(extension);
    }

    ExtensionData withId(String id) {
        return id == null && extension.isEmpty() ? EMPTY : new ExtensionData(id, extension);
    }

    @SuppressWarnings("unchecked")
    ExtensionData withExtension(List<Extension> extension) {
        return id == null && extension.isEmpty() ? EMPTY : new ExtensionData(id, extension == null ? PersistentVector.EMPTY : extension);
    }

    ISeq append(ISeq seq) {
        seq = appendElement(seq, EXTENSION, extension);
        seq = appendElement(seq, ID, id);
        return seq.count() == 0 ? null : seq;
    }

    void serializeJson(JsonGenerator generator) throws IOException {
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

    @SuppressWarnings("UnstableApiUsage")
    void hashInto(PrimitiveSink sink) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof ExtensionData that &&
                Objects.equals(id, that.id) &&
                extension.equals(that.extension);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(id) + extension.hashCode();
    }

    @Override
    public String toString() {
        return "id=" + (id == null ? null : '\'' + id + '\'') + ", extension=" + extension;
    }
}
