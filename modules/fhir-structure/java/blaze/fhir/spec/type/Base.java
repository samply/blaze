package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface Base extends IPersistentMap, Map<Object, Object>, IRecord {

    Keyword ID = Keyword.intern("id");
    Keyword EXTENSION = Keyword.intern("extension");
    Keyword VALUE = Keyword.intern("value");

    IFn imapCons = RT.var("clojure.core", "imap-cons");

    static ISeq appendElement(ISeq base, Keyword name, Object value) {
        return value == null ? base : base.cons(MapEntry.create(name, value));
    }

    Keyword fhirType();

    void serializeJsonPrimitiveExtension(JsonGenerator generator) throws IOException;

    @SuppressWarnings("UnstableApiUsage")
    void hashInto(PrimitiveSink sink);

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
