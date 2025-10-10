package blaze.fhir.spec.type;

import clojure.lang.ISeq;
import clojure.lang.PersistentList;

import java.lang.String;
import java.util.List;

import static blaze.fhir.spec.type.Base.appendElement;

abstract class PrimitiveElement extends Element implements Primitive {

    public PrimitiveElement(String id, List<Extension> extension) {
        super(id, extension);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == VALUE) return value();
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, VALUE, value());
        return appendBase(seq);
    }
}
