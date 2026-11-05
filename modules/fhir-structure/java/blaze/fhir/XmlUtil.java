package blaze.fhir;

import java.io.IOException;
import java.io.Writer;

public final class XmlUtil {

    private XmlUtil() {
    }

    private static boolean validXmlChar(char c) {
        return c == 0x09 || c == 0x0A || c == 0x0D ||
                (0x20 <= c && c <= 0xD7FF) ||
                (0xE000 <= c && c <= 0xFFFD);
    }

    public static void writeEscaped(Writer writer, String s) throws IOException {
        if (writer instanceof XmlUtf8Writer xmlWriter) {
            xmlWriter.writeEscaped(s);
            return;
        }
        int len = s.length();
        int start = 0;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> {
                    if (start < i) writer.write(s, start, i - start);
                    writer.write("&amp;");
                    start = i + 1;
                }
                case '<' -> {
                    if (start < i) writer.write(s, start, i - start);
                    writer.write("&lt;");
                    start = i + 1;
                }
                case '>' -> {
                    if (start < i) writer.write(s, start, i - start);
                    writer.write("&gt;");
                    start = i + 1;
                }
                case '"' -> {
                    if (start < i) writer.write(s, start, i - start);
                    writer.write("&quot;");
                    start = i + 1;
                }
                default -> {
                    if (Character.isHighSurrogate(c)) {
                        int j = i + 1;
                        if (j < len && Character.isLowSurrogate(s.charAt(j))) {
                            i = j;
                        } else {
                            if (start < i) writer.write(s, start, i - start);
                            writer.write("?");
                            start = j;
                        }
                    } else if (Character.isLowSurrogate(c) || !validXmlChar(c)) {
                        if (start < i) writer.write(s, start, i - start);
                        writer.write("?");
                        start = i + 1;
                    }
                }
            }
        }
        if (start < len) writer.write(s, start, len - start);
    }
}
