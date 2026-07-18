(ns blaze.job.disk-perf.engine
  "In-process disk benchmark with an I/O profile similar to the one Blaze's
  RocksDB stores produce.

  Three phases run in order against temporary files in a database directory:

  * seq-write - writes the test file sequentially in 1 MiB chunks like RocksDB
    writes SST files during compactions and memtable flushes
  * rand-read - reads blocks of the configured block size at random offsets
    from the test file like RocksDB reads blocks on point queries, sweeping
    the number of concurrent readers over the configured concurrency levels
    to measure how the I/O operations per second scale with concurrency,
    using direct I/O to bypass the page cache where supported
  * fsync - writes small chunks sequentially to a write-ahead log file, each
    followed by an fsync, like the transaction and resource stores do on every
    write

  All functions return anomalies instead of throwing exceptions."
  (:refer-clojure :exclude [run!])
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.job.disk-perf.histogram :as histogram]
   [blaze.path :as path]
   [blaze.util :as u]
   [taoensso.timbre :as log])
  (:import
   [com.sun.nio.file ExtendedOpenOption]
   [java.io IOException]
   [java.nio ByteBuffer]
   [java.nio.channels FileChannel]
   [java.nio.file Files OpenOption Path StandardOpenOption]
   [java.util SplittableRandom]
   [java.util.concurrent CountDownLatch TimeUnit]))

(set! *warn-on-reflection* true)

(def ^:private ^:const ^long write-chunk-size u/mib)

(def ^:private ^:const ^long progress-interval (* 64 u/mib))

(def ^:private ^:const ^long fsync-chunk-size 8192)

(def ^:private ^:const ^long max-wal-size (* 128 u/mib))

(def ^:private ^:const ^long alignment 4096)

(def data-file-name "blaze-disk-perf-data.tmp")

(def wal-file-name "blaze-disk-perf-wal.tmp")

(defn check-free-space
  "Checks that the volume of `dir` has enough usable space for the test files
  of a benchmark run with a test file of `file-size` bytes plus headroom.

  Returns nil if there is enough space and an anomaly otherwise."
  [dir file-size]
  (ba/try-anomaly
   (let [usable (.getUsableSpace (Files/getFileStore (path/path dir)))
         needed (+ (long file-size) (quot (long file-size) 10) max-wal-size)]
     (when (< usable needed)
       (ba/conflict
        (format "Not enough space on the volume of `%s`. The benchmark needs %d bytes but only %d bytes are usable." dir needed usable))))))

(defn- random-bytes ^bytes [size]
  (let [bytes (byte-array size)]
    (.nextBytes (SplittableRandom.) bytes)
    bytes))

(defn- write-buffer
  "Returns a direct byte buffer of `size` bytes filled with random data.

  Random data ensures that filesystems with transparent compression can't
  cheat the benchmark."
  ^ByteBuffer [size]
  (doto (ByteBuffer/allocateDirect (int size))
    (.put (random-bytes size))
    (.flip)))

(defn- write-fully! [^FileChannel ch ^ByteBuffer buf]
  (while (.hasRemaining buf)
    (.write ch buf)))

(defn- write-fully-at! [^FileChannel ch ^ByteBuffer buf ^long pos]
  (loop [pos pos]
    (when (.hasRemaining buf)
      (recur (+ pos (.write ch buf pos))))))

(def ^:private write-open-options
  (into-array
   OpenOption
   [StandardOpenOption/CREATE
    StandardOpenOption/TRUNCATE_EXISTING
    StandardOpenOption/WRITE]))

(defn write-phase!
  "Writes a test file of `file-size` bytes sequentially to `file` in 1 MiB
  chunks, finishing with an fsync.

  Returns a map of :bytes-per-second or an anomaly."
  [file file-size progress! cancelled?]
  (ba/try-anomaly
   (with-open [ch (FileChannel/open ^Path file write-open-options)]
     (let [buf (write-buffer write-chunk-size)
           file-size (long file-size)
           start (System/nanoTime)]
       (progress! :seq-write 0.0)
       (loop [written 0]
         (if-let [anomaly (cancelled?)]
           anomaly
           (if (< written file-size)
             (let [n (min write-chunk-size (- file-size written))]
               (doto buf (.rewind) (.limit (int n)))
               (write-fully! ch buf)
               (let [written (+ written n)]
                 (when (zero? (rem written progress-interval))
                   (progress! :seq-write (/ (double written) file-size)))
                 (recur written)))
             (do
               (.force ch true)
               {:bytes-per-second (/ (double file-size) (u/duration-s start))}))))))))

(defn- open-read-channel ^FileChannel [^Path file direct?]
  (FileChannel/open
   file
   (into-array
    OpenOption
    (cond-> [StandardOpenOption/READ] direct? (conj ExtendedOpenOption/DIRECT)))))

