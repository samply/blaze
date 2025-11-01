package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.time.chrono.IsoChronology;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalUnit;

import static blaze.fhir.spec.type.Base.MEM_SIZE_OBJECT_HEADER;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

@SuppressWarnings("UnstableApiUsage")
public final class DateTimeYearMonth implements DateTime, Comparable<DateTimeYearMonth> {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 byte - year
     * 4 byte - month
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 8;

    private final int year;
    private final int month;

    private DateTimeYearMonth(int year, int month) {
        this.year = year;
        this.month = month;
    }

    public static DateTimeYearMonth of(int year, int month) {
        YEAR_RANGE.checkValidValue(year, YEAR);
        MONTH_OF_YEAR.checkValidValue(month);
        return new DateTimeYearMonth(year, month);
    }

    static DateTimeYearMonth parse(String text) {
        try {
            return of(Integer.parseInt(text.substring(0, 4)),
                    Integer.parseInt(text.substring(5, 7)));
        } catch (NumberFormatException e) {
            throw new DateTimeException("Invalid date component: " + e.getMessage());
        }
    }

    private static DateTimeYearMonth fromYearMonth(YearMonth yearMonth) {
        return of(yearMonth.getYear(), yearMonth.getMonthValue());
    }

    public int year() {
        return year;
    }

    public int month() {
        return month;
    }

    /**
     * Checks if the year is a leap year, according to the ISO proleptic
     * calendar system rules.
     * <p>
     * This method applies the current rules for leap years across the whole time-line.
     * In general, a year is a leap year if it is divisible by four without
     * remainder. However, years divisible by 100, are not leap years, with
     * the exception of years divisible by 400 which are.
     * <p>
     * For example, 1904 is a leap year it is divisible by 4.
     * 1900 was not a leap year as it is divisible by 100, however 2000 was a
     * leap year as it is divisible by 400.
     * <p>
     * The calculation is proleptic - applying the same rules into the far future and far past.
     * This is historically inaccurate, but is correct for the ISO-8601 standard.
     *
     * @return true if the year is leap, false otherwise
     */
    public boolean isLeapYear() {
        return IsoChronology.INSTANCE.isLeapYear(year);
    }

    /**
     * Returns the length of the month represented by this date.
     * <p>
     * This returns the length of the month in days.
     * For example, a date in January would return 31.
     *
     * @return the length of the month in days
     */
    public int lengthOfMonth() {
        switch (month) {
            case 2:
                return (isLeapYear() ? 29 : 28);
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    @Override
    public void hashInto(PrimitiveSink sink) {
        sink.putByte((byte) 6);
        sink.putInt(year);
        sink.putInt(month);
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT;
    }

    public DateYearMonth toDate() {
        return DateYearMonth.of(year, month);
    }

    public DateTimeDate atStartOfMonth() {
        return new DateTimeDate(year, month, 1);
    }

    public DateTimeDate atEndOfMonth() {
        return new DateTimeDate(year, month, lengthOfMonth());
    }

    @Override
    public boolean isSupported(TemporalUnit unit) {
        return YearMonth.of(year, month).isSupported(unit);
    }

    @Override
    public Temporal with(TemporalField field, long newValue) {
        return fromYearMonth(YearMonth.of(year, month).with(field, newValue));
    }

    @Override
    public Temporal plus(long amountToAdd, TemporalUnit unit) {
        return fromYearMonth(YearMonth.of(year, month).plus(amountToAdd, unit));
    }

    /**
     * Returns a copy of this {@code DateTimeYearMonth} with the specified number of months added.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param monthsToAdd the months to add, may be negative
     * @return a {@code DateTimeYearMonth} based on this year-month with the months added, not null
     * @throws DateTimeException if the result exceeds the supported range
     */
    public DateTimeYearMonth plusMonths(int monthsToAdd) {
        if (monthsToAdd == 0) {
            return this;
        }
        int monthCount = year * 12 + (month - 1);
        int calcMonths = monthCount + monthsToAdd;
        int newYear = YEAR_RANGE.checkValidIntValue(Math.floorDiv(calcMonths, 12), YEAR);
        int newMonth = Math.floorMod(calcMonths, 12) + 1;
        return new DateTimeYearMonth(newYear, newMonth);
    }

    @Override
    public long until(Temporal endExclusive, TemporalUnit unit) {
        return YearMonth.of(year, month).until(endExclusive, unit);
    }

    @Override
    public boolean isSupported(TemporalField field) {
        return YearMonth.of(year, month).isSupported(field);
    }

    @Override
    public long getLong(TemporalField field) {
        return YearMonth.of(year, month).getLong(field);
    }

    @Override
    public int compareTo(DateTimeYearMonth other) {
        int cmp = (year - other.year);
        if (cmp == 0) {
            cmp = (month - other.month);
        }
        return cmp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof DateTimeYearMonth that && year == that.year && month == that.month;
    }

    @Override
    public int hashCode() {
        return year ^ (month << 27);
    }

    @Override
    public String toString() {
        int absYear = Math.abs(year);
        StringBuilder buf = new StringBuilder(9);
        if (absYear < 1000) {
            buf.append(year + 10000).deleteCharAt(0);
        } else {
            buf.append(year);
        }
        return buf.append(month < 10 ? "-0" : "-")
                .append(month)
                .toString();
    }
}
