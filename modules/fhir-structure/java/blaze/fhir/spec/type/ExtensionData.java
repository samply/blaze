package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import blaze.fhir.spec.type.system.Strings;
import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static blaze.fhir.spec.type.Base.*;
import static blaze.fhir.spec.type.Complex.serializeJsonComplexList;
import static java.util.Objects.requireNonNull;

public final class ExtensionData {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - id reference
     * 4 or 8 byte - extension reference
     * 4 or 8 byte - meta reference
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 3 * MEM_SIZE_REFERENCE + 7) & ~7;

    private static final FieldName FIELD_NAME_ID = FieldName.of("id");
    private static final FieldName FIELD_NAME_EXTENSION = FieldName.of("extension");

    final String id;
    final List<Extension> extension;
    final IPersistentMap meta;

    private static final Interner<List<Extension>, ExtensionData> INTERNER = Interners.weakInterner(k -> new ExtensionData(null, k, null));

    @SuppressWarnings("unchecked")
    public static final ExtensionData EMPTY = new ExtensionData(null, PersistentVector.EMPTY, null);

    private ExtensionData(String id, List<Extension> extension, IPersistentMap meta) {
        this.id = id;
        this.extension = requireNonNull(extension);
        this.meta = meta;
    }

    private static ExtensionData maybeIntern(String id, List<Extension> extension, IPersistentMap meta) {
        if (id == null && (meta == null || meta.count() == 0)) {
            if (extension.isEmpty()) return EMPTY;
            if (Base.areAllInterned(extension)) return INTERNER.intern(extension);
        }
        return new ExtensionData(id, extension, meta);
    }

    static ExtensionData fromMap(IPersistentMap m) {
        return maybeIntern((String) m.valAt(ID), Base.listFrom(m, EXTENSION), null);
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
        return this == EMPTY || id == null && Base.areAllInterned(extension) && (meta == null || meta.count() == 0);
    }

    Stream<PersistentVector> references() {
        return extension.stream().flatMap(Extension::references);
    }

    int memSize() {
        return isInterned() ? 0 : MEM_SIZE_OBJECT + (id == null ? 0 : Strings.memSize(id)) + Base.memSize(extension) +
                (meta == null ? 0 : Base.memSize(meta));
    }

    ExtensionData withId(Object id) {
        return maybeIntern((String) id, extension, meta);
    }

    ExtensionData withExtension(Object extension) {
        return maybeIntern(id, Lists.nullToEmpty(extension), meta);
    }

    ExtensionData withMeta(IPersistentMap meta) {
        return maybeIntern(id, extension, meta);
    }

    ISeq append(ISeq seq) {
        seq = appendElement(seq, EXTENSION, extension);
        seq = appendElement(seq, ID, id);
        return seq.count() == 0 ? null : seq;
    }

    void serializeJson(JsonGenerator generator) throws IOException {
        if (id != null) {
            generator.writeFieldName(FIELD_NAME_ID.normal());
            generator.writeString(id);
        }
        if (!extension.isEmpty()) {
            serializeJsonComplexList(extension, generator, FIELD_NAME_EXTENSION.normal());
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
            Base.hashIntoList(extension, sink);
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
