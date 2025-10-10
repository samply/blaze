package blaze.fhir.spec.type.system;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.chrono.IsoChronology;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalUnit;

import static blaze.fhir.spec.type.Base.MEM_SIZE_OBJECT_HEADER;
import static java.time.temporal.ChronoField.*;

@SuppressWarnings("UnstableApiUsage")
public final class DateDate implements Date, Comparable<DateDate> {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 byte - year
     * 2 byte - month
     * 2 byte - day
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 8;

    /**
     * The number of days in a 400 year cycle.
     */
    private static final int DAYS_PER_CYCLE = 146097;

    /**
     * The number of days from year zero to year 1970.
     * There are five 400 year cycles from year zero to 2000.
     * There are 7 leap years from 1970 to 2000.
     */
    private static final int DAYS_0000_TO_1970 = (DAYS_PER_CYCLE * 5) - (30 * 365 + 7);

    private final int year;
    private final short month;
    private final short day;

    DateDate(int year, int month, int dayOfMonth) {
        this.year = year;
        this.month = (short) month;
        this.day = (short) dayOfMonth;
    }

    private static DateDate create(int year, int month, int dayOfMonth) {
        if (dayOfMonth > 28) {
            int dom = 31;
            switch (month) {
                case 2:
                    dom = (IsoChronology.INSTANCE.isLeapYear(year) ? 29 : 28);
                    break;
                case 4:
                case 6:
                case 9:
                case 11:
                    dom = 30;
                    break;
            }
            if (dayOfMonth > dom) {
                if (dayOfMonth == 29) {
                    throw new DateTimeException("Invalid date 'February 29' as '" + year + "' is not a leap year");
                } else {
                    throw new DateTimeException("Invalid date '" + Month.of(month).name() + " " + dayOfMonth + "'");
                }
            }
        }
        return new DateDate(year, month, dayOfMonth);
    }

    public static DateDate of(int year, int month, int dayOfMonth) {
        YEAR_RANGE.checkValidValue(year, YEAR);
        MONTH_OF_YEAR.checkValidValue(month);
        DAY_OF_MONTH.checkValidValue(dayOfMonth);
        return create(year, month, dayOfMonth);
    }

    /**
     * Obtains an instance of {@code DateDate} from the epoch day count.
     * <p>
     * This returns a {@code DateDate} with the specified epoch-day.
     * The {@link ChronoField#EPOCH_DAY EPOCH_DAY} is a simple incrementing count
     * of days where day 0 is 1970-01-01. Negative numbers represent earlier days.
     *
     * @param epochDay the Epoch Day to convert, based on the epoch 1970-01-01
     * @return the local date, not null
     * @throws DateTimeException if the epoch day exceeds the supported date range
     */
    public static DateDate ofEpochDay(int epochDay) {
        int zeroDay = epochDay + DAYS_0000_TO_1970;
        // find the march-based year
        zeroDay -= 60;  // adjust to 0000-03-01 so leap day is at end of four year cycle
        int adjust = 0;
        if (zeroDay < 0) {
            // adjust negative years to positive for calculation
            int adjustCycles = (zeroDay + 1) / DAYS_PER_CYCLE - 1;
            adjust = adjustCycles * 400;
            zeroDay += -adjustCycles * DAYS_PER_CYCLE;
        }
        int yearEst = (400 * zeroDay + 591) / DAYS_PER_CYCLE;
        int doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
        if (doyEst < 0) {
            // fix estimate
            yearEst--;
            doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
        }
        yearEst += adjust;  // reset any negative year
        int marchDoy0 = doyEst;

        // convert march-based values back to january-based
        int marchMonth0 = (marchDoy0 * 5 + 2) / 153;
        int month = (marchMonth0 + 2) % 12 + 1;
        int dom = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1;
        yearEst += marchMonth0 / 10;

        // check year now we are certain it is correct
        int year = YEAR_RANGE.checkValidIntValue(yearEst, YEAR);
        return new DateDate(year, month, dom);
    }

    static DateDate parse(String text) {
        try {
            return of(Integer.parseInt(text.substring(0, 4)),
                    Integer.parseInt(text.substring(5, 7)),
                    Integer.parseInt(text.substring(8, 10)));
        } catch (NumberFormatException e) {
            throw new DateTimeException("Invalid date component: " + e.getMessage());
        }
    }

