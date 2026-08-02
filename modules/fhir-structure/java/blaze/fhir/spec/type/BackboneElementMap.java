package blaze.fhir.spec.type;

import clojure.lang.ILookupThunk;
import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * A value of one of the 464 FHIR types derived from {@code BackboneElement},
 * whose leading properties are {@code id}, {@code extension} and
 * {@code modifierExtension}.
 * <p>
 * That is the largest population of map-represented values by far. Reusing
 * {@link AbstractBackboneElement} takes those three properties out of the
 * values array and shares one interned {@link ExtensionData} instance between
 * all values that have neither an id nor extensions.
 */
public final class BackboneElementMap extends AbstractBackboneElement implements FhirMap {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - modifier extension reference
     * 4 or 8 byte - metadata reference
     * 4 or 8 byte - values reference
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 4 * MEM_SIZE_REFERENCE + 7) & ~7;

    private final TypeMetadata metadata;
    private final Object[] values;

    BackboneElementMap(ExtensionData extensionData, java.util.List<Extension> modifierExtension,
                       TypeMetadata metadata, Object[] values) {
        super(extensionData, modifierExtension);
        this.metadata = requireNonNull(metadata);
        this.values = requireNonNull(values);
    }

    public static BackboneElementMap create(IPersistentMap m) {
        return (BackboneElementMap) TypeMetadata.Registry.create(m);
    }

    @Override
    public TypeMetadata metadata() {
        return metadata;
    }

    @Override
    public Object propertyAt(int index) {
        return switch (index) {
            case 0 -> extensionData.id;
            case 1 -> extensionData.extension.isEmpty() ? null : extensionData.extension;
            case 2 -> modifierExtension.isEmpty() ? null : modifierExtension;
            default -> values[index - 3];
        };
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return metadata.type();
        int slot = metadata.slot(key);
        if (slot >= 0) return values[slot];
        return super.valAt(key, notFound);
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        int slot = metadata.slot(key);
        if (slot >= 0) return metadata.thunk(slot);
        if (key == FHIR_TYPE_KEY) return metadata.typeThunk();
        return super.getLookupThunk(key);
    }

    @Override
    public BackboneElementMap assoc(Object key, Object val) {
        int slot = metadata.slot(key);
        if (slot >= 0) {
            var values = this.values.clone();
            values[slot] = val;
            return new BackboneElementMap(extensionData, modifierExtension, metadata, values);
        }
        if (key == MODIFIER_EXTENSION)
            return new BackboneElementMap(extensionData, Lists.typedNullToEmpty(val, Extension.class), metadata,
                    values);
        if (key == EXTENSION)
            return new BackboneElementMap(extensionData.withExtension(val), modifierExtension, metadata, values);
        if (key == ID) return new BackboneElementMap(extensionData.withId(val), modifierExtension, metadata, values);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public BackboneElementMap empty() {
        return new BackboneElementMap(ExtensionData.EMPTY.withMeta(meta()), PersistentVector.EMPTY, metadata,
                new Object[values.length]);
    }

    @Override
    public BackboneElementMap withMeta(IPersistentMap meta) {
        return new BackboneElementMap(extensionData.withMeta(meta), modifierExtension, metadata, values);
    }

    @Override
    public int count() {
        return metadata.count(this);
    }

    @Override
    public ISeq seq() {
        return metadata.seq(this);
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return metadata.iterator(this);
    }

    @Override
    public Stream<PersistentVector> references() {
        return metadata.references(this);
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        metadata.serializeValues(generator, values);
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        metadata.hashInto(this, sink);
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(modifierExtension) +
                Base.memSizeObjectArray(values.length) + metadata.memSizeProperties(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof BackboneElementMap that && metadata == that.metadata &&
                extensionData.equals(that.extensionData) && modifierExtension.equals(that.modifierExtension) &&
                Arrays.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + modifierExtension.hashCode();
        return 31 * result + Arrays.hashCode(values);
    }

    @Override
    public java.lang.String toString() {
        return metadata.type() + "{" + extensionData + ", modifierExtension=" + modifierExtension +
                ", values=" + Arrays.toString(values) + "}";
    }
}
