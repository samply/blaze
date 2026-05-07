package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import clojure.lang.PersistentVector;

import java.util.List;

public final class Lists {

    private static final Interner<List<?>, PersistentVector> INTERNER = Interners.weakInterner(PersistentVector::create);

    private Lists() {
    }

    /**
     * Finalizes the mutable {@code list}, interning it if all elements are itself interned.
     * <p>
     * Please be aware that the mutable list is used as key for interning. So it's super important that {@code list}
     * isn't mutated after this function is called.
     *
     * @param list the list to intern
     * @return a {@link PersistentVector} created from the list
     */
    public static PersistentVector intern(List<?> list) {
        return Base.areAllInternedExt(list) ? INTERNER.intern(list) : PersistentVector.create(list);
    }

    /**
     * Returns an empty {@link PersistentVector} if {@code list} is {@code null}, otherwise the
     * given {@code list} unchanged.
     * <p>
     * Rejects lists containing {@code null} elements: FHIR has no representation for a {@code null}
     * inside a repeating element, so any such list is invalid and would fail later (e.g. during
     * hashing or serialization) with a less informative error.
     *
     * @param list the list to check, may be {@code null}
     * @return an empty {@link PersistentVector} if {@code list} is {@code null}, otherwise
     * {@code list}
     * @throws IllegalArgumentException if {@code list} contains a {@code null} element
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> nullToEmpty(Object list) {
        if (list == null) return PersistentVector.EMPTY;
        List<T> typed = (List<T>) list;
        for (T e : typed) {
            if (e == null) {
                throw new IllegalArgumentException("null element in list");
            }
        }
        return typed;
    }
}
