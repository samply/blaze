(ns blaze.job.disk-perf.score
  "Derives a single 0..100 disk performance score from the raw benchmark
  numbers by normalizing each dimension against the performance of a good
  local NVMe SSD.")

(set! *warn-on-reflection* true)

(def reference-iops-per-reader
  "Random read IOPS at 16 KiB block size expected from a single reader on a
  good local NVMe SSD, corresponding to a read latency of 100 µs."
  10000.0)

(def reference-max-concurrency
  "Number of concurrent readers up to which a good local NVMe SSD is expected
  to scale random read IOPS linearly.

  A Samsung 990 Pro measures 18 k IOPS at one reader and 390 k IOPS at 32
  readers, so the linear reference curve capped at 32 readers (320 k IOPS)
  leaves headroom for good local NVMe SSDs while storage behind a network hop
  falls short at every level."
  32)

(defn- reference-read-iops ^double [concurrency]
  (* reference-iops-per-reader
     (double (min (long concurrency) reference-max-concurrency))))

(def reference-seq-write-bytes-per-second
  "Sustained sequential write throughput expected from a good local NVMe SSD.

  RocksDB compactions and memtable flushes write large files sequentially."
  1.0e9)

(def reference-fsync-rate
  "Number of small append + fsync operations per second expected from a good
  local NVMe SSD.

  The transaction and resource stores fsync their write-ahead log on every
  write batch, so this corresponds to a WAL commit latency of about 1 ms."
  1000.0)

(defn- sub-score ^double [measured reference]
  (min 1.0 (/ (double measured) (double reference))))

(defn- read-score
  "Returns the read sub-score of `read-runs`, the geometric mean with equal
  weights of the sub-scores of all runs, each normalized against the
  reference curve.

  The concurrency levels of the sweep are spaced in powers of two, so equal
  weights per level weight the low-concurrency latency regime and the
  high-concurrency throughput regime about equally. The geometric mean
  ensures that one collapsed level drags down the whole read sub-score."
  ^double [read-runs]
  (-> (reduce
       (fn [product {:keys [concurrency iops]}]
         (* (double product) (sub-score iops (reference-read-iops concurrency))))
       1.0 read-runs)
      (Math/pow (/ 1.0 (count read-runs)))))

(defn score
  "Returns the overall disk performance score between 0 and 100 for
  `measurement`.

  The score is a weighted geometric mean of the three sub-scores with random
  reads weighted at one half and sequential writes and fsyncs at one quarter
  each. Random reads dominate Blaze's interactive query load, and the
  geometric mean ensures that one collapsed dimension collapses the whole
  score. The read sub-score uses all runs of the random read concurrency
  sweep."
  {:arglists '([measurement])}
  [{:keys [read-runs seq-write-bytes-per-second fsync-rate]}]
  (* 100.0
     (Math/pow (read-score read-runs) 0.5)
     (Math/pow (sub-score seq-write-bytes-per-second reference-seq-write-bytes-per-second) 0.25)
     (Math/pow (sub-score fsync-rate reference-fsync-rate) 0.25)))

(defn rating
  "Returns the rating code for `score`."
  [score]
  (condp <= score
    80.0 "excellent"
    50.0 "good"
    25.0 "acceptable"
    "insufficient"))
