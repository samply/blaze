package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Strings;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.*;
import java.util.stream.Stream;

public interface Base extends IPersistentMap, Map<Object, Object>, IRecord {

    Keyword ID = Keyword.intern("id");
    Keyword EXTENSION = Keyword.intern("extension");
    Keyword VALUE = Keyword.intern("value");
    Keyword FHIR_TYPE_KEY = Keyword.intern("fhir", "type");
    Keyword FHIR_TYPE_BUNDLE_ENTRY = Keyword.intern("fhir.Bundle", "entry");

    int MEM_SIZE_OBJECT_HEADER = 8;
    int MEM_SIZE_PERSISTENT_VECTOR_OBJECT = 40;
    int MEM_SIZE_PERSISTENT_SUB_VECTOR_OBJECT = 32;
    int MEM_SIZE_PERSISTENT_ARRAY_MAP_OBJECT = 24;
    int MEM_SIZE_PERSISTENT_HASH_MAP_OBJECT = 40;
    int MEM_SIZE_ATOMIC_REFERENCE = 16;
    int MEM_SIZE_REFERENCE = 4;

    IFn imapCons = RT.var("clojure.core", "imap-cons");

    static ISeq appendElement(ISeq base, Keyword name, Object value) {
        return value == null ? base : base.cons(MapEntry.create(name, value));
    }

    static ISeq appendElement(ISeq base, Keyword name, List<?> value) {
        return value.isEmpty() ? base : base.cons(MapEntry.create(name, value));
    }

    @SuppressWarnings("unchecked")
    static <T> List<T> listFrom(IPersistentMap m, Keyword key) {
        var list = m.valAt(key);
        return list == null ? PersistentVector.EMPTY : (List<T>) list;
    }

    static boolean isInterned(Base x) {
        return x == null || x.isInterned();
    }

    static boolean areAllInterned(List<? extends Base> x) {
        if (x == null) return true;
        for (Base e : x) {
            if (!Base.isInterned(e)) return false;
        }
        return true;
    }

    static boolean isInternedExt(Object x) {
        return x instanceof Base b && b.isInterned();
    }

    static boolean areAllInternedExt(List<?> x) {
        if (x == null) return true;
        for (Object e : x) {
            if (!Base.isInternedExt(e)) return false;
        }
        return true;
    }

