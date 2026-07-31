package blaze.fhir.writing;

import clojure.lang.IKVReduce;
import clojure.lang.ILookup;
import clojure.lang.IPersistentMap;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Common base of all type handlers for values that are represented as maps.
 * <p>
 * Values are written in the order of the element definitions of the type, while
 * a map only holds the properties actually present. So the values are put into
 * the slot of their property handler first and are written afterwards. Compared
 * to looking up every possible property in the map, this only touches the
 * properties present.
 */
abstract class AbstractMapTypeHandler implements TypeHandler {

    private final PropertyHandler[] propertyHandlers;
    private final SlotFiller slotFiller;

    AbstractMapTypeHandler(PropertyHandler[] propertyHandlers) {
        this.propertyHandlers = requireNonNull(propertyHandlers);
        this.slotFiller = new SlotFiller(new PropertyIndex(propertyHandlers));
    }

    static IPersistentMap checkMap(Object value) {
        if (value instanceof IPersistentMap map) {
            return map;
        }
        throw new IllegalArgumentException("Value `%s` is no FHIR type.".formatted(value));
    }

    @Override
    public final void link(ILookup typeHandlers) {
        for (PropertyHandler propertyHandler : propertyHandlers) {
            propertyHandler.link(typeHandlers);
        }
    }

    final void writeProperties(JsonGenerator generator, IPersistentMap map) throws IOException {
        var slots = new Object[propertyHandlers.length];
        fillSlots(map, slots);
        for (int i = 0; i < propertyHandlers.length; i++) {
            var value = slots[i];
            if (value != null) {
                propertyHandlers[i].writeValue(generator, value);
            }
        }
    }

    private void fillSlots(IPersistentMap map, Object[] slots) {
        if (map instanceof IKVReduce kvReduce) {
            kvReduce.kvreduce(slotFiller, slots);
        } else {
            for (Object entry : map) {
                slotFiller.invoke(slots, ((Map.Entry<?, ?>) entry).getKey(), ((Map.Entry<?, ?>) entry).getValue());
            }
        }
    }
}
