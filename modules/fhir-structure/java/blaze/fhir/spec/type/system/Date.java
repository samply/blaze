package blaze.fhir.spec.type.system;

import clojure.lang.Keyword;
import clojure.lang.RT;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.temporal.Temporal;
import java.time.temporal.ValueRange;

public interface Date extends JavaSystemType, Temporal {

    byte HASH_MARKER = 5;

    Keyword TYPE = RT.keyword("system", "date");

    ValueRange YEAR_RANGE = ValueRange.of(1, 9999);

    default Keyword type() {
        return TYPE;
    }

    DateTime toDateTime();

    void writeTo(JsonGenerator generator) throws IOException;

    static Date parse(String s) {
        return switch (s.length()) {
            case 10 -> DateDate.parse(s);
            case 7 -> DateYearMonth.parse(s);
            case 4 -> DateYear.parse(s);
            default ->
                    throw new DateTimeException(String.format("Can't parse `%s` as System.Date because it doesn't has the right length.", s));
        };
    }
}
