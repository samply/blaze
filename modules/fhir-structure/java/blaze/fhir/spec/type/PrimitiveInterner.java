package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;

import java.lang.String;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

/**
 * Interner for string-valued primitives.
 * <p>
 * Primitives with empty extension data are interned by their value alone, avoiding the allocation of a key and the
 * hashing and comparison of the extension data. Only primitives with non-empty extension data are interned by both
 * extension data and value. Both interners hold distinct primitives, because primitives with empty and non-empty
 * extension data are never equal.
 *
 * @param <V> the type of the primitive
 */
final class PrimitiveInterner<V> {

    private final V empty;
    private final Interner<String, V> valueInterner;
    private final Interner<Key, V> extendedInterner;

    /**
     * Creates an interner.
     *
     * @param empty   the value-less primitive with empty extension data that is returned instead of interning one
     * @param creator creates a new primitive from extension data and value
     */
    PrimitiveInterner(V empty, BiFunction<ExtensionData, String, V> creator) {
        this.empty = requireNonNull(empty);
        this.valueInterner = Interners.weakInterner(v -> creator.apply(ExtensionData.EMPTY, v));
        this.extendedInterner = Interners.weakInterner(k -> creator.apply(k.extensionData, k.value));
    }

    /**
     * Interns the primitive with empty extension data and {@code value}.
     *
     * @param value the value of the primitive
     * @return the interned primitive
     */
    V intern(String value) {
        return valueInterner.intern(requireNonNull(value));
    }

    /**
     * Interns the primitive with {@code extensionData} and {@code value}.
     *
     * @param extensionData the extension data of the primitive which has to be interned
     * @param value         the value of the primitive, may be {@code null}
     * @return the interned primitive
     */
    V intern(ExtensionData extensionData, String value) {
        if (extensionData == ExtensionData.EMPTY) {
            return value == null ? empty : valueInterner.intern(value);
        }
        return extendedInterner.intern(new Key(extensionData, value));
    }

    private record Key(ExtensionData extensionData, String value) {
        private Key {
            requireNonNull(extensionData);
        }
    }
}
