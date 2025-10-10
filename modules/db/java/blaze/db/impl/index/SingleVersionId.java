package blaze.db.impl.index;

import blaze.ByteString;

import static java.util.Objects.requireNonNull;

public record SingleVersionId(ByteString id, int hashPrefix) {

    public SingleVersionId {
        requireNonNull(id);
    }

    @Override
    public String toString() {
        return "SingleVersionId{" +
                "id=" + id +
                ", hashPrefix=0x" + Integer.toHexString(hashPrefix) +
                '}';
    }
}
