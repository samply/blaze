package blaze.fhir.writing;

import clojure.lang.AFn;

/**
 * Reducing function putting the value of each property of a map into the slot
 * of its property handler.
 * <p>
 * Stateless, because the slots are the accumulator of the reduction.
 */
final class SlotFiller extends AFn {

    private final PropertyIndex index;

    SlotFiller(PropertyIndex index) {
        this.index = index;
    }

    @Override
    public Object invoke(Object slots, Object key, Object value) {
        int i = index.get(key);
        if (i >= 0) {
            ((Object[]) slots)[i] = value;
        }
        return slots;
    }
}