(defn probe-direct-io
  "Returns true if `file` can be opened for reading with direct I/O."
  [file]
  (try
    (with-open [_ (open-read-channel file true)]
      true)
    (catch UnsupportedOperationException _ false)
    (catch IOException _ false)))

(defn concurrency-levels
  "Returns the reader concurrencies of the random read sweep up to
  `max-concurrency`: all powers of two up to `max-concurrency`, followed by
  `max-concurrency` itself if it isn't a power of two."
  [max-concurrency]
  (let [max-concurrency (long max-concurrency)
        powers (into [] (take-while #(<= (long %) max-concurrency)) (iterate #(* 2 (long %)) 1))]
    (cond-> powers (< (long (peek powers)) max-concurrency) (conj max-concurrency))))

(defn- read-buffer
  "Returns a direct byte buffer of `block-size` bytes aligned to the 4096 byte
  boundary required for direct I/O."
  ^ByteBuffer [block-size]
  (doto (.alignedSlice (ByteBuffer/allocateDirect (+ (int block-size) (* 2 alignment)))
                       alignment)
    (.limit (int block-size))))

(defn- reader-fn
  "Returns the run function of one reader thread.

  Reads random blocks from `file` until `deadline` or cancellation, recording
  latencies into `hist` and the number of reads into slot `i` of `counts`. Any
  exception is recorded as anomaly in the `error` atom."
  [^Path file {:keys [block-size direct?]} deadline cancelled? hist counts i error]
  (fn []
    (try
      (with-open [ch (open-read-channel file direct?)]
        (let [buf (read-buffer block-size)
              rnd (SplittableRandom.)
              deadline (long deadline)
              block-size (long block-size)
              num-blocks (quot (.size ch) block-size)]
          (loop [n 0]
            (if (and (< (System/nanoTime) deadline) (nil? (cancelled?)))
              (let [offset (* block-size (.nextLong rnd num-blocks))
                    start (System/nanoTime)]
                (.rewind buf)
                (.read ch buf offset)
                (histogram/record! hist (- (System/nanoTime) start))
                (recur (inc n)))
              (aset ^longs counts (int i) n)))))
      (catch Throwable e
        (reset! error (ba/anomaly e))))))

(defn- start-thread ^Thread [^CountDownLatch latch name f]
  (doto (Thread. ^Runnable #(try (f) (finally (.countDown latch))) ^String name)
    (.start)))

(defn read-phase!
  "Reads random blocks of :block-size bytes from `file` with :concurrency
  reader threads for :duration-millis, with direct I/O if :direct? is true.

  Returns a map of :iops, :bytes-per-second and :latency-nanos with quantiles
  :p50, :p95, :p99 and :max, or an anomaly."
  [file {:keys [concurrency duration-millis] :as params} progress! cancelled?]
  (ba/try-anomaly
   (let [hists (vec (repeatedly concurrency histogram/create))
         counts (long-array concurrency)
         error (atom nil)
         duration-seconds (/ (long duration-millis) 1000.0)
         start (System/nanoTime)
         deadline (+ start (* (long duration-millis) 1000000))
         latch (CountDownLatch. (int concurrency))
         threads (mapv
                  (fn [i]
                    (start-thread latch (str "disk-perf-reader-" i)
                                  (reader-fn file params deadline cancelled?
                                             (hists i) counts i error)))
                  (range concurrency))]
     (progress! :rand-read 0.0)
     (while (not (.await latch 250 TimeUnit/MILLISECONDS))
       (progress! :rand-read (min 0.99 (/ (u/duration-s start) duration-seconds))))
     (doseq [^Thread thread threads]
       (.join thread))
     (or @error
         (cancelled?)
         (let [seconds (u/duration-s start)
               total (areduce ^longs counts i sum 0 (+ sum (aget ^longs counts i)))
               hist (reduce #(doto ^longs %1 (histogram/merge-into! %2))
                            (histogram/create) hists)
               iops (/ (double total) seconds)]
           {:iops iops
            :bytes-per-second (* iops (double (:block-size params)))
            :latency-nanos
            {:p50 (histogram/quantile hist 0.5)
             :p95 (histogram/quantile hist 0.95)
             :p99 (histogram/quantile hist 0.99)
             :max (histogram/maximum hist)}})))))

(defn- level-progress
  "Wraps `progress!` so that the fraction of the sweep level with index `i` of
  `n` levels is mapped into the corresponding part of the whole sweep."
  [progress! i n]
  (fn [phase fraction]
    (progress! phase (/ (+ (long i) (double fraction)) (long n)))))

(defn read-sweep!
  "Runs one random read phase per concurrency in :concurrencies against
  `file`, each for :duration-millis, with direct I/O if :direct? is true.

  Returns a map of :direct? and :runs with one entry per concurrency in
  order, each a map of :concurrency and the read-phase! results, or an
  anomaly."
  [file {:keys [concurrencies direct?] :as params} progress! cancelled?]
  (let [n (count concurrencies)
        runs (reduce
              (fn [runs [i concurrency]]
                (let [params (-> (dissoc params :concurrencies)
                                 (assoc :concurrency concurrency))
                      run (read-phase! file params (level-progress progress! i n)
                                       cancelled?)]
                  (if (ba/anomaly? run)
                    (reduced run)
                    (conj runs (assoc run :concurrency concurrency)))))
              []
              (map-indexed vector concurrencies))]
    (if (ba/anomaly? runs)
      runs
      {:direct? direct? :runs runs})))

(defn fsync-phase!
  "Writes 8 KiB chunks sequentially to `file` for :duration-millis, forcing
  each chunk to disk with an fsync like a write-ahead log does, wrapping
  around after 128 MiB.

  Returns a map of :rate and :latency-nanos with quantiles :p50, :p95 and
  :p99, or an anomaly."
  [file {:keys [duration-millis]} progress! cancelled?]
  (ba/try-anomaly
   (with-open [ch (FileChannel/open ^Path file write-open-options)]
     (let [buf (write-buffer fsync-chunk-size)
           hist (histogram/create)
           duration-nanos (* (long duration-millis) 1000000)
           start (System/nanoTime)
           deadline (+ start duration-nanos)]
       (progress! :fsync 0.0)
       (loop [n 0 pos 0]
         (if-let [anomaly (cancelled?)]
           anomaly
           (if (< (System/nanoTime) deadline)
             (let [op-start (System/nanoTime)]
               (.rewind buf)
               (write-fully-at! ch buf pos)
               (.force ch false)
               (histogram/record! hist (- (System/nanoTime) op-start))
               (progress! :fsync (min 0.99 (/ (double (- (System/nanoTime) start))
                                              duration-nanos)))
               (recur (inc n) (rem (+ pos fsync-chunk-size) max-wal-size)))
             {:rate (/ (double n) (u/duration-s start))
              :latency-nanos
              {:p50 (histogram/quantile hist 0.5)
               :p95 (histogram/quantile hist 0.95)
               :p99 (histogram/quantile hist 0.99)}})))))))

(defn- dedup-progress
  "Wraps `progress!` so that it is only called when the phase changes or the
  fraction advances by at least 10 %.

  All phases call the wrapped function from the single benchmark thread, some
  of them once per I/O operation, so deduplication has to happen here."
  [progress!]
  (let [last-reported (volatile! nil)]
    (fn [phase fraction]
      (let [step [phase (long (* 10 (double fraction)))]]
        (when (not= @last-reported step)
          (vreset! last-reported step)
          (progress! phase fraction))))))

(defn- delete-quietly! [^Path file]
  (try
    (Files/deleteIfExists file)
    (catch IOException e
      (log/warn (format "Can't delete benchmark test file `%s`." file) e))))

(defn run!
  "Runs the complete disk performance benchmark in the directory `dir`.

  `params` is a map of :file-size, :block-size, :concurrencies and
  :phase-duration-millis, where each concurrency level of the random read
  sweep runs for the phase duration. `progress!` is called with the current
  phase and a fraction between 0 and 1. `cancelled?` is called regularly and
  stops the benchmark by returning an anomaly.

  Returns a map of :seq-write, :rand-read and :fsync phase results or an
  anomaly. The test files are always deleted, also on errors and
  cancellation."
  [dir {:keys [file-size block-size concurrencies phase-duration-millis]} progress! cancelled?]
  (let [progress! (dedup-progress progress!)
        data-file (path/resolve (path/path dir) data-file-name)
        wal-file (path/resolve (path/path dir) wal-file-name)]
    (try
      (when-ok [_ (check-free-space dir file-size)
                seq-write (write-phase! data-file file-size progress! cancelled?)
                rand-read (let [direct? (probe-direct-io data-file)]
                            (when-not direct?
                              (log/warn (format "Direct I/O is not supported on the volume of `%s`. The random read results will include page cache effects." dir)))
                            (read-sweep! data-file
                                         {:block-size block-size
                                          :concurrencies concurrencies
                                          :duration-millis phase-duration-millis
                                          :direct? direct?}
                                         progress! cancelled?))
                fsync (fsync-phase! wal-file {:duration-millis phase-duration-millis}
                                    progress! cancelled?)]
        {:seq-write seq-write
         :rand-read rand-read
         :fsync fsync})
      (finally
        (delete-quietly! data-file)
        (delete-quietly! wal-file)))))
