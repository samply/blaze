package blaze.fhir.spec.type;

import clojure.lang.ILookupThunk;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import clojure.lang.RT;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;

import java.io.IOException;
import java.lang.String;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Complex.serializeJsonComplexList;
import static java.util.Objects.requireNonNull;

/**
 * A value of one of the 143 FHIR resource types derived from
 * {@code DomainResource}, adding {@code text}, {@code contained},
 * {@code extension} and {@code modifierExtension} to the leading properties of
 * {@link ResourceMap}.
 */
public final class DomainResourceMap extends ResourceMap {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - id reference
     * 4 or 8 byte - FHIR meta reference
     * 4 or 8 byte - implicitRules reference
     * 4 or 8 byte - language reference
     * 4 or 8 byte - text reference
     * 4 or 8 byte - contained reference
     * 4 or 8 byte - extension reference
     * 4 or 8 byte - modifierExtension reference
     * 4 or 8 byte - Clojure metadata reference
     * 4 or 8 byte - metadata reference
     * 4 or 8 byte - values reference
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 11 * MEM_SIZE_REFERENCE + 7) & ~7;

    private static final Keyword TEXT = RT.keyword(null, "text");
    private static final Keyword CONTAINED = RT.keyword(null, "contained");
    private static final Keyword MODIFIER_EXTENSION = RT.keyword(null, "modifierExtension");

    private static final FieldName FIELD_NAME_TEXT = FieldName.of("text");
    private static final SerializableString FIELD_NAME_CONTAINED = FieldName.of("contained").normal();
    private static final SerializableString FIELD_NAME_EXTENSION = FieldName.of("extension").normal();
    private static final SerializableString FIELD_NAME_MODIFIER_EXTENSION = FieldName.of("modifierExtension").normal();

    private static final ILookupThunk TEXT_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof DomainResourceMap r ? r.text : this;
        }
    };

    /**
     * The three list thunks report an empty list as absent, the same way
     * {@link #propertyAt} does, because a plain map answers {@code nil} for a
     * repeating property it doesn't contain.
     */
    private static final ILookupThunk CONTAINED_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof DomainResourceMap r ? nullIfEmpty(r.contained) : this;
        }
    };

    private static final ILookupThunk EXTENSION_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof DomainResourceMap r ? nullIfEmpty(r.extension) : this;
        }
    };

    private static final ILookupThunk MODIFIER_EXTENSION_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof DomainResourceMap r ? nullIfEmpty(r.modifierExtension) : this;
        }
    };

    private static List<?> nullIfEmpty(List<?> list) {
        return list.isEmpty() ? null : list;
    }

    /**
     * Typed for the same reason the leading properties of {@link ResourceMap}
     * are, which also rejects a wrong element type of the three lists on
     * `assoc` instead of on write.
     * <p>
     * The three lists are empty rather than {@code null} when absent, the same
     * way every other complex type represents a repeating property.
     * {@link #propertyAt} reports an empty one as absent, because a plain map
     * wouldn't contain the key at all.
     */
    private final Narrative text;
    private final List<Complex> contained;
    private final List<Extension> extension;
    private final List<Extension> modifierExtension;

    DomainResourceMap(String id, Meta fhirMeta, Uri implicitRules, Code language, Narrative text,
                      List<Complex> contained, List<Extension> extension, List<Extension> modifierExtension,
                      IPersistentMap objMeta, TypeMetadata metadata, Object[] values) {
        super(id, fhirMeta, implicitRules, language, objMeta, metadata, values);
        this.text = text;
        this.contained = requireNonNull(contained);
        this.extension = requireNonNull(extension);
        this.modifierExtension = requireNonNull(modifierExtension);
    }

    public static DomainResourceMap create(IPersistentMap m) {
        return (DomainResourceMap) TypeMetadata.Registry.create(m);
    }

    @Override
    public Object propertyAt(int index) {
        return switch (index) {
            case 0 -> id;
            case 1 -> fhirMeta;
            case 2 -> implicitRules;
            case 3 -> language;
            case 4 -> text;
            case 5 -> nullIfEmpty(contained);
            case 6 -> nullIfEmpty(extension);
            case 7 -> nullIfEmpty(modifierExtension);
            default -> values[index - 8];
        };
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        int slot = metadata.slot(key);
        if (slot >= 0) return metadata.thunk(slot);
        if (key == TEXT) return TEXT_LOOKUP_THUNK;
        if (key == CONTAINED) return CONTAINED_LOOKUP_THUNK;
        if (key == EXTENSION) return EXTENSION_LOOKUP_THUNK;
        if (key == MODIFIER_EXTENSION) return MODIFIER_EXTENSION_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public DomainResourceMap assoc(Object key, Object val) {
        int slot = metadata.slot(key);
        if (slot >= 0) {
            var values = this.values.clone();
            values[slot] = val;
            return new DomainResourceMap(id, fhirMeta, implicitRules, language, text, contained, extension,
                    modifierExtension, objMeta, metadata, values);
        }
        if (key == ID)
            return new DomainResourceMap((String) val, fhirMeta, implicitRules, language, text, contained, extension,
                    modifierExtension, objMeta, metadata, values);
        if (key == META)
            return new DomainResourceMap(id, (Meta) val, implicitRules, language, text, contained, extension,
                    modifierExtension, objMeta, metadata, values);
        if (key == IMPLICIT_RULES)
            return new DomainResourceMap(id, fhirMeta, (Uri) val, language, text, contained, extension,
                    modifierExtension, objMeta, metadata, values);
        if (key == LANGUAGE)
            return new DomainResourceMap(id, fhirMeta, implicitRules, (Code) val, text, contained, extension,
                    modifierExtension, objMeta, metadata, values);
        if (key == TEXT)
            return new DomainResourceMap(id, fhirMeta, implicitRules, language, (Narrative) val, contained, extension,
                    modifierExtension, objMeta, metadata, values);
        if (key == CONTAINED)
            return new DomainResourceMap(id, fhirMeta, implicitRules, language, text,
                    Lists.typedNullToEmpty(val, Complex.class), extension, modifierExtension, objMeta, metadata,
                    values);
        if (key == EXTENSION)
            return new DomainResourceMap(id, fhirMeta, implicitRules, language, text, contained,
                    Lists.typedNullToEmpty(val, Extension.class), modifierExtension, objMeta, metadata, values);
        if (key == MODIFIER_EXTENSION)
            return new DomainResourceMap(id, fhirMeta, implicitRules, language, text, contained, extension,
                    Lists.typedNullToEmpty(val, Extension.class), objMeta, metadata, values);
        return this;
    }

    @Override
    void serializeLeading(JsonGenerator generator) throws IOException {
        super.serializeLeading(generator);
        if (text != null) {
            text.serializeJsonField(generator, FIELD_NAME_TEXT);
        }
        if (!contained.isEmpty()) {
            serializeJsonComplexList(contained, generator, FIELD_NAME_CONTAINED);
        }
        if (!extension.isEmpty()) {
            serializeJsonComplexList(extension, generator, FIELD_NAME_EXTENSION);
        }
        if (!modifierExtension.isEmpty()) {
            serializeJsonComplexList(modifierExtension, generator, FIELD_NAME_MODIFIER_EXTENSION);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public DomainResourceMap empty() {
        return new DomainResourceMap(null, null, null, null, null, PersistentVector.EMPTY, PersistentVector.EMPTY,
                PersistentVector.EMPTY, objMeta, metadata, new Object[values.length]);
    }

    @Override
    public DomainResourceMap withMeta(IPersistentMap meta) {
        return new DomainResourceMap(id, fhirMeta, implicitRules, language, text, contained, extension,
                modifierExtension, meta, metadata, values);
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + memSizeLeading() + Base.memSize(text) + Base.memSize(contained) +
                Base.memSize(extension) + Base.memSize(modifierExtension) + Base.memSizeObjectArray(values.length) +
                metadata.memSizeProperties(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof DomainResourceMap that && equalsLeading(that) &&
                Objects.equals(text, that.text) && Objects.equals(contained, that.contained) &&
                Objects.equals(extension, that.extension) &&
                Objects.equals(modifierExtension, that.modifierExtension) && Arrays.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        int result = hashCodeLeading();
        result = 31 * result + Objects.hashCode(text);
        result = 31 * result + Objects.hashCode(contained);
        result = 31 * result + Objects.hashCode(extension);
        result = 31 * result + Objects.hashCode(modifierExtension);
        return 31 * result + Arrays.hashCode(values);
    }
}
