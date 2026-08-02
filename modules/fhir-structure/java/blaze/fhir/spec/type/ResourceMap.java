package blaze.fhir.spec.type;

import clojure.lang.ILookupThunk;
import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import clojure.lang.RT;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * A value of one of the three FHIR resource types that are no domain resources,
 * namely {@code Bundle}, {@code Parameters} and {@code Binary}.
 * <p>
 * A resource is no {@code Element}, so this class can't extend
 * {@link AbstractElement}. Its element order is {@code id, meta, implicitRules,
 * language}, while {@link AbstractElement} writes {@code extension} right after
 * {@code id}. Writing the leading properties in declaration order produces the
 * canonical order directly.
 * <p>
 * Careful: this class carries two things called meta. The FHIR {@code meta}
 * element is {@link #fhirMeta} and is reachable as {@code valAt(:meta)}, while
 * the Clojure metadata is {@link #objMeta} and is reachable as {@code meta()}.
 * They are unrelated.
 */
public sealed class ResourceMap implements FhirMap permits DomainResourceMap {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - id reference
     * 4 or 8 byte - FHIR meta reference
     * 4 or 8 byte - implicitRules reference
     * 4 or 8 byte - language reference
     * 4 or 8 byte - Clojure metadata reference
     * 4 or 8 byte - metadata reference
     * 4 or 8 byte - values reference
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 7 * MEM_SIZE_REFERENCE + 7) & ~7;

    static final Keyword META = RT.keyword(null, "meta");
    static final Keyword IMPLICIT_RULES = RT.keyword(null, "implicitRules");
    static final Keyword LANGUAGE = RT.keyword(null, "language");

    private static final ILookupThunk ID_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ResourceMap r ? r.id : this;
        }
    };

    /**
     * The thunk of the FHIR {@code meta} element, never of the Clojure
     * metadata.
     */
    private static final ILookupThunk META_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ResourceMap r ? r.fhirMeta : this;
        }
    };

    private static final ILookupThunk IMPLICIT_RULES_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ResourceMap r ? r.implicitRules : this;
        }
    };

    private static final ILookupThunk LANGUAGE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ResourceMap r ? r.language : this;
        }
    };

    private static final SerializableString FIELD_NAME_RESOURCE_TYPE = new SerializedString("resourceType");
    private static final SerializableString FIELD_NAME_ID = FieldName.of("id").normal();
    private static final FieldName FIELD_NAME_META = FieldName.of("meta");
    private static final FieldName FIELD_NAME_IMPLICIT_RULES = FieldName.of("implicitRules");
    private static final FieldName FIELD_NAME_LANGUAGE = FieldName.of("language");

    final String id;

    /**
     * The leading properties are typed, the same way the properties of the
     * complex types with a Java implementation are. That lets
     * {@link #serializeLeading} write them without any dispatch and rejects a
     * wrong type on `assoc` instead of on write.
     */
    final Meta fhirMeta;
    final Uri implicitRules;
    final Code language;

    final IPersistentMap objMeta;
    final TypeMetadata metadata;
    final Object[] values;

    ResourceMap(String id, Meta fhirMeta, Uri implicitRules, Code language, IPersistentMap objMeta,
                TypeMetadata metadata, Object[] values) {
        this.id = id;
        this.fhirMeta = fhirMeta;
        this.implicitRules = implicitRules;
        this.language = language;
        this.objMeta = objMeta;
        this.metadata = requireNonNull(metadata);
        this.values = requireNonNull(values);
    }

    public static ResourceMap create(IPersistentMap m) {
        return (ResourceMap) TypeMetadata.Registry.create(m);
    }

    @Override
    public final TypeMetadata metadata() {
        return metadata;
    }

    @Override
    public Object propertyAt(int index) {
        return switch (index) {
            case 0 -> id;
            case 1 -> fhirMeta;
            case 2 -> implicitRules;
            case 3 -> language;
            default -> values[index - 4];
        };
    }

    @Override
    public final Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return metadata.type();
        int property = metadata.property(key);
        return property < 0 ? notFound : propertyAt(property);
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        int slot = metadata.slot(key);
        if (slot >= 0) return metadata.thunk(slot);
        if (key == FHIR_TYPE_KEY) return metadata.typeThunk();
        if (key == ID) return ID_LOOKUP_THUNK;
        if (key == META) return META_LOOKUP_THUNK;
        if (key == IMPLICIT_RULES) return IMPLICIT_RULES_LOOKUP_THUNK;
        if (key == LANGUAGE) return LANGUAGE_LOOKUP_THUNK;
        return FhirMap.super.getLookupThunk(key);
    }

    @Override
    public ResourceMap assoc(Object key, Object val) {
        int slot = metadata.slot(key);
        if (slot >= 0) {
            var values = this.values.clone();
            values[slot] = val;
            return new ResourceMap(id, fhirMeta, implicitRules, language, objMeta, metadata, values);
        }
        if (key == ID) return new ResourceMap((String) val, fhirMeta, implicitRules, language, objMeta, metadata, values);
        if (key == META) return new ResourceMap(id, (Meta) val, implicitRules, language, objMeta, metadata, values);
        if (key == IMPLICIT_RULES)
            return new ResourceMap(id, fhirMeta, (Uri) val, language, objMeta, metadata, values);
        if (key == LANGUAGE) return new ResourceMap(id, fhirMeta, implicitRules, (Code) val, objMeta, metadata, values);
        return this;
    }

    @Override
    public ResourceMap empty() {
        return new ResourceMap(null, null, null, null, objMeta, metadata, new Object[values.length]);
    }

    @Override
    public final IPersistentMap meta() {
        return objMeta;
    }

    @Override
    public ResourceMap withMeta(IPersistentMap meta) {
        return new ResourceMap(id, fhirMeta, implicitRules, language, meta, metadata, values);
    }

    @Override
    public final int count() {
        return metadata.count(this);
    }

    @Override
    public final ISeq seq() {
        return metadata.seq(this);
    }

    @Override
    public final Iterator<Map.Entry<Object, Object>> iterator() {
        return metadata.iterator(this);
    }

    @Override
    public final Stream<PersistentVector> references() {
        return metadata.references(this);
    }

    @Override
    public final void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        generator.writeFieldName(FIELD_NAME_RESOURCE_TYPE);
        generator.writeString(metadata.resourceType());
        serializeLeading(generator);
        metadata.serializeValues(generator, values);
        generator.writeEndObject();
    }

    /**
     * Writes the leading properties, which are held in explicit fields instead
     * of the values array.
     * <p>
     * The field types are what makes this fast: {@link Meta} and {@link Code}
     * are final and {@link Uri} is sealed, so the JIT can bind each call
     * statically, while a property handler would leave a megamorphic call to
     * {@code serializeAsJsonValue} behind.
     * <p>
     * Overridden by {@link DomainResourceMap}, which appends its four
     * additional ones. Only two implementations, so this call is still inlined.
     */
    void serializeLeading(JsonGenerator generator) throws IOException {
        if (id != null) {
            generator.writeFieldName(FIELD_NAME_ID);
            generator.writeString(id);
        }
        if (fhirMeta != null) {
            fhirMeta.serializeJsonField(generator, FIELD_NAME_META);
        }
        if (implicitRules != null) {
            implicitRules.serializeJsonField(generator, FIELD_NAME_IMPLICIT_RULES);
        }
        if (language != null) {
            language.serializeJsonField(generator, FIELD_NAME_LANGUAGE);
        }
    }

    @Override
    public final void serializeJsonPrimitiveExtension(JsonGenerator generator) {
        throw new UnsupportedOperationException("A resource is no primitive extension.");
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public final void hashInto(PrimitiveSink sink) {
        metadata.hashInto(this, sink);
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + memSizeLeading() + Base.memSizeObjectArray(values.length) +
                metadata.memSizeProperties(this);
    }

    final int memSizeLeading() {
        return (id == null ? 0 : blaze.fhir.spec.type.system.Strings.memSize(id)) + Base.memSize(fhirMeta) +
                Base.memSize(implicitRules) + Base.memSize(language);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof ResourceMap that && getClass() == that.getClass() && equalsLeading(that) &&
                Arrays.equals(values, that.values);
    }

    final boolean equalsLeading(ResourceMap that) {
        return metadata == that.metadata && java.util.Objects.equals(id, that.id) &&
                java.util.Objects.equals(fhirMeta, that.fhirMeta) &&
                java.util.Objects.equals(implicitRules, that.implicitRules) &&
                java.util.Objects.equals(language, that.language);
    }

    @Override
    public int hashCode() {
        return 31 * hashCodeLeading() + Arrays.hashCode(values);
    }

    final int hashCodeLeading() {
        int result = java.util.Objects.hashCode(id);
        result = 31 * result + java.util.Objects.hashCode(fhirMeta);
        result = 31 * result + java.util.Objects.hashCode(implicitRules);
        return 31 * result + java.util.Objects.hashCode(language);
    }

    @Override
    public String toString() {
        return metadata.type() + "{id=" + id + ", values=" + Arrays.toString(values) + "}";
    }
}
