package blaze.fhir.spec;

import blaze.fhir.spec.type.Lists;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.RT;

import java.util.ArrayList;
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
 * <p>
 * Empty objects of the {@code _} property don't create primitive values. In arrays they are null placeholders as well.
 * For single primitive values the slot is marked but stays empty, so an empty marked slot of a single primitive value
 * is a null element.
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
     * Marks the list of primitive values or the single primitive value at {@code slot} as possibly containing null
     * elements.
     * <p>
     * Keeps the order in which slots are marked first.
     *
     * @param slot         the index of the slot of the list of primitive values or the single primitive value
     * @param expectedType the expected type of the primitive values, used in error messages
     */
    public void markNullElements(int slot, String expectedType) {
        if (nullElementSlots == null) {
            nullElementSlots = new LinkedHashMap<>(4);
        }
        nullElementSlots.putIfAbsent(slot, expectedType);
    }

    /**
     * Returns the first null element of the slots marked by {@link #markNullElements(int, String)}, looking at the
     * slots in marking order, or {@code null} if the marked slots contain no null element.
     *
     * @return the first null element or {@code null}
     */
    public NullElement firstNullElement() {
        if (nullElementSlots != null) {
            for (Map.Entry<Integer, String> entry : nullElementSlots.entrySet()) {
                int slot = entry.getKey();
                if (slots[slot] instanceof List<?> list) {
                    int index = list.indexOf(null);
                    if (index >= 0) {
                        return new NullElement(slot, index, entry.getValue());
                    }
                } else if (slots[slot] == null) {
                    return new NullElement(slot, -1, entry.getValue());
                }
            }
        }
        return null;
    }

    /**
     * Removes all null elements of the slots marked by {@link #markNullElements(int, String)}.
     * <p>
     * Empties the slots of lists which contain only null elements. That includes empty lists, which remain if all null
     * elements were trailing nulls of extended properties. Empty slots of single primitive values stay empty.
     *
     * @return this property map
     */
    public PropertyMap removeNullElements() {
        if (nullElementSlots != null) {
            for (int slot : nullElementSlots.keySet()) {
                if (slots[slot] instanceof List<?> list && (list.isEmpty() || list.contains(null))) {
                    List<Object> elements = new ArrayList<>(list.size());
                    for (Object element : list) {
                        if (element != null) elements.add(element);
                    }
                    if (elements.isEmpty()) {
                        slots[slot] = null;
                        count--;
                    } else {
                        slots[slot] = Lists.intern(elements);
                    }
                }
            }
            nullElementSlots = null;
        }
        return this;
    }

    /**
     * A null element in a list of primitive values or a single primitive value.
     *
     * @param slot         the index of the slot
     * @param index        the index of the null element in the list or -1 for a single primitive value
     * @param expectedType the expected type of the primitive values
     */
    public record NullElement(int slot, int index, String expectedType) {
    }
}
