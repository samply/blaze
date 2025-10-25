package blaze.fhir.spec.type.system;

import clojure.lang.Keyword;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.time.temporal.ValueRange;

public interface DateTime extends JavaSystemType, Temporal {

    Keyword TYPE = Keyword.intern("system", "date-time");

    ValueRange YEAR_RANGE = ValueRange.of(1, 9999);

    /**
     * Obtains an instance of {@code DateTimeYear}, {@code DateTimeYearMonth},
     * {@code DateTimeDate}, {@code LocalDateTime} or {@code OffsetDateTime}
     * from a text string such as {@code 2007}, {@code 2007-12},
     * {@code 2007-12-03}, {@code 2007-12-03T10:15:30} or
     * {@code 2007-12-03T10:15:30+01:00} .
     * <p>
     * The string must represent a valid date-time.
     *
     * @param s the text to parse such as "2007-12-03T10:15:30+01:00", not null
     * @return the parsed offset or local date-time, not null
     * @throws DateTimeParseException if the text cannot be parsed
     */
    static Temporal parse(String s) {
        switch (s.length()) {
            case 10:
                return DateTimeDate.parse(s);
            case 7:
                return DateTimeYearMonth.parse(s);
            case 4:
                return DateTimeYear.parse(s);
        }

        if (s.length() < 13) {
            throw new DateTimeParseException("Text cannot be parsed to a DateTime", s, 0);
        }

        int year = parseInt(s, 0, 4);
        int month = parseInt(s, 5, 7);
        int day = parseInt(s, 8, 10);
        var date = LocalDate.of(year, month, day);

        int hour = parseInt(s, 11, 13);
        if (s.length() < 16) {
            return LocalDateTime.of(date, LocalTime.of(hour, 0));
        }

        int minute = parseInt(s, 14, 16);
        if (s.length() == 16) {
            return LocalDateTime.of(date, LocalTime.of(hour, minute));
        }

        if (s.charAt(16) != ':') {
            var offset = parseOffset(s, 16);
            return OffsetDateTime.of(date, LocalTime.of(hour, minute), offset);
        }

        if (s.length() < 19) {
            throw new DateTimeParseException("Text cannot be parsed to a DateTime", s, 16);
        }

        int second = parseInt(s, 17, 19);
        if (s.length() == 19) {
            var time = LocalTime.of(hour, minute, second);
            return LocalDateTime.of(date, time);
        }

        int endIdx;
        LocalTime time;
        if (s.charAt(19) == '.') {
            endIdx = skipFractionDigits(s, 20);
            int nano = parseInt(s, 20, endIdx) * pow10(29 - endIdx);
            time = LocalTime.of(hour, minute, second, nano);
        } else {
            endIdx = 19;
            time = LocalTime.of(hour, minute, second);
        }

        if (s.length() == endIdx) {
            return LocalDateTime.of(date, time);
        } else {
            var offset = parseOffset(s, endIdx);
            return OffsetDateTime.of(date, time, offset);
        }
    }

    static int parseInt(String s, int beginIdx, int endIdx) {
        try {
            return Integer.parseInt(s, beginIdx, endIdx, 10);
        } catch (NumberFormatException e) {
            throw new DateTimeParseException("Text cannot be parsed to a DateTime", s, beginIdx);
        }
    }

    static int skipFractionDigits(String s, int i) {
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            i++;
        }
        return i;
    }

    static int pow10(int i) {
        int n = 1;
        while (i > 0) {
            n = n * 10;
            i--;
        }
        return n;
    }

    static ZoneOffset parseOffset(String s, int idx) {
        char c = s.charAt(idx);
        if (c == '+' || c == '-') {
            if (s.length() < idx + 6) {
                throw new DateTimeParseException("Text cannot be parsed to a DateTime", s, idx + 1);
            }
            int hour = parseInt(s, idx + 1, idx + 3);
            int minute = parseInt(s, idx + 4, idx + 6);
            if (s.length() == idx + 6) {
                return ZoneOffset.ofHoursMinutes(c == '+' ? hour : -hour, minute);
            } else {
                throw new DateTimeParseException("Text cannot be parsed to a DateTime.", s, idx + 6);
            }
        }
        if (c == 'Z') {
            if (s.length() == idx + 1) {
                return ZoneOffset.UTC;
            } else {
                throw new DateTimeParseException("Text cannot be parsed to a DateTime.", s, idx + 1);
            }
        }
        throw new DateTimeParseException("Text cannot be parsed to a DateTime. Expected one of `+`, `-` or `Z`.", s, idx);
    }

    default Keyword type() {
        return TYPE;
    }

    Date toDate();
}
