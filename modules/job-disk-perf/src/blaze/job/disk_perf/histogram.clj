(ns blaze.job.disk-perf.histogram
  "Latency histogram with geometric buckets.

  Records latencies in nanoseconds into 512 buckets growing geometrically by a
  factor of 2^(1/8), covering the full long range with a relative error of at
  most ~4.5 %. Uses constant memory (4 KiB) independent of the number of
  recorded values.")

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:private ^:const ^long num-buckets 512)

(def ^:private ^:const ^double buckets-per-octave 8.0)

(def ^:private ^:const ^double log-2 0.6931471805599453)

(defn- bucket-index ^long [^long nanos]
  (let [nanos (max 1 nanos)]
    (min (dec num-buckets)
         (Math/round (* buckets-per-octave (/ (Math/log (double nanos)) log-2))))))

(defn- bucket-value ^long [^long index]
  (Math/round (Math/pow 2.0 (/ (double index) buckets-per-octave))))

(defn create
  "Creates an empty histogram."
  ^longs []
  (long-array num-buckets))

(defn record!
  "Records a latency of `nanos` nanoseconds into `histogram`."
  [^longs histogram nanos]
  (let [i (bucket-index nanos)]
    (aset histogram i (inc (aget histogram i)))))

(defn merge-into!
  "Merges all values of `source` into `target`, leaving `source` unchanged."
  [^longs target ^longs source]
  (dotimes [i num-buckets]
    (aset target i (+ (aget target i) (aget source i)))))

(defn total
  "Returns the number of values recorded into `histogram`."
  [^longs histogram]
  (areduce histogram i sum 0 (+ sum (aget histogram i))))

(defn quantile
  "Returns the latency in nanoseconds at quantile `q` or 0 if `histogram` is
  empty."
  [^longs histogram q]
  (let [total (long (total histogram))]
    (if (zero? total)
      0
      (let [rank (max 1.0 (Math/ceil (* (double q) total)))]
        (loop [i 0 seen 0]
          (let [seen (+ seen (aget histogram i))]
            (if (<= rank seen)
              (bucket-value i)
              (recur (inc i) seen))))))))

(defn maximum
  "Returns the largest recorded latency in nanoseconds or 0 if `histogram` is
  empty."
  [^longs histogram]
  (loop [i (dec num-buckets)]
    (cond
      (pos? (aget histogram i)) (bucket-value i)
      (zero? i) 0
      :else (recur (dec i)))))
