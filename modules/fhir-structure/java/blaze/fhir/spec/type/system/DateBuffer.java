package blaze.fhir.spec.type.system;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

/**
 * Buffer building the ASCII representation of a system date value.
 * <p>
 * Every part has a fixed width, so the parts can be written into a byte array
 * directly, without going through a {@link StringBuilder} and a {@link String}
 * first. The buffer tracks its own position, so the parts only have to be
 * appended in the order they appear in.
 * <p>
 * Mutable and not thread safe. Only meant to be used inside a single
 * {@code writeTo} call, where escape analysis can remove it again.
 */
final class DateBuffer {

    private final byte[] buffer;
    private int index;

    DateBuffer(int size) {
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
