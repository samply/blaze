package blaze.db.kv;

/**
 * An {@link AutoCloseable} whose {@link #close() close} doesn't throw checked exceptions.
 * <p>
 * Snapshots and iterators of all key-value stores implement it, so that they can be closed without handling checked
 * exceptions. Their close must not throw at all, because closing a pooling snapshot closes all its iterators and then
 * the underlying snapshot, and an exception would leave the remaining ones open.
 */
public interface KvCloseable extends AutoCloseable {

    @Override
    void close();
}
