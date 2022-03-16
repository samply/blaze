package blaze.fhir.spec.type;

import clojure.lang.Counted;
import clojure.lang.IKVReduce;
import clojure.lang.ILookup;
import clojure.lang.MapEntry;

public interface ComplexType extends FhirType, ILookup, Counted, Iterable<MapEntry>, IKVReduce {
}