    public static DateDate fromLocalDate(LocalDate date) {
        return of(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    public int year() {
        return year;
    }

    public int month() {
        return month;
    }

    public int day() {
        return day;
    }

    @Override
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        sink.putInt(year);
        sink.putInt(month);
        sink.putInt(day);
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT;
    }

    public DateTimeDate toDateTime() {
        return DateTimeDate.of(year, month, day);
    }

    public LocalDateTime atStartOfDay() {
        return LocalDate.of(year, month, day).atStartOfDay();
    }

    public LocalDateTime atTime(int hour, int minute, int second) {
        return LocalDate.of(year, month, day).atTime(hour, minute, second);
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

    public int toEpochDay() {
        int y = year;
        int m = month;
        int total = 0;
        total += 365 * y;
        if (y >= 0) {
            total += (y + 3) / 4 - (y + 99) / 100 + (y + 399) / 400;
        } else {
            total -= y / -4 - y / -100 + y / -400;
        }
        total += ((367 * m - 362) / 12);
        total += day - 1;
        if (m > 2) {
            total--;
            if (!isLeapYear()) {
                total--;
            }
        }
        return total - DAYS_0000_TO_1970;
    }

    @Override
    public boolean isSupported(TemporalUnit unit) {
        return LocalDate.of(year, month, day).isSupported(unit);
    }

    @Override
    public Temporal with(TemporalField field, long newValue) {
        return fromLocalDate(LocalDate.of(year, month, day).with(field, newValue));
    }

    @Override
    public Temporal plus(long amountToAdd, TemporalUnit unit) {
        return fromLocalDate(LocalDate.of(year, month, day).plus(amountToAdd, unit));
    }

    /**
     * Returns a copy of this {@code DateDate} with the specified number of days added.
     * <p>
     * This method adds the specified amount to the days field incrementing the
     * month and year fields as necessary to ensure the result remains valid.
     * The result is only invalid if the maximum/minimum year is exceeded.
     * <p>
     * For example, 2008-12-31 plus one day would result in 2009-01-01.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param daysToAdd the days to add, may be negative
     * @return a {@code DateDate} based on this date with the days added, not null
     * @throws DateTimeException if the result exceeds the supported date range
     */
    public DateDate plusDays(int daysToAdd) {
        if (daysToAdd == 0) {
            return this;
        }
        int dom = day + daysToAdd;
        if (dom > 0) {
            if (dom <= 28) {
                return new DateDate(year, month, dom);
            } else if (dom <= 59) { // 59th Jan is 28th Feb, 59th Feb is 31st Mar
                int monthLen = lengthOfMonth();
                if (dom <= monthLen) {
                    return new DateDate(year, month, dom);
                } else if (month < 12) {
                    return new DateDate(year, month + 1, dom - monthLen);
                } else {
                    YEAR_RANGE.checkValidValue(year + 1, YEAR);
                    return new DateDate(year + 1, 1, dom - monthLen);
                }
            }
        }

        int mjDay = toEpochDay() + daysToAdd;
        return DateDate.ofEpochDay(mjDay);
    }

    @Override
    public long until(Temporal endExclusive, TemporalUnit unit) {
        return LocalDate.of(year, month, day).until(endExclusive, unit);
    }

    @Override
    public boolean isSupported(TemporalField field) {
        return LocalDate.of(year, month, day).isSupported(field);
    }

    @Override
    public long getLong(TemporalField field) {
        return LocalDate.of(year, month, day).getLong(field);
    }

    @Override
    public int compareTo(DateDate other) {
        int cmp = (year - other.year);
        if (cmp == 0) {
            cmp = (month - other.month);
            if (cmp == 0) {
                cmp = (day - other.day);
            }
        }
        return cmp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof DateDate that && year == that.year && month == that.month && day == that.day;
    }

    @Override
    public int hashCode() {
        int yearValue = year;
        return (yearValue & 0xFFFFF800) ^ ((yearValue << 11) + ((int) month << 6) + ((int) day));
    }

    @Override
    public String toString() {
        int yearValue = year;
        int monthValue = month;
        int dayValue = day;
        int absYear = Math.abs(yearValue);
        StringBuilder buf = new StringBuilder(10);
        if (absYear < 1000) {
            buf.append(yearValue + 10000).deleteCharAt(0);
        } else {
            buf.append(yearValue);
        }
        return buf.append(monthValue < 10 ? "-0" : "-")
                .append(monthValue)
                .append(dayValue < 10 ? "-0" : "-")
                .append(dayValue)
                .toString();
    }

    public void writeTo(JsonGenerator generator) throws IOException {
        var appendable = new AsciiByteArrayAppendable(10);
        appendable.append(toString());
        generator.writeRawUTF8String(appendable.toByteArray(), 0, appendable.length());
    }
}
