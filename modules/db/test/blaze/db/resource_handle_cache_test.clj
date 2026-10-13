(ns blaze.db.resource-handle-cache-test
  (:require
   [blaze.cache-collector.protocols :as ccp]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle-test-util :as rh-tu]
   [blaze.db.impl.index.rts-as-of :as rts]
   [blaze.db.impl.index.rts-as-of-spec]
   [blaze.db.resource-handle-cache :as rhc]
   [blaze.db.resource-handle-cache-spec]
   [blaze.fhir.hash :as hash]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [blaze.fhir.spec.type Base]
   [com.github.benmanes.caffeine.cache.stats CacheStats]
   [java.util.concurrent CountDownLatch Executors TimeUnit]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private tid (codec/tid "Patient"))

(def ^:private config
  {:blaze.db/resource-handle-cache {:max-size-ratio 0.01}})

(defn- hash-of [id t]
  ;; only has to differ per id and t
  (hash/generate {:fhir/type :fhir/Patient :id (str id "-" t)}))

(defn- resource-handle
  "Returns a resource handle of the Patient with `id` at `t`."
  [id t]
  (rh-tu/resource-handle "Patient" id t (hash-of id t)))

(defn- entry
  "Returns the update of the ResourceAsOf index entry of the Patient with `id`
  at `t`, optionally purged."
  ([id t]
   (entry id t false))
  ([id t purged?]
   [(rts/resource-handle tid id t (hash-of id t) t :put) purged?]))

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.db/resource-handle-cache nil}
      :key := :blaze.db/resource-handle-cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing max-size-ratio"
    (given-failed-system {:blaze.db/resource-handle-cache {}}
      :key := :blaze.db/resource-handle-cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :max-size-ratio))))

  (testing "invalid max-size-ratio"
    (given-failed-system (assoc-in config [:blaze.db/resource-handle-cache :max-size-ratio] ::invalid)
      :key := :blaze.db/resource-handle-cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db.resource-handle-cache/max-size-ratio]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "non-positive max-size-ratio"
    (given-failed-system (assoc-in config [:blaze.db/resource-handle-cache :max-size-ratio] 0)
      :key := :blaze.db/resource-handle-cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db.resource-handle-cache/max-size-ratio]
      [:cause-data ::s/problems 0 :val] := 0))

  (testing "is a cache"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (is (rhc/resource-handle-cache? cache)))))

(deftest get-test
  (with-system [{cache :blaze.db/resource-handle-cache} config]
    (rhc/set-update-t! cache 1)
    (rhc/put! cache 1 (resource-handle "0" 1))

    (testing "finds the handle at its own t"
      (given (rhc/get cache tid "0" 1)
        :id := "0"
        :t := 1))

    (testing "finds the handle at a newer t"
      (given (rhc/get cache tid "0" 2)
        :id := "0"
        :t := 1))

    (testing "doesn't find the handle at an older t"
      (is (nil? (rhc/get cache tid "0" 0))))

    (testing "doesn't find another id"
      (is (nil? (rhc/get cache tid "1" 1))))

    (testing "doesn't find another type"
      (is (nil? (rhc/get cache (codec/tid "Observation") "0" 1))))))

(deftest put-test
  (testing "doesn't insert before the update t was set"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/put! cache 1 (resource-handle "0" 1))

      (is (nil? (rhc/get cache tid "0" 1)))))

  (testing "inserts at the update t"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/set-update-t! cache 1)
      (rhc/put! cache 1 (resource-handle "0" 1))

      (given (rhc/get cache tid "0" 1)
        :t := 1)))

  (testing "inserts after the update t"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/set-update-t! cache 1)
      (rhc/put! cache 2 (resource-handle "0" 1))

      (given (rhc/get cache tid "0" 2)
        :t := 1)))

  (testing "doesn't insert before the update t"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/set-update-t! cache 2)
      (rhc/put! cache 1 (resource-handle "0" 1))

      (is (nil? (rhc/get cache tid "0" 2)))))

  (testing "doesn't replace an existing handle"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/set-update-t! cache 2)
      (rhc/put! cache 2 (resource-handle "0" 2))
      (rhc/put! cache 2 (resource-handle "0" 1))

      (given (rhc/get cache tid "0" 2)
        :t := 2)))

  (testing "a read-only view doesn't insert"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/set-update-t! cache 1)
      (rhc/put! (rhc/read-only cache) 1 (resource-handle "0" 1))

      (is (nil? (rhc/get cache tid "0" 1))))))

