package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Interner for elements without value, interned by their extension data.
 * <p>
 * The element with empty extension data is always the given {@code empty} constant, so there is exactly one such
 * instance.
 *
 * @param <V> the type of the element
 */
final class ValuelessInterner<V> {

    private final V empty;
    private final Interner<ExtensionData, V> interner;

    /**
     * Creates an interner.
     *
     * @param empty   the element with empty extension data that is returned instead of interning one
     * @param creator creates a new element from extension data
     */
    ValuelessInterner(V empty, Function<ExtensionData, V> creator) {
        this.empty = requireNonNull(empty);
        this.interner = Interners.weakInterner(creator);
    }

    /**
     * Interns the element with {@code extensionData}.
     *
     * @param extensionData the extension data of the element which has to be interned
     * @return the interned element
     */
    V intern(ExtensionData extensionData) {
        return extensionData == ExtensionData.EMPTY ? empty : interner.intern(extensionData);
    }
}
