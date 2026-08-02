package blaze.fhir.spec.type;

import blaze.fhir.writing.PropertyHandler;
import blaze.fhir.writing.PropertyIndex;
import clojure.lang.IFn;
import clojure.lang.ILookupThunk;
import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.MapEntry;
import clojure.lang.PersistentArrayMap;
import clojure.lang.PersistentList;
import clojure.lang.PersistentVector;
import clojure.lang.RT;
import clojure.lang.Reduced;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.Integer;
import java.lang.String;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * The shape of one FHIR type that is represented as a map.
 * <p>
 * Built once per type when the type metadata registry is built and shared by
 * all values of that type. Holds everything that is the same for all of them:
 * the type keyword, the properties in element-definition order together with
 * their {@link PropertyHandler}, the keyword to slot index, the lexical key
 * order used for hashing and one {@link ILookupThunk} per array slot.
 * <p>
 * The two orders are precomputed here, so that serialization can write the
 * properties in element order and hashing can write them in lexical key order,
 * without sorting anything at runtime.
 */
public final class TypeMetadata {

    /**
     * The FHIR abstract type a type is derived from, which determines the
     * properties held in explicit fields and therefore the class of its values.
     */
    public enum Kind {

        /**
         * Elements nested in a complex type. Leading properties are
         * {@code id} and {@code extension}.
         */
        ELEMENT(2),

        /**
         * Elements nested in a resource and the named complex types without a
         * Java implementation. Leading properties are {@code id},
         * {@code extension} and {@code modifierExtension}.
         */
        BACKBONE_ELEMENT(3),

        /**
         * Resources that are no domain resources, namely {@code Bundle},
         * {@code Parameters} and {@code Binary}.
         */
        RESOURCE(4),

        /**
         * All other resources.
         */
        DOMAIN_RESOURCE(8);

        private final int leadingCount;

        Kind(int leadingCount) {
            this.leadingCount = leadingCount;
        }

        /**
         * Number of leading properties that are held in explicit fields instead
         * of the values array.
         */
        public int leadingCount() {
            return leadingCount;
        }

        boolean isResource() {
            return this == RESOURCE || this == DOMAIN_RESOURCE;
        }
    }

    private final Keyword type;
    private final Kind kind;
    private final int lead;
    private final Keyword[] keys;

    /**
     * The property handlers in element-definition order.
     * <p>
     * Only the ones from {@link #lead} on are used, because the leading
     * properties are written by the map classes themselves, from their typed
     * fields.
     */
    private final PropertyHandler[] propertyHandlers;

    private final PropertyIndex index;
    private final SerializableString resourceType;

    /**
     * The property indices in lexical key order, followed by the index of the
     * {@code :fhir/type} pseudo property, which is {@code keys.length}.
     */
    private final int[] hashOrder;

    /**
     * The cached hashes of the keys at the same position in {@link #hashOrder}.
     */
    private final int[] hashKeys;

    private final ILookupThunk[] thunks;
    private final ILookupThunk typeThunk;

    public TypeMetadata(Keyword type, Kind kind, String typeName, Keyword[] keys,
                        PropertyHandler[] propertyHandlers) {
        this.type = requireNonNull(type);
        this.kind = requireNonNull(kind);
        this.lead = kind.leadingCount();
        this.keys = requireNonNull(keys);
        this.propertyHandlers = requireNonNull(propertyHandlers);
        if (keys.length != propertyHandlers.length) {
            throw new IllegalArgumentException("Different number of keys and property handlers.");
        }
        if (keys.length < lead) {
            throw new IllegalArgumentException(
                    "Type `%s` has fewer properties than its abstract type `%s`.".formatted(type, kind));
        }
        this.index = new PropertyIndex(keys);
        this.resourceType = kind.isResource() ? new SerializedString(requireNonNull(typeName)) : null;

        int n = keys.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> keys[a].compareTo(keys[b]));
        this.hashOrder = new int[n + 1];
        this.hashKeys = new int[n + 1];
        for (int i = 0; i < n; i++) {
            hashOrder[i] = order[i];
            hashKeys[i] = keys[order[i]].hasheq();
        }
        // :fhir/type is the only namespaced key, so it always sorts last
        hashOrder[n] = n;
        hashKeys[n] = Base.FHIR_TYPE_KEY.hasheq();

