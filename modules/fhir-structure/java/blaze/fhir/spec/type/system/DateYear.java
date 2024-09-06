package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

import java.time.DateTimeException;
import java.time.Year;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalUnit;

import static java.time.temporal.ChronoField.YEAR;

@SuppressWarnings("UnstableApiUsage")
public final class DateYear implements Date, Comparable<DateYear> {

    private final int year;

    private DateYear(int year) {
        this.year = year;
    }

    public static DateYear of(int year) {
        YEAR_RANGE.checkValidValue(year, YEAR);
        return new DateYear(year);
    }

    static DateYear parse(String text) {
        try {
            return of(Integer.parseInt(text.substring(0, 4)));
        } catch (NumberFormatException e) {
            throw new DateTimeException("Invalid date component: " + e.getMessage());
        }
    }

    private static DateYear fromYear(Year year) {
        return of(year.getValue());
    }

    public int year() {
        return year;
    }

    @Override
    public void hashInto(PrimitiveSink sink) {
        sink.putByte((byte) 5);
        sink.putInt(year);
    }

    public DateTimeYear toDateTime() {
        return DateTimeYear.of(year);
    }

    public DateDate atStartOfYear() {
        return new DateDate(year, 1, 1);
    }

    public DateDate atEndOfYear() {
        return new DateDate(year, 12, 31);
    }

    @Override
    public boolean isSupported(TemporalUnit unit) {
        return Year.of(year).isSupported(unit);
    }

    @Override
    public Temporal with(TemporalField field, long newValue) {
        return fromYear(Year.of(year).with(field, newValue));
    }

    @Override
    public Temporal plus(long amountToAdd, TemporalUnit unit) {
        return fromYear(Year.of(year).plus(amountToAdd, unit));
    }

    /**
     * Returns a copy of this {@code DateYear} with the specified number of years added.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param yearsToAdd the years to add, may be negative
     * @return a {@code DateYear} based on this year with the years added, not null
     * @throws DateTimeException if the result exceeds the supported range
     */
    public DateYear plusYears(int yearsToAdd) {
        if (yearsToAdd == 0) {
            return this;
        }
        return of(year + yearsToAdd);
    }

    @Override
    public long until(Temporal endExclusive, TemporalUnit unit) {
        return Year.of(year).until(endExclusive, unit);
    }

    @Override
    public boolean isSupported(TemporalField field) {
        return Year.of(year).isSupported(field);
    }

    @Override
    public long getLong(TemporalField field) {
        return Year.of(year).getLong(field);
    }

    @Override
    public int compareTo(DateYear other) {
        return year - other.year;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof DateYear) {
            return year == ((DateYear) obj).year;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return year;
    }

    @Override
    public String toString() {
        int absYear = Math.abs(year);
        StringBuilder buf = new StringBuilder(6);
        if (absYear < 1000) {
            buf.append(year + 10000).deleteCharAt(0);
        } else {
            buf.append(year);
        }
        return buf.toString();
    }
}
