package blaze.fhir.spec.type;

public interface PrimitiveType<T> extends FhirType {

    T value();

    Object toXml();
}
