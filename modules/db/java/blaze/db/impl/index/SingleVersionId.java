package blaze.db.impl.index;

import blaze.ByteString;
import blaze.fhir.Hash;

import static java.util.Objects.requireNonNull;

public record SingleVersionId(ByteString id, int hashPrefix) {

    public SingleVersionId {
        requireNonNull(id);
    }

    public boolean matchesHash(Hash hash) {
        return hashPrefix == hash.prefix();
    }

    @Override
    public String toString() {
        return "SingleVersionId{" +
                "id=" + id +
                ", hashPrefix=0x" + Integer.toHexString(hashPrefix) +
                '}';
    }
}