        final TypeMetadata self = this;
        this.thunks = new ILookupThunk[n - lead];
        for (int slot = 0; slot < thunks.length; slot++) {
            final int property = slot + lead;
            thunks[slot] = new ILookupThunk() {
                @Override
                public Object get(Object target) {
                    // the metadata guard is essential: all values of the same
                    // abstract type share one class, so only the metadata
                    // determines which property lives at which slot
                    return target instanceof FhirMap m && m.metadata() == self ? m.propertyAt(property) : this;
                }
            };
        }
        this.typeThunk = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof FhirMap m && m.metadata() == self ? self.type : this;
            }
        };
    }

    public Keyword type() {
        return type;
    }

    /**
     * The keys of all properties in element-definition order.
     */
    public Keyword[] keys() {
        return keys.clone();
    }

    /**
     * Returns the index of the property {@code key} in element-definition order
     * or -1 if this type has no such property.
     */
    public int property(Object key) {
        return index.get(key);
    }

    /**
     * Returns the index of {@code key} in the values array or a negative number
     * if {@code key} is either a leading property or unknown.
     */
    public int slot(Object key) {
        return index.get(key) - lead;
    }

    ILookupThunk thunk(int slot) {
        return thunks[slot];
    }

    ILookupThunk typeThunk() {
        return typeThunk;
    }

    /**
     * Creates a value of this type from the property values in
     * element-definition order.
     * <p>
     * The array is not retained. Its trailing part is copied into the values
     * array of the new value, so the caller can't mutate it afterwards.
     */
    public FhirMap create(Object[] properties) {
        if (properties.length != keys.length) {
            throw new IllegalArgumentException(
                    "Expected %d properties for type `%s` but got %d.".formatted(keys.length, type, properties.length));
        }
        var values = Arrays.copyOfRange(properties, lead, properties.length);
        return switch (kind) {
            case ELEMENT -> new ElementMap(ExtensionData.of(properties[0], properties[1]), this, values);
            case BACKBONE_ELEMENT -> new BackboneElementMap(ExtensionData.of(properties[0], properties[1]),
                    Lists.typedNullToEmpty(properties[2], Extension.class), this, values);
            case RESOURCE -> new ResourceMap((String) properties[0], (Meta) properties[1], (Uri) properties[2],
                    (Code) properties[3], null, this, values);
            case DOMAIN_RESOURCE -> new DomainResourceMap((String) properties[0], (Meta) properties[1],
                    (Uri) properties[2], (Code) properties[3], (Narrative) properties[4],
                    Lists.typedNullToEmpty(properties[5], Complex.class),
                    Lists.typedNullToEmpty(properties[6], Extension.class),
                    Lists.typedNullToEmpty(properties[7], Extension.class), null, this, values);
        };
    }

    /**
     * Creates a value of this type from {@code m}.
     * <p>
     * Rejects keys without a property, because silently dropping a mistyped key
     * of a resource would also change its content hash.
     */
    public FhirMap create(IPersistentMap m) {
        var properties = new Object[keys.length];
        for (ISeq s = RT.seq(m); s != null; s = s.next()) {
            var entry = (Map.Entry<?, ?>) s.first();
            var key = entry.getKey();
            if (key == Base.FHIR_TYPE_KEY) continue;
            int i = index.get(key);
            if (i < 0) {
                throw new IllegalArgumentException("Unknown property `%s` in type `%s`.".formatted(key, type));
            }
            properties[i] = entry.getValue();
        }
        return create(properties);
    }

    /**
     * Creates a {@link PersistentArrayMap} of the non-null {@code properties},
     * using {@code keys} as keys.
     * <p>
     * Used to feed the constructors of the complex types with a Java
     * implementation, which take an {@link IPersistentMap}.
     */
    public static IPersistentMap arrayMap(Keyword[] keys, Object[] properties) {
        int n = 0;
        for (Object property : properties) {
            if (property != null) n++;
        }
        var array = new Object[2 * n];
        int j = 0;
        for (int i = 0; i < properties.length; i++) {
            if (properties[i] != null) {
                array[j++] = keys[i];
                array[j++] = properties[i];
            }
        }
        return new PersistentArrayMap(array);
    }

    /**
     * The name of the FHIR resource type, to be written as {@code resourceType}
     * property. Only available if this type is a resource.
     */
    SerializableString resourceType() {
        return resourceType;
    }

    /**
     * Writes the properties held in {@code values}, which belong to a value of
     * this type.
     * <p>
     * Reads the values from the array directly instead of going through
     * {@link FhirMap#propertyAt}, because that would be a megamorphic interface
     * call for every property of every value, most of which are absent. The
     * leading properties are written by the map classes themselves.
     */
    void serializeValues(JsonGenerator generator, Object[] values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            var value = values[i];
            if (value != null) {
                propertyHandlers[lead + i].writeValue(generator, value);
            }
        }
    }

    /**
     * Writes {@code value} into {@code sink}, producing exactly the byte stream
     * {@link Base#hashIntoMap} produces for the equivalent plain map.
     */
    @SuppressWarnings("UnstableApiUsage")
    void hashInto(FhirMap value, PrimitiveSink sink) {
        sink.putByte(Base.HASH_MARKER_MAP);
        int n = keys.length;
        for (int i = 0; i < hashOrder.length; i++) {
            int property = hashOrder[i];
            var v = property == n ? type : value.propertyAt(property);
            if (v != null) {
                sink.putInt(hashKeys[i]);
                hashValue(v, sink);
            }
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void hashValue(Object value, PrimitiveSink sink) {
        switch (value) {
            case Keyword k -> sink.putInt(k.hasheq());
            case Base b -> b.hashInto(sink);
            // for compatibility reasons, we use the hash signature of a
            // FHIR.String instead of a System.String
            case String s -> blaze.fhir.spec.type.String.hashIntoValue(sink, s);
            default -> Base.hashInto(value, sink);
        }
    }

    int memSizeProperties(FhirMap value) {
        int size = 0;
        for (int i = lead; i < keys.length; i++) {
            size += Base.memSize(value.propertyAt(i));
        }
        return size;
    }

    int count(FhirMap value) {
        int count = 1;
        for (int i = 0; i < keys.length; i++) {
            if (value.propertyAt(i) != null) count++;
        }
        return count;
    }

    ISeq seq(FhirMap value) {
        ISeq seq = PersistentList.EMPTY;
        for (int i = keys.length - 1; i >= 0; i--) {
            var property = value.propertyAt(i);
            if (property != null) {
                seq = seq.cons(MapEntry.create(keys[i], property));
            }
        }
        return seq.cons(MapEntry.create(Base.FHIR_TYPE_KEY, type));
    }

    Object kvreduce(FhirMap value, IFn f, Object init) {
        init = f.invoke(init, Base.FHIR_TYPE_KEY, type);
        if (init instanceof Reduced reduced) return reduced.deref();
        for (int i = 0; i < keys.length; i++) {
            var property = value.propertyAt(i);
            if (property != null) {
                init = f.invoke(init, keys[i], property);
                if (init instanceof Reduced reduced) return reduced.deref();
            }
        }
        return init;
    }

    Stream<PersistentVector> references(FhirMap value) {
        if (type == Base.FHIR_TYPE_BUNDLE_ENTRY) return Stream.empty();
        return IntStream.range(0, keys.length)
                .mapToObj(value::propertyAt)
                .filter(Objects::nonNull)
                .flatMap(Base::references);
    }

    Iterator<Map.Entry<Object, Object>> iterator(FhirMap value) {
        return new FhirMapIterator(value);
    }

    private final class FhirMapIterator implements Iterator<Map.Entry<Object, Object>> {

        private final FhirMap value;
        private int i = -1;
        private Object next;

        private FhirMapIterator(FhirMap value) {
            this.value = value;
            this.next = type;
        }

        @Override
        public boolean hasNext() {
            if (next != null) return true;
            while (i + 1 < keys.length) {
                i++;
                var property = value.propertyAt(i);
                if (property != null) {
                    next = property;
                    return true;
                }
            }
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map.Entry<Object, Object> next() {
            if (!hasNext()) throw new NoSuchElementException();
            var property = next;
            next = null;
            return MapEntry.create(i < 0 ? Base.FHIR_TYPE_KEY : keys[i], property);
        }
    }

    @Override
    public String toString() {
        return "TypeMetadata{" + type + "}";
    }

    /**
     * Access to the global type metadata registry.
     * <p>
     * Values of the four map classes have to be creatable from an
     * {@link IPersistentMap} alone, both for the {@code #fhir/map} data reader
     * and for the Clojure compiler, which recreates constants of {@code IRecord}
     * types by calling their static {@code create} method. Neither can be given
     * a context, so the registry is looked up globally. There is no deployment
     * variance in the set of FHIR types, so a global registry is the same kind
     * of singleton the structure definition repository already is.
     * <p>
     * The var is resolved lazily on first use, so that loading the map classes
     * doesn't load the registry namespace.
     */
    public static final class Registry {

        private static final IFn LOOKUP = (IFn) RT.var("clojure.core", "requiring-resolve")
                .invoke(clojure.lang.Symbol.intern("blaze.fhir.type-metadata", "type-metadata"));

        private Registry() {
        }

        public static TypeMetadata get(Object type) {
            return (TypeMetadata) LOOKUP.invoke(type);
        }

        public static FhirMap create(IPersistentMap m) {
            var type = m.valAt(Base.FHIR_TYPE_KEY);
            if (type == null) {
                throw new IllegalArgumentException("Missing `:fhir/type` property.");
            }
            var metadata = get(type);
            if (metadata == null) {
                throw new IllegalArgumentException("Unknown FHIR type `%s`.".formatted(type));
            }
            return metadata.create(m);
        }
    }
}