    @SuppressWarnings({"UnstableApiUsage"})
    static void hashInto(Object x, PrimitiveSink sink) {
        if (x == null) return;
        switch (x) {
            case Base b:
                b.hashInto(sink);
                break;
            case Map<?, ?> m:
                hashIntoMap(m, sink);
                break;
            case List<?> l:
                sink.putByte((byte) 36);
                for (Object i : l) {
                    hashInto(i, sink);
                }
                break;
            default:
                throw new IllegalStateException("Unexpected value of class: " + x.getClass());
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    static void hashIntoList(List<? extends Base> l, PrimitiveSink sink) {
        sink.putByte((byte) 36);
        for (Base b : l) {
            b.hashInto(sink);
        }
    }

    @SuppressWarnings({"UnstableApiUsage", "unchecked"})
    static void hashIntoMap(Map<?, ?> m, PrimitiveSink sink) {
        sink.putByte((byte) 37);
        List<Entry<Keyword, Object>> toSort = new ArrayList<>(m.size());
        for (Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() instanceof Keyword) toSort.add((Entry<Keyword, Object>) e);
        }
        toSort.sort(Entry.comparingByKey());
        for (Entry<Keyword, Object> e : toSort) {
            sink.putInt(e.getKey().hasheq());
            switch (e.getValue()) {
                case Keyword k:
                    sink.putInt(k.hasheq());
                    break;
                case Base b:
                    b.hashInto(sink);
                    break;
                // for compatibility reasons, we use the hash signature of a FHIR.String instead of a System.String
                case String s:
                    blaze.fhir.spec.type.String.hashIntoValue(sink, s);
                    break;
                default:
                    hashInto(e.getValue(), sink);
            }
        }
    }

    static Stream<PersistentVector> references(Object x) {
        return switch (x) {
            case Base b -> b.references();
            case Map<?, ?> m -> referencesMap(m);
            case List<?> v -> v.stream().flatMap(Base::references);
            default -> Stream.empty();
        };
    }

    static Stream<PersistentVector> referencesMap(Map<?, ?> m) {
        if (m.get(FHIR_TYPE_KEY) == FHIR_TYPE_BUNDLE_ENTRY) return Stream.empty();
        return m.values().stream()
                .filter(v -> !(v instanceof Keyword))
                .filter(v -> !(v instanceof String))
                .flatMap(Base::references);
    }

    static int memSize(Object x) {
        if (x == null) return 0;
        return switch (x) {
            case Base b -> b.memSize();
            case PersistentArrayMap m -> m.isEmpty() ? 0 : memSizeArrayMap(m);
            case PersistentHashMap m -> m.isEmpty() ? 0 : memSizeHashMap(m);
            case IPersistentVector v -> v.count() == 0 ? 0 : memSizeVector(v);
            case String s -> Strings.memSize(s);
            default -> 0;
        };
    }

    @SuppressWarnings("unchecked")
    static int memSizeArrayMap(PersistentArrayMap m) {
        return MEM_SIZE_PERSISTENT_ARRAY_MAP_OBJECT + 16 +
                ((m.size() * MEM_SIZE_REFERENCE * 2 + 3) & ~7) +
                m.values().stream().mapToInt(Base::memSize).sum();
    }

    @SuppressWarnings("unchecked")
    static int memSizeHashMap(PersistentHashMap m) {
        // TODO: improve calculation
        return MEM_SIZE_PERSISTENT_HASH_MAP_OBJECT + MEM_SIZE_ATOMIC_REFERENCE +
                m.size() * MEM_SIZE_REFERENCE * 7 +
                m.values().stream().mapToInt(Base::memSize).sum();
    }

    static int memSizeVector(IPersistentVector list) {
        return switch (list) {
            case PersistentVector v -> memSizeNormalVector(v);
            case APersistentVector.SubVector v -> memSizeSubVector(v);
            default -> 0;
        };
    }

    @SuppressWarnings("unchecked")
    static int memSizeNormalVector(PersistentVector list) {
        // TODO: improve calculation for lists with more than 32 elements
        return MEM_SIZE_PERSISTENT_VECTOR_OBJECT + (list.size() <= 32
                ? 16 + ((list.size() * MEM_SIZE_REFERENCE + 3) & ~7)
                : list.size() * MEM_SIZE_REFERENCE * 3)
                + list.stream().mapToInt(Base::memSize).sum();
    }

    static int memSizeSubVector(APersistentVector.SubVector list) {
        return MEM_SIZE_PERSISTENT_SUB_VECTOR_OBJECT + memSizeVector(list.v);
    }

    static int memSize(Base value) {
        return value == null ? 0 : value.memSize();
    }

    Keyword fhirType();

    boolean isInterned();

    void serializeJsonPrimitiveExtension(JsonGenerator generator) throws IOException;

    void serializeJsonField(JsonGenerator generator, FieldName fieldName) throws IOException;

    @SuppressWarnings("UnstableApiUsage")
    void hashInto(PrimitiveSink sink);

    default Stream<PersistentVector> references() {
        return Stream.empty();
    }

    int memSize();

    @Override
    default boolean containsKey(Object key) {
        return valAt(key) != null;
    }

    @Override
    default Object valAt(Object key) {
        return valAt(key, null);
    }

    @Override
    default IMapEntry entryAt(Object key) {
        var val = valAt(key);
        return val == null ? null : MapEntry.create(key, val);
    }

    @Override
    default int count() {
        var seq = seq();
        return seq == null ? 0 : seq.count();
    }

    @Override
    default IPersistentCollection cons(Object o) {
        return (IPersistentCollection) imapCons.invoke(this, o);
    }

    @Override
    default IPersistentMap assocEx(Object key, Object val) {
        throw new UnsupportedOperationException("AssocEx isn't supported in FHIR types.");
    }

    @Override
    default IPersistentMap without(Object key) {
        return assoc(key, null);
    }

    @Override
    default boolean equiv(Object o) {
        return equals(o);
    }

    @Override
    default int size() {
        return count();
    }

    @Override
    default boolean isEmpty() {
        return size() == 0;
    }

    @Override
    default boolean containsValue(Object value) {
        return false;
    }

    @Override
    default Object get(Object key) {
        return valAt(key);
    }

    @Override
    default Object put(Object key, Object value) {
        throw new UnsupportedOperationException("Put isn't supported in FHIR types.");
    }

    @Override
    default Object remove(Object key) {
        throw new UnsupportedOperationException("Remove isn't supported in FHIR types.");
    }

    @Override
    default void putAll(Map<?, ?> m) {
        throw new UnsupportedOperationException("PutAll isn't supported in FHIR types.");
    }

    @Override
    default void clear() {
        throw new UnsupportedOperationException("Clear isn't supported in FHIR types.");
    }

    @Override
    @SuppressWarnings("unchecked")
    default Set<Object> keySet() {
        return PersistentHashSet.create(RT.keys(this));
    }

    @Override
    @SuppressWarnings("unchecked")
    default Set<Entry<Object, Object>> entrySet() {
        return PersistentHashSet.create(seq());
    }

    @Override
    @SuppressWarnings("unchecked")
    default Collection<Object> values() {
        return (Collection<Object>) RT.vals(this);
    }
}
