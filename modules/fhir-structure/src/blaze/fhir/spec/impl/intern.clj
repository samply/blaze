(ns blaze.fhir.spec.impl.intern
  (:import
   [clojure.lang Util]
   [java.lang.ref ReferenceQueue WeakReference]
   [java.util.concurrent ConcurrentHashMap]))

(set! *warn-on-reflection* true)

(defn intern-value
  "Returns a function of arity 1 that creates a value using `create-fn` only if
  no value exists already in an internal cache using the argument as key.

  Should be used the following way:

  (def intern-foo
    \"Creates interned instances of foo.\"
    (intern-value foo))

  Holds weak references to values so that they can be collected under high
  pressure. The algorithm was taken from Clojure 1.10.3 keyword interning."
  [create-fn]
  (let [cache (ConcurrentHashMap.)
        rq (ReferenceQueue.)]
    (fn [key]
      (loop [ref (.get cache key)]
        (if ref
          (if-let [existing-value (.get ^WeakReference ref)]
            existing-value
            (do (.remove cache key ref)
                (recur (.get cache key))))
          (let [new-value (create-fn key)]
            (Util/clearCache rq cache)
            (if-let [ref (.putIfAbsent cache key (WeakReference. new-value rq))]
              (if-let [existing-value (.get ^WeakReference ref)]
                existing-value
                (do (.remove cache key ref)
                    (recur (.get cache key))))
              new-value)))))))
