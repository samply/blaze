package blaze.fhir.writing;

import clojure.lang.Keyword;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

/**
 * Writes one property of a FHIR value that is represented as map.
 */
public abstract class PropertyHandler {

    /**
     * The key of the property in the map to write.
     */
    final Keyword key;

    PropertyHandler(Keyword key) {
        this.key = requireNonNull(key);
    }

    /**
     * The key of the property this handler is responsible for.
     */
    public final Keyword key() {
        return key;
    }

    /**
     * Writes the property this handler is responsible for, including its field
     * name.
     */
    public abstract void writeValue(JsonGenerator generator, Object value) throws IOException;

    /**
     * Returns an exception for a list in a property that the element definition
     * declares single-valued.
     */
    final IllegalArgumentException singleValueExpected() {
        return new IllegalArgumentException(
                "Expected a single value in property `%s` but got a list.".formatted(key.getName()));
    }

    /**
     * Returns an exception for a single value in a property that the element
     * definition declares repeating.
     */
    final IllegalArgumentException listExpected() {
        return new IllegalArgumentException(
                "Expected a list of values in property `%s` but got a single value.".formatted(key.getName()));
    }
}
