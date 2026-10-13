(ns blaze.db.impl.cache
  "Functions shared by the caches of the database."
  (:require
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defn max-size-in-bytes
  "Returns the size in bytes of `max-size-ratio` of the max memory of the JVM
  and logs it as memory size of the cache with `name`."
  [name max-size-ratio]
  (let [max-size-ratio (double max-size-ratio)
        max-size-in-bytes (long (* (.maxMemory (Runtime/getRuntime)) max-size-ratio))]
    (log/info (format "Create %s with a memory size of %d MiB (%d%% of max memory size)"
                      name (bit-shift-right max-size-in-bytes 20)
                      (long (* 100 max-size-ratio))))
    max-size-in-bytes))
