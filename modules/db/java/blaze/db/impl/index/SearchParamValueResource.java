package blaze.db.impl.index;

import blaze.ByteString;
import blaze.fhir.Hash;
import clojure.lang.IPersistentVector;
import clojure.lang.Tuple;

import java.nio.ByteBuffer;

public interface SearchParamValueResource {

    int HASH_PREFIX_SIZE = Hash.PREFIX_SIZE;
    int C_HASH_SIZE = Integer.BYTES;
    int TID_SIZE = Integer.BYTES;
    int BASE_KEY_SIZE = C_HASH_SIZE + TID_SIZE;

    static int idSize(ByteBuffer buffer) {
        return buffer.get(buffer.limit() - HASH_PREFIX_SIZE - 1);
    }

    static int hashPrefix(ByteBuffer buffer) {
        return buffer.getInt(buffer.position() + 1);
    }

    static IPersistentVector decodeKey(ByteBuffer buffer) {
        int idSize = idSize(buffer);
        int allSize = buffer.remaining();
        int prefixSize = allSize - 2 - idSize - HASH_PREFIX_SIZE;
        var prefix = ByteString.copyFrom(buffer, prefixSize);
        buffer.position(buffer.position() + 1);
        var id = ByteString.copyFrom(buffer, idSize);
        return Tuple.create(prefix, new SingleVersionId(id, hashPrefix(buffer)));
    }

    static IPersistentVector decodeValueSingleVersionId(ByteBuffer buffer) {
        buffer.position(BASE_KEY_SIZE);
        return decodeKey(buffer);
    }

    static SingleVersionId decodeSingleVersionId(ByteBuffer buffer) {
        int idSize = idSize(buffer);
        int allSize = idSize + HASH_PREFIX_SIZE + 1;
        buffer.position(buffer.limit() - allSize);
        var id = ByteString.copyFrom(buffer, idSize);
        return new SingleVersionId(id, hashPrefix(buffer));
    }
}
