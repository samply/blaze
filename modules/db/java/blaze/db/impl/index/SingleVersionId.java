package blaze.db.impl.index;

import static java.util.Objects.requireNonNull;

public record SingleVersionId(String id, int hashPrefix) {

    public SingleVersionId {
        requireNonNull(id);
    }

    public static SingleVersionId fromResourceHandle(ResourceHandle resourceHandle) {
        return new SingleVersionId(resourceHandle.id(), resourceHandle.hashPrefix());
    }

    public boolean matchesHashPrefix(ResourceHandle resourceHandle) {
        return hashPrefix == resourceHandle.hashPrefix();
    }

    @Override
    public String toString() {
        return "SingleVersionId{" +
                "id=" + id +
                ", hashPrefix=0x" + Integer.toHexString(hashPrefix) +
                '}';
    }
}
