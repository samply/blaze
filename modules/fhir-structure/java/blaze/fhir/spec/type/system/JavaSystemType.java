package blaze.fhir.spec.type.system;

import clojure.lang.Keyword;
import com.google.common.hash.PrimitiveSink;

@SuppressWarnings("UnstableApiUsage")
public interface JavaSystemType {

    Keyword type();

    void hashInto(PrimitiveSink sink);
}
