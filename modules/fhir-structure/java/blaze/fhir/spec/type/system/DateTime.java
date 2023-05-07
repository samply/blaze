package blaze.fhir.spec.type.system;

import clojure.lang.Keyword;

import java.time.DateTimeException;
import java.time.temporal.Temporal;
import java.time.temporal.ValueRange;

public interface DateTime extends JavaSystemType, Temporal {

    Keyword TYPE = Keyword.intern("system", "date-time");

    ValueRange YEAR_RANGE = ValueRange.of(1, 9999);

    default Keyword type() {
        return TYPE;
    }

    Date toDate();

    static DateTime parse(String s) {
        switch (s.length()) {
            case 10:
                return DateTimeDate.parse(s);
            case 7:
                return DateTimeYearMonth.parse(s);
            case 4:
                return DateTimeYear.parse(s);
            default:
                throw new DateTimeException(String.format("Can't parse `%s` as System.DateTime because it doesn't has the right length.", s));
        }
    }
}
