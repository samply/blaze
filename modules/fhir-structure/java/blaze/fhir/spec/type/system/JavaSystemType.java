package blaze.fhir.spec.type.system;

import clojure.lang.Keyword;
import com.google.common.hash.PrimitiveSink;

public interface JavaSystemType {

    Keyword type();

    @SuppressWarnings("UnstableApiUsage")
    void hashInto(PrimitiveSink sink);

    int memSize();
}
