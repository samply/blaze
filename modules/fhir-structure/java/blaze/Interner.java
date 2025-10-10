package blaze;

public interface Interner<K, V> {

    V intern(K key);
}
