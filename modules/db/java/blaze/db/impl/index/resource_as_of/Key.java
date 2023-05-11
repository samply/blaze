package blaze.db.impl.index.resource_as_of;

import com.google.protobuf.ByteString;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public final class Key {

    public final int tid;
    public final ByteString id;
    public final long t;

    public Key(int tid, ByteString id, long t) {
        this.tid = tid;
        this.id = requireNonNull(id);
        this.t = t;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Key) {
            Key other = ((Key) o);
            return t == other.t && tid == other.tid && Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = tid;
        result = 31 * result + id.hashCode();
        return 31 * result + Long.hashCode(t);
    }
}
