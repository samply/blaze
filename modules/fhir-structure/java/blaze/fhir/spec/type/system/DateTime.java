package blaze.fhir.spec.type.system;

import clojure.lang.Keyword;
import clojure.lang.RT;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.time.*;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.Temporal;
import java.time.temporal.ValueRange;

import static blaze.fhir.spec.type.Base.MEM_SIZE_OBJECT_HEADER;
import static blaze.fhir.spec.type.Base.MEM_SIZE_REFERENCE;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;

public interface DateTime extends JavaSystemType, Temporal {

    byte HASH_MARKER = 6;

    DateTimeFormatter LOCAL_DATE_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(ISO_LOCAL_DATE)
            .appendLiteral('T')
            .append(Times.LOCAL_TIME)
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT)
            .withChronology(IsoChronology.INSTANCE);

    DateTimeFormatter DATE_TIME = new DateTimeFormatterBuilder()
            .append(LOCAL_DATE_TIME)
            .appendOffsetId()
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT)
            .withChronology(IsoChronology.INSTANCE);

    Keyword TYPE = RT.keyword("system", "date-time");

    ValueRange YEAR_RANGE = ValueRange.of(1, 9999);

    /**
     * Memory size LocalDate.
     * <p>
     * 8 byte - object header
     * 4 byte - year
     * 2 byte - month
     * 2 byte - day
     */
    int MEM_SIZE_OBJECT_LOCAL_DATE = MEM_SIZE_OBJECT_HEADER + 8;

    /**
     * Memory size LocalDate.
     * <p>
     * 8 byte - object header
     * 1 byte - hour
     * 1 byte - minute
     * 1 byte - second
     * 4 byte - nano
     */
    int MEM_SIZE_OBJECT_LOCAL_TIME = MEM_SIZE_OBJECT_HEADER + 8;

    /**
     * Memory size LocalDateTime.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - LocalDate reference
     * 4 or 8 byte - LocalTime reference
     */
    int MEM_SIZE_OBJECT_LOCAL_DATE_TIME = MEM_SIZE_OBJECT_HEADER + 2 * MEM_SIZE_REFERENCE + MEM_SIZE_OBJECT_LOCAL_DATE +
            MEM_SIZE_OBJECT_LOCAL_TIME;

    /**
     * Memory size OffsetDateTime.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - LocalDateTime reference
     * 4 or 8 byte - ZoneOffset reference (shared)
     */
    int MEM_SIZE_OBJECT_OFFSET_DATE_TIME = MEM_SIZE_OBJECT_HEADER + 2 * MEM_SIZE_REFERENCE + MEM_SIZE_OBJECT_LOCAL_DATE_TIME;

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

    @SuppressWarnings("UnstableApiUsage")
    static void hashInto(Temporal value, PrimitiveSink sink) {
        if (value instanceof JavaSystemType systemType) {
            systemType.hashInto(sink);
        }
        if (value instanceof LocalDateTime dateTime) {
            sink.putByte(HASH_MARKER);
            sink.putInt(dateTime.getYear());
            sink.putInt(dateTime.getMonthValue());
            sink.putInt(dateTime.getDayOfMonth());
            sink.putInt(dateTime.getHour());
            sink.putInt(dateTime.getMinute());
            sink.putInt(dateTime.getSecond());
            sink.putInt(dateTime.getNano());
        }
        if (value instanceof OffsetDateTime dateTime) {
            hashInto(dateTime.toLocalDateTime(), sink);
            sink.putInt(dateTime.getOffset().getTotalSeconds());
        }
    }

    static int memSize(Temporal value) {
        if (value == null) return 0;
        return switch (value) {
            case JavaSystemType systemType -> systemType.memSize();
            case LocalDateTime x -> MEM_SIZE_OBJECT_LOCAL_DATE_TIME;
            case OffsetDateTime x -> MEM_SIZE_OBJECT_OFFSET_DATE_TIME;
            default -> 0;
        };
    }

    static String toString(Temporal value) {
        return switch (value) {
            case LocalDateTime dateTime -> LOCAL_DATE_TIME.format(dateTime);
            case OffsetDateTime dateTime -> DATE_TIME.format(dateTime);
            default -> value.toString();
        };
    }

    static void writeTo(Temporal value, JsonGenerator generator) throws IOException {
        switch (value) {
            case LocalDateTime dateTime -> {
                var appendable = new AsciiByteArrayAppendable(29);
                LOCAL_DATE_TIME.formatTo(dateTime, appendable);
                generator.writeRawUTF8String(appendable.toByteArray(), 0, appendable.length());
            }
            case OffsetDateTime dateTime -> {
                var appendable = new AsciiByteArrayAppendable(35);
                DATE_TIME.formatTo(dateTime, appendable);
                generator.writeRawUTF8String(appendable.toByteArray(), 0, appendable.length());
            }
            default -> generator.writeString(value.toString());
        }
    }

    default Keyword type() {
        return TYPE;
    }

    Date toDate();
}
