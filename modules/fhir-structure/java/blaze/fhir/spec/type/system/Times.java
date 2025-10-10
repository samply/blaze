package blaze.fhir.spec.type.system;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;

import static java.time.temporal.ChronoField.*;

public interface Times {

    DateTimeFormatter LOCAL_TIME = new DateTimeFormatterBuilder()
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendFraction(NANO_OF_SECOND, 0, 9, true)
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);

    static int memSize(LocalTime value) {
        return value == null ? 0 : 16;
    }

    static String toString(LocalTime value) {
        return LOCAL_TIME.format(value);
    }
}
