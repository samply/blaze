package blaze.fhir.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A mutable map collecting the properties of a FHIR object while parsing.
 * <p>
 * The properties are stored as alternating keys and values, like in a {@link clojure.lang.PersistentArrayMap}.
 * <p>
 * Separately from the properties, the keys of lists of primitive values which contain null placeholders are tracked.
 * So that tracking never ends up in the finalized FHIR object.
 */
public final class PropertyMap {

    private final ArrayList<Object> keysAndValues;
    private Map<Object, String> nullElementKeys;

    /**
     * @param initialCapacity the initial capacity of keys and values together
     */
    public PropertyMap(int initialCapacity) {
        keysAndValues = new ArrayList<>(initialCapacity);
    }

    /**
     * Returns the value under {@code key} or {@code notFound} if there is no such value.
     *
     * @param key      the key of the value
     * @param notFound the value to return if there is no value under {@code key}
     * @return the value under {@code key} or {@code notFound}
     */
    public Object get(Object key, Object notFound) {
        int idx = keysAndValues.indexOf(key);
        return idx < 0 ? notFound : keysAndValues.get(idx + 1);
    }

    /**
     * Puts {@code value} under {@code key}, replacing an existing value.
     *
     * @param key   the key of the value
     * @param value the value to put
     * @return this property map
     */
    public PropertyMap put(Object key, Object value) {
        int idx = keysAndValues.indexOf(key);
        if (idx < 0) {
            keysAndValues.add(key);
            keysAndValues.add(value);
        } else {
            keysAndValues.set(idx + 1, value);
        }
        return this;
    }

    /**
     * Returns a new array of the alternating keys and values.
     *
     * @return a new array of the alternating keys and values
     */
    public Object[] toArray() {
        return keysAndValues.toArray();
    }

    /**
     * Marks the list of primitive values under {@code key} as possibly containing null elements.
     * <p>
     * Keeps the order in which keys are marked first.
     *
     * @param key          the key of the list of primitive values
     * @param expectedType the expected type of the elements of the list, used in error messages
     */
    public void markNullElements(Object key, String expectedType) {
        if (nullElementKeys == null) {
            nullElementKeys = new LinkedHashMap<>(4);
        }
        nullElementKeys.putIfAbsent(key, expectedType);
    }

    /**
     * Returns the keys marked by {@link #markNullElements(Object, String)} in marking order, mapped to their expected
     * types, or {@code null} if no key was marked.
     *
     * @return the marked keys or {@code null}
     */
    public Map<Object, String> nullElementKeys() {
        return nullElementKeys;
    }
}
