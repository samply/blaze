package blaze.fhir.spec.type;

import java.lang.String;
import java.util.List;

public interface Element extends Base {

    String id();

    List<Extension> extension();
}
