(ns blaze.job.disk-perf.engine-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.job.disk-perf.engine :as engine]
   [blaze.job.disk-perf.engine-spec]
   [blaze.path :as path]
   [blaze.test-util :as tu]
   [blaze.util :as u]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]])
  (:import
   [java.nio.file Files OpenOption Path]
   [java.nio.file.attribute FileAttribute]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private params
  {:file-size (* 4 u/mib)
   :block-size 16384
   :concurrencies [1 2]
   :phase-duration-millis 200})

(defn- tmp-dir []
  (str (Files/createTempDirectory "blaze-disk-perf-test" (make-array FileAttribute 0))))

(defn- dir-entries [dir]
  (with-open [stream (Files/list (path/path dir))]
    (vec (iterator-seq (.iterator stream)))))

(def ^:private not-cancelled (constantly nil))

(defn- noop-progress! [_ _])

(deftest check-free-space-test
  (testing "enough space"
    (is (nil? (engine/check-free-space (tmp-dir) (* 4 u/mib)))))

  (testing "not enough space"
    (given (engine/check-free-space (tmp-dir) (bit-shift-left 1 60))
      ::anom/category := ::anom/conflict
      ::anom/message :# "Not enough space on the volume of `.*`\\..*"))

  (testing "missing directory"
    (given (engine/check-free-space "/blaze-disk-perf-missing-dir" u/mib)
      ::anom/category := ::anom/fault)))

(deftest write-phase-test
  (testing "success"
    (let [file (path/resolve (path/path (tmp-dir)) "test.bin")]
      (given (engine/write-phase! file (* 4 u/mib) noop-progress! not-cancelled)
        [:bytes-per-second] :? pos?)

      (testing "the file has exactly the requested size"
        (is (= (* 4 u/mib) (Files/size file))))))

  (testing "progress is reported every 64 u/mib"
    (let [file (path/resolve (path/path (tmp-dir)) "test.bin")
          calls (atom [])]
      (given (engine/write-phase! file (* 64 u/mib)
                                  (fn [phase fraction] (swap! calls conj [phase fraction]))
                                  not-cancelled)
        [:bytes-per-second] :? pos?)
      (is (= [[:seq-write 0.0] [:seq-write 1.0]] @calls))))

  (testing "writing to a directory fails"
    (given (engine/write-phase! (path/path (tmp-dir)) u/mib noop-progress! not-cancelled)
      ::anom/category := ::anom/fault))

  (testing "cancellation"
    (let [file (path/resolve (path/path (tmp-dir)) "test.bin")]
      (given (engine/write-phase! file (* 4 u/mib) noop-progress!
                                  (constantly (ba/interrupted)))
        ::anom/category := ::anom/interrupted))))

(deftest concurrency-levels-test
  (testing "powers of two up to the maximum"
    (is (= [1] (engine/concurrency-levels 1)))
    (is (= [1 2] (engine/concurrency-levels 2)))
    (is (= [1 2 4 8] (engine/concurrency-levels 8)))
    (is (= [1 2 4 8 16 32] (engine/concurrency-levels 32)))
    (is (= [1 2 4 8 16 32 64 128] (engine/concurrency-levels 128))))

  (testing "a maximum that is no power of two becomes the last level"
    (is (= [1 2 4 8 12] (engine/concurrency-levels 12)))
    (is (= [1 2 3] (engine/concurrency-levels 3)))))

(deftest probe-direct-io-test
  (testing "on an existing file"
    (let [file (path/resolve (path/path (tmp-dir)) "test.bin")]
      (engine/write-phase! file u/mib noop-progress! not-cancelled)
      (is (boolean? (engine/probe-direct-io file)))))

  (testing "on a missing file"
    (is (false? (engine/probe-direct-io (path/resolve (path/path (tmp-dir)) "missing.bin"))))))

(deftest read-phase-test
  (let [file (path/resolve (path/path (tmp-dir)) "test.bin")]
    (engine/write-phase! file (* 4 u/mib) noop-progress! not-cancelled)

    (testing "buffered reads"
      (let [calls (atom [])
            result (engine/read-phase! file {:block-size 16384 :concurrency 2
                                             :duration-millis 600 :direct? false}
                                       (fn [phase fraction] (swap! calls conj [phase fraction]))
                                       not-cancelled)]
        (given result
          [:iops] :? pos?
          [:bytes-per-second] :? pos?)

        (testing "latency percentiles are monotone"
          (let [{:keys [p50 p95 p99 max]} (:latency-nanos result)]
            (is (pos? p50))
            (is (<= p50 p95 p99 max))))

        (testing "progress is reported while reading"
          (is (some #(and (= :rand-read (first %)) (pos? (second %))) @calls)))))

    (testing "a missing file fails"
      (given (engine/read-phase! (path/resolve (path/path (tmp-dir)) "missing.bin")
                                 {:block-size 16384 :concurrency 2
                                  :duration-millis 200 :direct? false}
                                 noop-progress! not-cancelled)
        ::anom/category := ::anom/fault))))

(deftest read-sweep-test
  (let [file (path/resolve (path/path (tmp-dir)) "test.bin")]
    (engine/write-phase! file (* 4 u/mib) noop-progress! not-cancelled)

    (testing "one run per concurrency level"
      (let [calls (atom [])
            result (engine/read-sweep! file {:block-size 16384 :concurrencies [1 2]
                                             :duration-millis 200 :direct? false}
                                       (fn [phase fraction] (swap! calls conj [phase fraction]))
                                       not-cancelled)]
        (given result
          [:direct?] := false
          [:runs count] := 2
          [:runs 0 :concurrency] := 1
          [:runs 0 :iops] :? pos?
          [:runs 0 :bytes-per-second] :? pos?
          [:runs 1 :concurrency] := 2
          [:runs 1 :iops] :? pos?)

        (testing "latency percentiles are monotone in every run"
          (doseq [{{:keys [p50 p95 p99 max]} :latency-nanos} (:runs result)]
            (is (pos? p50))
            (is (<= p50 p95 p99 max))))

        (testing "progress spans the whole sweep"
          (let [fractions (map second (filter #(= :rand-read (first %)) @calls))]
            (is (apply <= fractions))
            (is (some #(<= 0.5 %) fractions))))))

    (testing "cancellation"
      (given (engine/read-sweep! file {:block-size 16384 :concurrencies [1 2]
                                       :duration-millis 200 :direct? false}
                                 noop-progress! (constantly (ba/interrupted)))
        ::anom/category := ::anom/interrupted))

    (testing "a missing file fails"
      (given (engine/read-sweep! (path/resolve (path/path (tmp-dir)) "missing.bin")
                                 {:block-size 16384 :concurrencies [1]
                                  :duration-millis 200 :direct? false}
                                 noop-progress! not-cancelled)
        ::anom/category := ::anom/fault))))

(deftest fsync-phase-test
  (let [file (path/resolve (path/path (tmp-dir)) "wal.bin")
        result (engine/fsync-phase! file {:duration-millis 200}
                                    noop-progress! not-cancelled)]
    (given result
      [:rate] :? pos?)

    (testing "latency percentiles are monotone"
      (let [{:keys [p50 p95 p99]} (:latency-nanos result)]
        (is (pos? p50))
        (is (<= p50 p95 p99))))

    (testing "writing to a directory fails"
      (given (engine/fsync-phase! (path/path (tmp-dir)) {:duration-millis 200}
                                  noop-progress! not-cancelled)
        ::anom/category := ::anom/fault))))

(deftest run-test
  (testing "success"
    (let [dir (tmp-dir)
          calls (atom [])
          progress! (fn [phase fraction] (swap! calls conj [phase fraction]))
          result (engine/run! dir params progress! not-cancelled)]
      (given result
        [:seq-write :bytes-per-second] :? pos?
        [:rand-read :direct?] :? boolean?
        [:rand-read :runs count] := 2
        [:rand-read :runs 0 :concurrency] := 1
        [:rand-read :runs 0 :iops] :? pos?
        [:rand-read :runs 0 :bytes-per-second] :? pos?
        [:rand-read :runs 0 :latency-nanos :p50] :? pos?
        [:rand-read :runs 1 :concurrency] := 2
        [:rand-read :runs 1 :iops] :? pos?
        [:fsync :rate] :? pos?
        [:fsync :latency-nanos :p50] :? pos?)

      (testing "the phases run in order"
        (is (= [:seq-write :rand-read :fsync] (distinct (map first @calls)))))

      (testing "all test files are deleted"
        (is (empty? (dir-entries dir))))))

  (testing "leftover test files from a previous run are replaced and deleted"
    (let [dir (tmp-dir)]
      (Files/write ^Path (path/resolve (path/path dir) "blaze-disk-perf-data.tmp")
                   (byte-array 10)
                   ^"[Ljava.nio.file.OpenOption;" (make-array OpenOption 0))
      (given (engine/run! dir params noop-progress! not-cancelled)
        [:seq-write :bytes-per-second] :? pos?)
      (is (empty? (dir-entries dir)))))

  (testing "not enough space"
    (let [dir (tmp-dir)]
      (given (engine/run! dir (assoc params :file-size (bit-shift-left 1 60))
                          noop-progress! not-cancelled)
        ::anom/category := ::anom/conflict)
      (is (empty? (dir-entries dir)))))

  (testing "without direct I/O support the random reads still succeed"
    (with-redefs [engine/probe-direct-io (constantly false)]
      (let [dir (tmp-dir)
            result (engine/run! dir params noop-progress! not-cancelled)]
        (given result
          [:rand-read :direct?] := false
          [:rand-read :runs 0 :iops] :? pos?)
        (is (empty? (dir-entries dir))))))

  (testing "a directory in place of the test file fails the benchmark"
    (let [dir (tmp-dir)
          data-dir ^Path (path/resolve (path/path dir) "blaze-disk-perf-data.tmp")]
      (Files/createDirectories data-dir (make-array FileAttribute 0))
      (Files/write ^Path (path/resolve data-dir "blocker") (byte-array 1)
                   ^"[Ljava.nio.file.OpenOption;" (make-array OpenOption 0))
      (given (engine/run! dir params noop-progress! not-cancelled)
        ::anom/category := ::anom/fault)))

  (testing "cancellation during the write phase"
    (let [dir (tmp-dir)]
      (given (engine/run! dir params noop-progress! (constantly (ba/interrupted)))
        ::anom/category := ::anom/interrupted)

      (testing "all test files are deleted"
        (is (empty? (dir-entries dir))))))

  (testing "cancellation during the random read phase"
    (let [dir (tmp-dir)
          cancelled (atom false)
          progress! (fn [phase _] (when (= :rand-read phase) (reset! cancelled true)))]
      (given (engine/run! dir params progress! #(when @cancelled (ba/interrupted)))
        ::anom/category := ::anom/interrupted)

      (testing "all test files are deleted"
        (is (empty? (dir-entries dir))))))

  (testing "cancellation during the fsync phase"
    (let [dir (tmp-dir)
          cancelled (atom false)
          progress! (fn [phase _] (when (= :fsync phase) (reset! cancelled true)))]
      (given (engine/run! dir params progress! #(when @cancelled (ba/interrupted)))
        ::anom/category := ::anom/interrupted)

      (testing "all test files are deleted"
        (is (empty? (dir-entries dir)))))))
