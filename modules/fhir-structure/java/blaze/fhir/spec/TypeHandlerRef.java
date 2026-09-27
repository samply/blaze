package blaze.fhir.spec;

import clojure.lang.IFn;

import static java.util.Objects.requireNonNull;

/**
 * A reference to a type-handler which can be linked only once.
 * <p>
 * Type-handlers reference each other, even recursively. So references are
 * handed out while the type-handlers are created and linked after all
 * type-handlers exist. References to types without a type-handler stay
 * unlinked.
 * <p>
 * The field is not volatile, so reads are as cheap as a plain field read.
 * Linking has to happen before the references are published to other threads.
 */
public final class TypeHandlerRef {

    private IFn handler;

    /**
     * Returns the linked type-handler or {@code null} if this reference is not
     * linked.
     */
    public IFn get() {
        return handler;
    }

    /**
     * Links this reference to {@code handler}.
     *
     * @throws NullPointerException  if {@code handler} is {@code null}
     * @throws IllegalStateException if this reference is already linked
     */
    public void link(IFn handler) {
        requireNonNull(handler);
        if (this.handler != null) {
            throw new IllegalStateException("The type-handler reference is already linked.");
        }
        this.handler = handler;
    }
}
