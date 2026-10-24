package blaze.db.impl.index;

import static java.util.Objects.requireNonNull;

/**
 * The key of a resource consisting of its type ID and its logical FHIR ID.
 * <p>
 * Used as key of the resource handle cache. Holding the ID as String allows the
 * cache to share the ID with the resource handle stored under that key.
 */
public final class ResourceKey {

    private final int tid;
    private final String id;

    public ResourceKey(int tid, String id) {
        this.tid = tid;
        this.id = requireNonNull(id);
    }

    /**
     * Returns the key of {@code resourceHandle}, sharing its id instance.
     */
    public static ResourceKey fromHandle(ResourceHandle resourceHandle) {
        return new ResourceKey(resourceHandle.tid(), resourceHandle.id());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof ResourceKey that && tid == that.tid && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return 31 * tid + id.hashCode();
    }

    @Override
    public String toString() {
        return "ResourceKey[tid = " + tid + ", id = " + id + ']';
    }
}