(deftest read-only-test
  (with-system [{cache :blaze.db/resource-handle-cache} config]
    (rhc/set-update-t! cache 1)
    (rhc/put! cache 1 (resource-handle "0" 1))

    (testing "is a cache"
      (is (rhc/resource-handle-cache? (rhc/read-only cache))))

    (testing "reads the entries of the cache"
      (given (rhc/get (rhc/read-only cache) tid "0" 1)
        :t := 1))))

(deftest update-test
  (testing "doesn't insert absent entries"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/update! cache 1 [(entry "0" 1)])

      (is (nil? (rhc/get cache tid "0" 1)))))

  (testing "replaces present entries"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/set-update-t! cache 1)
      (rhc/put! cache 1 (resource-handle "0" 1))
      (rhc/update! cache 2 [(entry "0" 2)])

      (given (rhc/get cache tid "0" 2)
        :fhir/type := :fhir/Patient
        :tid := tid
        :id := "0"
        :t := 2
        :hash := (hash-of "0" 2)
        :num-changes := 2
        :op := :put)

      (testing "readers at the older t don't use the new entry"
        (is (nil? (rhc/get cache tid "0" 1))))))

  (testing "keeps the id instance of the inserted handle, which is shared with
            the key"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (let [handle (resource-handle "0" 1)]
        (rhc/set-update-t! cache 1)
        (rhc/put! cache 1 handle)
        ;; the update carries an equal id that is another instance
        (rhc/update! cache 2 [(entry (String. "0") 2)])

        (is (identical? (:id handle) (:id (rhc/get cache tid "0" 2)))))))

  (testing "sets the update t"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/update! cache 2 [])
      (rhc/put! cache 1 (resource-handle "0" 1))

      (is (nil? (rhc/get cache tid "0" 2)))

      (rhc/put! cache 2 (resource-handle "0" 1))

      (given (rhc/get cache tid "0" 2)
        :t := 1)))

  (testing "removes the entry of a purged resource"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/set-update-t! cache 2)
      (rhc/put! cache 2 (resource-handle "0" 2))
      (rhc/update! cache 3 [(entry "0" 2 true) (entry "0" 1 true)])

      (is (nil? (rhc/get cache tid "0" 3)))))

  (testing "keeps the entry if only older versions are purged"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/set-update-t! cache 2)
      (rhc/put! cache 2 (resource-handle "0" 2))
      (rhc/update! cache 3 [(entry "0" 1 true)])

      (given (rhc/get cache tid "0" 3)
        :t := 2)))

  (testing "only touches the entries of the resources updated"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/set-update-t! cache 1)
      (rhc/put! cache 1 (resource-handle "0" 1))
      (rhc/put! cache 1 (resource-handle "1" 1))
      (rhc/update! cache 2 [(entry "1" 2)])

      (given (rhc/get cache tid "0" 2)
        :t := 1)
      (given (rhc/get cache tid "1" 2)
        :t := 2))))

(deftest interleaving-test
  (testing "a reader inserting before the indexer updates the same key"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/set-update-t! cache 1)

      ;; the reader at t = 1 loaded the handle from its snapshot
      (rhc/put! cache 1 (resource-handle "0" 1))
      ;; the indexer updates the entry of the transaction with t = 2
      (rhc/update! cache 2 [(entry "0" 2)])

      (testing "the indexer replaced the entry of the reader"
        (given (rhc/get cache tid "0" 2)
          :t := 2))))

  (testing "a reader inserting after the indexer skipped the same key"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/set-update-t! cache 1)

      ;; the indexer skips the entry of the transaction with t = 2, because it
      ;; isn't present
      (rhc/update! cache 2 [(entry "0" 2)])
      ;; the reader at t = 1 loaded the handle from its snapshot before
      (rhc/put! cache 1 (resource-handle "0" 1))

      (testing "the reader didn't insert its outdated handle"
        (is (nil? (rhc/get cache tid "0" 2))))))

  (testing "concurrent readers and indexer"
    ;; the indexer creates a new version of one resource in every transaction,
    ;; while readers at the current or an older t load the version at their t
    ;; and insert it. At the end, every entry present has to be the newest
    ;; version of its resource. Because a resource changes only every
    ;; `num-ids` transactions, an outdated insert would likely survive.
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (let [num-ids 10
            num-txs 5000
            ;; the transaction with t changes the resource with id (mod t n)
            version-at (fn [i t]
                         (let [v (- t (mod (- t i) num-ids))]
                           (when (pos? v) v)))
            ;; the t visible to readers, advanced after the update like the
            ;; node does
            visible-t (atom 0)
            executor (Executors/newFixedThreadPool 4)
            start (CountDownLatch. 1)
            reader (fn []
                     (.await start)
                     (loop []
                       (let [current-t @visible-t
                             t (max 0 (- current-t (rand-int 3)))
                             i (rand-int num-ids)]
                         (when-let [v (version-at i t)]
                           (when-not (rhc/get cache tid (str i) t)
                             (rhc/put! cache t (resource-handle (str i) v))))
                         (when (< current-t num-txs)
                           (recur)))))]
        (rhc/set-update-t! cache 0)
        (let [readers (mapv (fn [_] (.submit executor ^Callable reader))
                            (range 3))]
          (.countDown start)
          (doseq [t (range 1 (inc num-txs))]
            (rhc/update! cache t [(entry (str (mod t num-ids)) t)])
            (reset! visible-t t))
          (run! deref readers)
          (.shutdown executor)
          (.awaitTermination executor 10 TimeUnit/SECONDS))

        (doseq [i (range num-ids)]
          (when-let [handle (rhc/get cache tid (str i) num-txs)]
            (is (= (version-at i num-txs) (:t handle)))))))))

