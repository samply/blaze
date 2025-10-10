package blaze.fhir.spec.type;

import clojure.lang.ISeq;
import clojure.lang.PersistentList;

import static blaze.fhir.spec.type.Base.appendElement;

abstract class PrimitiveElement extends AbstractElement implements Primitive {

    /**
     * Memory size of most primitive types.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - value reference
     */
    protected static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 2 * MEM_SIZE_REFERENCE;

    protected PrimitiveElement(ExtensionData extensionData) {
        super(extensionData);
    }

    @Override
    public boolean isInterned() {
        return extensionData.isInterned() && !hasValue();
    }

    public boolean isExtended() {
        return extensionData.isNotEmpty();
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        return key == VALUE ? value() : extensionData.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, VALUE, value());
        return extensionData.append(seq);
    }

    @Override
    public int memSize() {
        return isInterned() ? 0 : MEM_SIZE_OBJECT + extensionData.memSize();
    }
}
