(ns blaze.job.disk-perf.score-test
  (:require
   [blaze.job.disk-perf.score :as score]
   [blaze.job.disk-perf.score-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- reference-read-run
  "The read run of `concurrency` of a disk exactly matching the reference
  curve."
  [concurrency]
  {:concurrency concurrency
   :iops (* score/reference-iops-per-reader
            (double (min concurrency score/reference-max-concurrency)))})

(defn- scale-iops [read-runs factor]
  (mapv #(update % :iops * factor) read-runs))

(def ^:private reference-measurement
  {:read-runs (mapv reference-read-run [1 2 4 8 16 32])
   :seq-write-bytes-per-second score/reference-seq-write-bytes-per-second
   :fsync-rate score/reference-fsync-rate})

(deftest score-test
  (testing "reference hardware scores 100"
    (is (= 100.0 (score/score reference-measurement))))

  (testing "sub-scores are capped at the reference values"
    (is (= 100.0 (score/score (update reference-measurement :read-runs scale-iops 10)))))

  (testing "the reference curve is capped at 32 readers"
    (is (= 100.0 (score/score (assoc reference-measurement :read-runs
                                     [{:concurrency 64
                                       :iops (* 32 score/reference-iops-per-reader)}])))))

  (testing "zero performance scores 0"
    (is (= 0.0 (score/score (update reference-measurement :read-runs scale-iops 0)))))

  (testing "random reads are weighted with one half"
    (is (< (abs (- 50.0 (score/score (update reference-measurement :read-runs scale-iops 0.25)))) 1e-9)))

  (testing "one collapsed level drags down the read score by the equal-weight geometric mean"
    (let [runs (into [(update (reference-read-run 1) :iops / 64)]
                     (map reference-read-run) [2 4 8 16 32])]
      (is (< (abs (- (* 100.0 (Math/pow 0.5 0.5))
                     (score/score (assoc reference-measurement :read-runs runs))))
             1e-9))))

  (testing "sequential writes are weighted with one quarter"
    (is (< (abs (- 50.0 (score/score (update reference-measurement :seq-write-bytes-per-second * (/ 1.0 16))))) 1e-9)))

  (testing "fsyncs are weighted with one quarter"
    (is (< (abs (- 50.0 (score/score (update reference-measurement :fsync-rate * (/ 1.0 16))))) 1e-9))))

(deftest rating-test
  (testing "excellent"
    (is (= "excellent" (score/rating 100.0)))
    (is (= "excellent" (score/rating 80.0))))

  (testing "good"
    (is (= "good" (score/rating 79.9)))
    (is (= "good" (score/rating 50.0))))

  (testing "acceptable"
    (is (= "acceptable" (score/rating 49.9)))
    (is (= "acceptable" (score/rating 25.0))))

  (testing "insufficient"
    (is (= "insufficient" (score/rating 24.9)))
    (is (= "insufficient" (score/rating 0.0)))))
