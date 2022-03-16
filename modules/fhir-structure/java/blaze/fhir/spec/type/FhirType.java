package blaze.fhir.spec.type;

import clojure.lang.*;
import com.google.common.hash.PrimitiveSink;

@SuppressWarnings("UnstableApiUsage")
public interface FhirType extends IType {

    Var VAR_MEM_SIZE = RT.var("blaze.fhir.spec.type.protocols", "-mem-size");

    static PersistentVector appendExtensionReferences(PersistentVector extension, PersistentVector init) {
        return (PersistentVector) extension.reduce(
                new AFn() {
                    @Override
                    public Object invoke(Object res, Object extension) {
                        return ((Extension) extension).references().reduce(
                                new AFn() {
                                    @Override
                                    public Object invoke(Object res, Object ref) {
                                        return ((PersistentVector) res).cons(ref);
                                    }
                                },
                                res
                        );
                    }
                },
                init
        );
    }

    Keyword fhirType();

    void hashInto(PrimitiveSink sink);

    PersistentVector references();

    int memSize();

    static int memSize(Object x) {
        Object o = ((IFn) VAR_MEM_SIZE.getRawRoot()).invoke(x);
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }
}
