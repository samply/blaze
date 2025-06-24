package blaze.util;

/**
 * Java implementation of functionality used in blaze.util/str.
 * <p>
 * This Java implementation is nessesary because it's not possible to use the +
 * operator in Clojure. The + operator is the gateway into
 * <a href="https://openjdk.org/jeps/280">JEP 280</a> and other efforts to
 * optimize string concatenation.
 */
public class Str {

    public static String string(Object x) {
        return x == null ? "" : x.toString();
    }

    public static String concat(String s1, String s2) {
        return s1 + s2;
    }

    public static String concat(String s1, String s2, String s3) {
        return s1 + s2 + s3;
    }

    public static String concat(String s1, String s2, String s3, String s4) {
        return s1 + s2 + s3 + s4;
    }

    public static String concat(String s1, String s2, String s3, String s4, String s5) {
        return s1 + s2 + s3 + s4 + s5;
    }

    public static String concat(String s1, String s2, String s3, String s4, String s5, String s6) {
        return s1 + s2 + s3 + s4 + s5 + s6;
    }
}
