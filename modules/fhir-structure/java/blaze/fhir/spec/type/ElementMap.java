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
 * A value of one of the nine FHIR types derived from {@code Element}, whose
 * leading properties are {@code id} and {@code extension}.
 * <p>
 * Those are exclusively anonymous elements nested inside a complex type, eight
 * of them inside {@code ElementDefinition} and one inside
 * {@code SubstanceAmount}. Elements nested inside a resource are backbone
 * elements and use {@link BackboneElementMap} instead.
 * <p>
 * This class must not be merged into {@link BackboneElementMap} with an
 * always-empty {@code modifierExtension}, because
 * {@link AbstractBackboneElement#valAt} would answer that key with an empty
 * vector where a plain map answers {@code nil}.
 */
public final class ElementMap extends AbstractElement implements FhirMap {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - metadata reference
     * 4 or 8 byte - values reference
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 3 * MEM_SIZE_REFERENCE + 7) & ~7;

    private final TypeMetadata metadata;
    private final Object[] values;

    ElementMap(ExtensionData extensionData, TypeMetadata metadata, Object[] values) {
        super(extensionData);
        this.metadata = requireNonNull(metadata);
        this.values = requireNonNull(values);
    }

    public static ElementMap create(IPersistentMap m) {
        return (ElementMap) TypeMetadata.Registry.create(m);
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
            default -> values[index - 2];
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
    public ElementMap assoc(Object key, Object val) {
        int slot = metadata.slot(key);
        if (slot >= 0) {
            var values = this.values.clone();
            values[slot] = val;
            return new ElementMap(extensionData, metadata, values);
        }
        if (key == EXTENSION) return new ElementMap(extensionData.withExtension(val), metadata, values);
        if (key == ID) return new ElementMap(extensionData.withId(val), metadata, values);
        return this;
    }

    @Override
    public ElementMap empty() {
        return new ElementMap(ExtensionData.EMPTY.withMeta(meta()), metadata, new Object[values.length]);
    }

    @Override
    public ElementMap withMeta(IPersistentMap meta) {
        return new ElementMap(extensionData.withMeta(meta), metadata, values);
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
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSizeObjectArray(values.length) +
                metadata.memSizeProperties(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof ElementMap that && metadata == that.metadata &&
                extensionData.equals(that.extensionData) && Arrays.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return 31 * extensionData.hashCode() + Arrays.hashCode(values);
    }

    @Override
    public java.lang.String toString() {
        return metadata.type() + "{" + extensionData + ", values=" + Arrays.toString(values) + "}";
    }
}
