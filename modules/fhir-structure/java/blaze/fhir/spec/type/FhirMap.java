package blaze.fhir.spec.type;

import clojure.lang.AFn;
import clojure.lang.ArityException;
import clojure.lang.IFn;
import clojure.lang.IKVReduce;
import clojure.lang.ISeq;

/**
 * Common interface of the four FHIR types that are represented as a map of a
 * shared {@link TypeMetadata} instance and an array of property values.
 * <p>
 * The four implementations differ only in the properties of their FHIR abstract
 * type, which they hold in explicit typed fields instead of the array. Which
 * property lives at which array slot is determined by the metadata, not by the
 * class.
 * <p>
 * Implementations are immutable values. The values array is never mutated after
 * construction and never escapes, so instances can be published to other
 * threads safely, which the resource cache relies on.
 */
public interface FhirMap extends Complex, IKVReduce, IFn {

    /**
     * The shape of this value, shared by all values of the same FHIR type.
     */
    TypeMetadata metadata();

    /**
     * Returns the value of the property at {@code index} in element-definition
     * order or {@code null} if that property is absent.
     * <p>
     * An empty list of a property that is held in {@link ExtensionData} or in
     * the {@code modifierExtension} field is reported as absent, because a plain
     * map wouldn't contain the key at all.
     */
    Object propertyAt(int index);

    @Override
    default Object kvreduce(IFn f, Object init) {
        return metadata().kvreduce(this, f, init);
    }

    private Object throwArity(int n) {
        throw new ArityException(n, getClass().getName());
    }

    @Override
    default Object invoke() {
        return throwArity(0);
    }

    @Override
    default Object invoke(Object key) {
        return valAt(key);
    }

    @Override
    default Object invoke(Object key, Object notFound) {
        return valAt(key, notFound);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3) {
        return throwArity(3);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3, Object a4) {
        return throwArity(4);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3, Object a4, Object a5) {
        return throwArity(5);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3, Object a4, Object a5, Object a6) {
        return throwArity(6);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7) {
        return throwArity(7);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8) {
        return throwArity(8);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8,
                          Object a9) {
        return throwArity(9);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8,
                          Object a9, Object a10) {
        return throwArity(10);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8,
                          Object a9, Object a10, Object a11) {
        return throwArity(11);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8,
                          Object a9, Object a10, Object a11, Object a12) {
        return throwArity(12);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8,
                          Object a9, Object a10, Object a11, Object a12, Object a13) {
        return throwArity(13);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8,
                          Object a9, Object a10, Object a11, Object a12, Object a13, Object a14) {
        return throwArity(14);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8,
                          Object a9, Object a10, Object a11, Object a12, Object a13, Object a14, Object a15) {
        return throwArity(15);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8,
                          Object a9, Object a10, Object a11, Object a12, Object a13, Object a14, Object a15,
                          Object a16) {
        return throwArity(16);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8,
                          Object a9, Object a10, Object a11, Object a12, Object a13, Object a14, Object a15,
                          Object a16, Object a17) {
        return throwArity(17);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8,
                          Object a9, Object a10, Object a11, Object a12, Object a13, Object a14, Object a15,
                          Object a16, Object a17, Object a18) {
        return throwArity(18);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8,
                          Object a9, Object a10, Object a11, Object a12, Object a13, Object a14, Object a15,
                          Object a16, Object a17, Object a18, Object a19) {
        return throwArity(19);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8,
                          Object a9, Object a10, Object a11, Object a12, Object a13, Object a14, Object a15,
                          Object a16, Object a17, Object a18, Object a19, Object a20) {
        return throwArity(20);
    }

    @Override
    default Object invoke(Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8,
                          Object a9, Object a10, Object a11, Object a12, Object a13, Object a14, Object a15,
                          Object a16, Object a17, Object a18, Object a19, Object a20, Object... args) {
        return throwArity(21);
    }

    @Override
    default Object applyTo(ISeq arglist) {
        return AFn.applyToHelper(this, arglist);
    }

    @Override
    default Object call() {
        return invoke();
    }

    @Override
    default void run() {
        invoke();
    }
}
