(ns blaze.jvm-metrics-logger.impl
  (:require
   [clojure.string :as str]
   [taoensso.timbre :as log])
  (:import
   [java.lang.management GarbageCollectorMXBean ManagementFactory MemoryUsage]))

(set! *warn-on-reflection* true)

(defn- format-bytes [bytes]
  (if (>= bytes 1073741824)
    (format "%.1f GB" (/ bytes 1073741824.0))
    (format "%.0f MB" (/ bytes 1048576.0))))

(defn heap-pct ^long [^MemoryUsage usage]
  (let [max (.getMax usage)]
    (if (pos? max)
      (long (* 100 (/ (.getUsed usage) max)))
      0)))

(defn- format-gc [^GarbageCollectorMXBean gc]
  (let [cnt (.getCollectionCount gc)
        time-s (/ (.getCollectionTime gc) 1000.0)]
    (format "%s %d %s %.1fs" (.getName gc) cnt
            (if (= 1 cnt) "collection" "collections") time-s)))

(defn heap-usage-string [^MemoryUsage heap ^MemoryUsage non-heap gcs pct]
  (format "Heap: %s used / %s committed / %s max (%d%%), Non-Heap: %s used / %s committed%s"
          (format-bytes (.getUsed heap))
          (format-bytes (.getCommitted heap))
          (format-bytes (.getMax heap))
          pct
          (format-bytes (.getUsed non-heap))
          (format-bytes (.getCommitted non-heap))
          (if (seq gcs)
            (str ", GC: " (str/join ", " (map format-gc gcs)))
            "")))

(defn run-tick! [warn-threshold warn-factor tick-counter]
  (let [mbean (ManagementFactory/getMemoryMXBean)
        heap (.getHeapMemoryUsage mbean)
        non-heap (.getNonHeapMemoryUsage mbean)
        gcs (ManagementFactory/getGarbageCollectorMXBeans)
        pct (heap-pct heap)
        tick-count (swap! tick-counter inc)]
    (cond
      (>= pct warn-threshold)
      (log/warn "High heap usage -" (heap-usage-string heap non-heap gcs pct))

      (zero? (rem tick-count warn-factor))
      (log/debug (heap-usage-string heap non-heap gcs pct)))))