(defn- stats [cache]
  (let [^CacheStats stats (ccp/-stats cache)]
    {:hits (.hitCount stats)
     :misses (.missCount stats)
     :loads (.loadCount stats)
     :evictions (.evictionCount stats)}))

(defn- await-eviction
  "Returns the stats of `cache` after the first eviction, which Caffeine records
  asynchronously, or after 10 seconds."
  [cache]
  (loop [n 1000]
    (let [stats (stats cache)]
      (if (or (pos? (:evictions stats)) (zero? n))
        stats
        (do (Thread/sleep 10)
            (recur (dec n)))))))

(deftest stats-test
  (testing "inserts and updates are neither recorded as misses nor as loads"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/set-update-t! cache 1)
      (rhc/put! cache 1 (resource-handle "0" 1))
      (rhc/update! cache 2 [(entry "0" 2)])
      (rhc/get cache tid "0" 2)
      (rhc/get cache tid "1" 2)

      (given (stats cache)
        :hits := 1
        :misses := 1
        :loads := 0)

      (is (= 1 (ccp/-estimated-size cache)))))

  (testing "a handle not newer than t is a hit"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/set-update-t! cache 1)
      (rhc/put! cache 1 (resource-handle "0" 1))
      (rhc/get cache tid "0" 1)
      (rhc/get cache tid "0" 2)

      (given (stats cache)
        :hits := 2
        :misses := 0)))

  (testing "a handle newer than t is a miss"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/set-update-t! cache 1)
      (rhc/put! cache 1 (resource-handle "0" 1))
      (rhc/update! cache 2 [(entry "0" 2)])
      (rhc/get cache tid "0" 1)

      (given (stats cache)
        :hits := 0
        :misses := 1)))

  (testing "an absent handle is a miss"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/get cache tid "0" 1)

      (given (stats cache)
        :hits := 0
        :misses := 1)))

  (testing "lookups through a read-only view aren't recorded"
    (with-system [{cache :blaze.db/resource-handle-cache} config]
      (rhc/set-update-t! cache 1)
      (rhc/put! cache 1 (resource-handle "0" 1))
      (rhc/get (rhc/read-only cache) tid "0" 1)
      (rhc/get (rhc/read-only cache) tid "1" 1)

      (given (stats cache)
        :hits := 0
        :misses := 0)))

  (testing "evictions are recorded"
    (with-system [{cache :blaze.db/resource-handle-cache}
                  ;; a maximum weight of zero evicts every entry
                  {:blaze.db/resource-handle-cache {:max-size-ratio 1e-15}}]
      (rhc/set-update-t! cache 1)
      (rhc/put! cache 1 (resource-handle "0" 1))

      (given (await-eviction cache)
        :evictions := 1))))

(deftest weight-test
  (testing "the weight depends on the reference size"
    (testing "id with one char"
      (is (= (if (= 4 Base/MEM_SIZE_REFERENCE) 204 260)
             (rhc/weight (resource-handle "0" 1)))))

    (testing "id with 16 chars"
      (is (= (if (= 4 Base/MEM_SIZE_REFERENCE) 220 276)
             (rhc/weight (resource-handle "0123456789abcdef" 1)))))))
