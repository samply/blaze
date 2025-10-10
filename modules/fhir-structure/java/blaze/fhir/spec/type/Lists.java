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

    @SuppressWarnings("unchecked")
    public static <T> List<T> nullToEmpty(Object list) {
        return list == null ? PersistentVector.EMPTY : (List<T>) list;
    }
}
