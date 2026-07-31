package blaze.fhir.spec.type.system;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Buffer building the ASCII representation of a system date or date-time value.
 * <p>
 * Every part has a fixed width, so the parts can be written into a byte array
 * directly, without going through a {@link StringBuilder} and a {@link String}
 * first. The buffer tracks its own position, so the parts only have to be
 * appended in the order they appear in.
 * <p>
 * Mutable and not thread safe. Only meant to be used inside a single
 * {@code writeTo} call, where escape analysis can remove it again.
 */
final class DateTimeBuffer {

    /**
     * Size of the longest local date-time, {@code 2020-01-01T00:00:00.123456789}.
     */
    static final int SIZE_LOCAL_DATE_TIME = 29;

    /**
     * Size of the longest offset date-time,
     * {@code 2020-01-01T00:00:00.123456789+01:02:03}.
     */
    static final int SIZE_OFFSET_DATE_TIME = SIZE_LOCAL_DATE_TIME + 9;

    private final byte[] buffer;
    private int index;

    DateTimeBuffer(int size) {
        buffer = new byte[size];
    }

    /**
     * Appends {@code year} as four digits.
     * <p>
     * The year has to be in the range 1-9999, which every system date value
     * ensures by checking {@code YEAR_RANGE}.
     */
    void appendYear(int year) {
        buffer[index++] = (byte) ('0' + year / 1000);
        buffer[index++] = (byte) ('0' + year / 100 % 10);
        buffer[index++] = (byte) ('0' + year / 10 % 10);
        buffer[index++] = (byte) ('0' + year % 10);
    }

    /**
     * Appends the dash separating the year, month and day parts.
     */
    void appendDash() {
        buffer[index++] = '-';
    }

    /**
     * Appends {@code month} as two digits.
     */
    void appendMonth(int month) {
        appendTwoDigits(month);
    }

    /**
     * Appends {@code day} as two digits.
     */
    void appendDay(int day) {
        appendTwoDigits(day);
    }

    /**
     * Appends the whole local date-time, including the {@code T} separating its
     * date and time part.
     */
    void appendLocalDateTime(LocalDateTime dateTime) {
        appendYear(dateTime.getYear());
        appendDash();
        appendMonth(dateTime.getMonthValue());
        appendDash();
        appendDay(dateTime.getDayOfMonth());
        buffer[index++] = 'T';
        appendHour(dateTime.getHour());
        appendColon();
        appendMinute(dateTime.getMinute());
        appendColon();
        appendSecond(dateTime.getSecond());
        appendFraction(dateTime.getNano());
    }

    /**
     * Appends the colon separating the hour, minute and second parts.
     */
    void appendColon() {
        buffer[index++] = ':';
    }

    /**
     * Appends {@code hour} as two digits.
     */
    void appendHour(int hour) {
        appendTwoDigits(hour);
    }

    /**
     * Appends {@code minute} as two digits.
     */
    void appendMinute(int minute) {
        appendTwoDigits(minute);
    }

    /**
     * Appends {@code second} as two digits.
     */
    void appendSecond(int second) {
        appendTwoDigits(second);
    }

    /**
     * Appends {@code nano} as a decimal point followed by up to nine digits
     * with trailing zeros removed, or nothing at all if it is zero.
     * <p>
     * Produces the same output as
     * {@code appendFraction(NANO_OF_SECOND, 0, 9, true)} does.
     */
    void appendFraction(int nano) {
        if (nano == 0) {
            return;
        }
        buffer[index++] = '.';
        for (int divisor = 100_000_000; divisor > 0; divisor /= 10) {
            buffer[index++] = (byte) ('0' + nano / divisor % 10);
        }
        // terminates because at least one digit of a non-zero nano is non-zero
        while (buffer[index - 1] == '0') {
            index--;
        }
    }

    /**
     * Appends {@code offset} as {@code Z} if it is UTC and as
     * {@code (+|-)HH:MM} otherwise, with the second part only appended if it is
     * non-zero.
     * <p>
     * Produces the same output as {@code appendOffsetId()} does.
     */
    void appendOffset(ZoneOffset offset) {
        int totalSeconds = offset.getTotalSeconds();
        if (totalSeconds == 0) {
            buffer[index++] = 'Z';
            return;
        }
        buffer[index++] = (byte) (totalSeconds < 0 ? '-' : '+');
        int absSeconds = Math.abs(totalSeconds);
        appendTwoDigits(absSeconds / 3600);
        appendColon();
        appendTwoDigits(absSeconds / 60 % 60);
        int seconds = absSeconds % 60;
        if (seconds != 0) {
            appendColon();
            appendTwoDigits(seconds);
        }
    }

    private void appendTwoDigits(int value) {
        buffer[index++] = (byte) ('0' + value / 10);
        buffer[index++] = (byte) ('0' + value % 10);
    }

    /**
     * Writes everything appended so far as raw UTF-8 string.
     */
    void writeTo(JsonGenerator generator) throws IOException {
        generator.writeRawUTF8String(buffer, 0, index);
    }
}
