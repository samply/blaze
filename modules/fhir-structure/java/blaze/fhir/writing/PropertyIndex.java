package blaze.fhir.writing;

import clojure.lang.Keyword;

/**
 * Maps the key of a property to the index of its property handler.
 * <p>
 * Open addressing with linear probing over the cached hash of the keys.
 * Keywords are interned, so keys can be compared by identity, the same way
 * {@link clojure.lang.PersistentArrayMap} does it.
 */
public final class PropertyIndex {

    private final Keyword[] keys;
    private final int[] indices;
    private final int mask;

    public PropertyIndex(Keyword[] propertyKeys) {
        int capacity = 4;
        while (capacity < propertyKeys.length * 2) {
            capacity <<= 1;
        }
        this.keys = new Keyword[capacity];
        this.indices = new int[capacity];
        this.mask = capacity - 1;

        for (int index = 0; index < propertyKeys.length; index++) {
            var key = propertyKeys[index];
            int i = key.hasheq() & mask;
            while (keys[i] != null) {
                if (keys[i] == key) {
                    throw new IllegalArgumentException("Duplicate property key `%s`.".formatted(key));
                }
                i = (i + 1) & mask;
            }
            keys[i] = key;
            indices[i] = index;
        }
    }

    /**
     * Returns the index of the property handler responsible for {@code key} or
     * -1 if there is none.
     */
    public int get(Object key) {
        if (!(key instanceof Keyword keyword)) {
            return -1;
        }
        int i = keyword.hasheq() & mask;
        for (; ; ) {
            var k = keys[i];
            if (k == keyword) {
                return indices[i];
            }
            if (k == null) {
                return -1;
            }
            i = (i + 1) & mask;
        }
    }
}
