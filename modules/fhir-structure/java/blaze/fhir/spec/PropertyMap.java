package blaze.fhir.spec;

import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.RT;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A mutable map collecting the properties of a FHIR object while parsing.
 * <p>
 * The properties are stored in an object array of slots. Each key of the FHIR object has a fixed slot index. An empty
 * slot holds {@code null}. Values can't be {@code null}, so the number of non-empty slots can be tracked on
 * {@link #put(int, Object)}.
 * <p>
 * Separately from the properties, the slots of lists of primitive values which contain null placeholders are tracked.
 * In JSON, a null in an array of primitive values is a placeholder for an element that has only extended properties
 * given in the array of the corresponding {@code _} property. Because both arrays can come in any order, remaining
 * nulls can only be detected at the end of the object. The tracking allows checking only the marked slots.
 */
public final class PropertyMap {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "type");

    private final Object[] slots;
    private int count;
    private Map<Integer, String> nullElementSlots;

    /**
     * @param numSlots the number of slots
     */
    public PropertyMap(int numSlots) {
        slots = new Object[numSlots];
    }

    /**
     * Returns the value at {@code slot} or {@code null} if the slot is empty.
     *
     * @param slot the index of the slot
     * @return the value at {@code slot} or {@code null}
     */
    public Object get(int slot) {
        return slots[slot];
    }

    /**
     * Returns the value at {@code slot} or {@code notFound} if the slot is empty.
     *
     * @param slot     the index of the slot
     * @param notFound the value to return if the slot is empty
     * @return the value at {@code slot} or {@code notFound}
     */
    public Object get(int slot, Object notFound) {
        Object value = slots[slot];
        return value == null ? notFound : value;
    }

    /**
     * Puts {@code value} at {@code slot}, replacing an existing value.
     *
     * @param slot  the index of the slot
     * @param value the value to put, never {@code null}
     * @return this property map
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public PropertyMap put(int slot, Object value) {
        Objects.requireNonNull(value);
        if (slots[slot] == null) count++;
        slots[slot] = value;
        return this;
    }

    /**
     * Returns the slots of this property map.
     * <p>
     * The slots are not copied, so they must not be modified.
     *
     * @return the slots of this property map
     */
    public Object[] slots() {
        return slots;
    }

    /**
     * Returns the number of non-empty slots.
     *
     * @return the number of non-empty slots
     */
    public int count() {
        return count;
    }

    /**
     * Returns a persistent map of all non-empty slots using {@code keys}, which are indexed by slot. Additionally
     * associates {@code fhirType} under {@code :fhir/type} as the first entry.
     *
     * @param keys     the keys of the slots
     * @param fhirType the value to associate under {@code :fhir/type}
     * @return a persistent map of all non-empty slots
     */
    public IPersistentMap toPersistentMap(Object[] keys, Object fhirType) {
        Object[] kvs = new Object[2 * count + 2];
        kvs[0] = FHIR_TYPE;
        kvs[1] = fhirType;
        int n = 2;
        for (int i = 0; n < kvs.length; i++) {
            Object value = slots[i];
            if (value != null) {
                kvs[n++] = keys[i];
                kvs[n++] = value;
            }
        }
        return RT.mapUniqueKeys(kvs);
    }

    /**
     * Marks the list of primitive values at {@code slot} as possibly containing null elements.
     * <p>
     * Keeps the order in which slots are marked first.
     *
     * @param slot         the index of the slot of the list of primitive values
     * @param expectedType the expected type of the elements of the list, used in error messages
     */
    public void markNullElements(int slot, String expectedType) {
        if (nullElementSlots == null) {
            nullElementSlots = new LinkedHashMap<>(4);
        }
        nullElementSlots.putIfAbsent(slot, expectedType);
    }

    /**
     * Returns the first null element of the lists of primitive values marked by
     * {@link #markNullElements(int, String)}, looking at the lists in marking order, or {@code null} if the marked lists
     * contain no null element.
     *
     * @return the first null element or {@code null}
     */
    public NullElement firstNullElement() {
        if (nullElementSlots != null) {
            for (Map.Entry<Integer, String> entry : nullElementSlots.entrySet()) {
                int slot = entry.getKey();
                int index = ((List<?>) slots[slot]).indexOf(null);
                if (index >= 0) {
                    return new NullElement(slot, index, entry.getValue());
                }
            }
        }
        return null;
    }

    /**
     * A null element in a list of primitive values.
     *
     * @param slot         the index of the slot of the list
     * @param index        the index of the null element in the list
     * @param expectedType the expected type of the elements of the list
     */
    public record NullElement(int slot, int index, String expectedType) {
    }
}
