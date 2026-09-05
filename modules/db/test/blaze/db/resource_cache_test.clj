(ns blaze.db.resource-cache-test
  (:require
   [blaze.cache-collector.protocols :as ccp]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.resource-cache :as rc]
   [blaze.db.resource-cache-spec]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store-spec]
   [blaze.db.resource-store.kv :as rs-kv]
   [blaze.db.test-util :as dtu]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [blaze.db.resource_cache DefaultResourceCache]
   [com.github.benmanes.caffeine.cache AsyncCache]
   [com.github.benmanes.caffeine.cache.stats CacheStats]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def patient-0 {:fhir/type :fhir/Patient :id "0"})
(def patient-1 {:fhir/type :fhir/Patient :id "1"})
(def patient-2 {:fhir/type :fhir/Patient :id "2"})
(def code-system-0 {:fhir/type :fhir/CodeSystem :id "0"
                    :concept
                    [{:fhir/type :fhir.CodeSystem/concept
                      :code #fhir/code "foo"}]})

(def patient-0-hash (hash/generate patient-0))
(def patient-1-hash (hash/generate patient-1))
(def patient-2-hash (hash/generate patient-2))
(def code-system-0-hash (hash/generate code-system-0))

(def ^:private config
  {:blaze.db/resource-cache
   {:resource-store (ig/ref ::rs/kv)}
   ::rs/kv
   {:kv-store (ig/ref ::kv/mem)
    :parsing-context (ig/ref ::dtu/parsing-context)
    :writing-context (ig/ref ::dtu/writing-context)
    :executor (ig/ref ::rs-kv/executor)}
   ::rs-kv/executor {}
   ::kv/mem {:column-families {}}
   ::dtu/parsing-context {}
   ::dtu/writing-context {}})

(def ^:private zero-config
  "Creates a special version of a no-op cache."
  (assoc-in config [:blaze.db/resource-cache :max-size-ratio] 0))

(def ^:private one-config
  "Constraints the max-size-ratio to 0.8."
  (assoc-in config [:blaze.db/resource-cache :max-size-ratio] 1))

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.db/resource-cache nil}
      :key := :blaze.db/resource-cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing store"
    (given-failed-system {:blaze.db/resource-cache {}}
      :key := :blaze.db/resource-cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :resource-store))))

  (testing "invalid store"
    (given-failed-system (assoc-in config [:blaze.db/resource-cache :resource-store] ::invalid)
      :key := :blaze.db/resource-cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/resource-store]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid max-size-ratio"
    (given-failed-system (assoc-in config [:blaze.db/resource-cache :max-size-ratio] ::invalid)
      :key := :blaze.db/resource-cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::rc/max-size-ratio]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest get-test
  (testing "success"
    (doseq [config [config zero-config one-config]]
      (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
        @(rs/put! store {patient-0-hash patient-0
                         patient-1-hash patient-1
                         code-system-0-hash code-system-0})

        (are [key resource] (= resource @(rc/get cache key))
          [:fhir/Patient patient-0-hash :complete] patient-0
          [:fhir/Patient patient-1-hash :complete] patient-1
          [:fhir/CodeSystem code-system-0-hash :complete] code-system-0
          [:fhir/CodeSystem code-system-0-hash :summary] {:fhir/type :fhir/CodeSystem :id "0"
                                                          :meta (type/meta {:tag [fu/subsetted]})}
          [:fhir/CodeSystem code-system-0-hash :complete] code-system-0))))

  (testing "not-found"
    (doseq [config [config zero-config one-config]]
      (with-system [{cache :blaze.db/resource-cache} config]

        (is (nil? @(rc/get cache [:fhir/Patient patient-0-hash :complete])))))))

(deftest multi-get-test
  (testing "found both"
    (doseq [config [config zero-config one-config]]
      (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
        @(rs/put! store {patient-0-hash patient-0
                         patient-1-hash patient-1})

        (is (= {[:fhir/Patient patient-0-hash :complete] patient-0
                [:fhir/Patient patient-1-hash :complete] patient-1}
               @(st/with-instrument-disabled
                  (rc/multi-get cache [[:fhir/Patient patient-0-hash :complete]
                                       [:fhir/Patient patient-1-hash :complete]])))))))

  (testing "found one"
    (doseq [config [config zero-config one-config]]
      (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
        @(rs/put! store {patient-0-hash patient-0})

        (is (= {[:fhir/Patient patient-0-hash :complete] patient-0}
               @(st/with-instrument-disabled
                  (rc/multi-get cache [[:fhir/Patient patient-0-hash :complete]
                                       [:fhir/Patient patient-1-hash :complete]]))))))))

(defn- generate-patients [n]
  (into
   {}
   (map
    (fn [i]
      (let [patient {:fhir/type :fhir/Patient :id (str i)}]
        [(hash/generate patient) patient])))
   (range n)))

(defn- contains-key? [cache key]
  (when (instance? DefaultResourceCache cache)
    (some? (.getIfPresent ^AsyncCache (.cache ^DefaultResourceCache cache) key))))

(defn- cache-size [cache]
  (.size (.asMap ^AsyncCache (.cache ^DefaultResourceCache cache))))

(deftest get-skip-cache-insertion-test
  (testing "returns an existing patient from the store"
    (doseq [config [config zero-config one-config]]
      (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
        @(rs/put! store {patient-0-hash patient-0})

        (is (= patient-0
               @(st/with-instrument-disabled
                  (rc/get-skip-cache-insertion
                   cache [:fhir/Patient patient-0-hash :complete])))))))

  (testing "returns an already cached patient"
    (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
      @(rs/put! store {patient-0-hash patient-0})
      @(rc/get cache [:fhir/Patient patient-0-hash :complete])

      (is (contains-key? cache [:fhir/Patient patient-0-hash :complete]))

      (is (= patient-0
             @(st/with-instrument-disabled
                (rc/get-skip-cache-insertion
                 cache [:fhir/Patient patient-0-hash :complete]))))))

  (testing "doesn't insert the patient into the cache"
    (doseq [config [config one-config]]
      (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
        @(rs/put! store {patient-0-hash patient-0})

        (is (= patient-0
               @(st/with-instrument-disabled
                  (rc/get-skip-cache-insertion
                   cache [:fhir/Patient patient-0-hash :complete]))))

        (is (not (contains-key? cache [:fhir/Patient patient-0-hash :complete]))))))

  (testing "returns nil for a not-found patient"
    (doseq [config [config zero-config one-config]]
      (with-system [{cache :blaze.db/resource-cache} config]
        (is (nil? @(st/with-instrument-disabled
                     (rc/get-skip-cache-insertion
                      cache [:fhir/Patient patient-0-hash :complete]))))))))

(deftest multi-get-skip-cache-insertion-test
  (testing "just returns two existing patients"
    (doseq [config [config zero-config one-config]]
      (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
        @(rs/put! store {patient-0-hash patient-0
                         patient-1-hash patient-1})

        @(rc/get cache [:fhir/Patient patient-0-hash :complete])
        @(rc/get cache [:fhir/Patient patient-1-hash :complete])

        (is (= {[:fhir/Patient patient-0-hash :complete] patient-0
                [:fhir/Patient patient-1-hash :complete] patient-1}
               @(st/with-instrument-disabled
                  (rc/multi-get-skip-cache-insertion
                   cache [[:fhir/Patient patient-0-hash :complete]
                          [:fhir/Patient patient-1-hash :complete]])))))))

  (testing "not inserting both patients"
    (doseq [config [config zero-config one-config]]
      (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
        @(rs/put! store {patient-0-hash patient-0
                         patient-1-hash patient-1})

        (is (= {[:fhir/Patient patient-0-hash :complete] patient-0
                [:fhir/Patient patient-1-hash :complete] patient-1}
               @(st/with-instrument-disabled
                  (rc/multi-get-skip-cache-insertion
                   cache [[:fhir/Patient patient-0-hash :complete]
                          [:fhir/Patient patient-1-hash :complete]]))))

        (is (not (contains-key? cache [:fhir/Patient patient-0-hash :complete])))
        (is (not (contains-key? cache [:fhir/Patient patient-1-hash :complete]))))))

  (testing "not inserting second patient"
    (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
      @(rs/put! store {patient-0-hash patient-0
                       patient-1-hash patient-1})

      @(rc/get cache [:fhir/Patient patient-0-hash :complete])

      (is (contains-key? cache [:fhir/Patient patient-0-hash :complete]))

      (is (= {[:fhir/Patient patient-0-hash :complete] patient-0
              [:fhir/Patient patient-1-hash :complete] patient-1}
             @(st/with-instrument-disabled
                (rc/multi-get-skip-cache-insertion
                 cache [[:fhir/Patient patient-0-hash :complete]
                        [:fhir/Patient patient-1-hash :complete]]))))

      (is (not (contains-key? cache [:fhir/Patient patient-1-hash :complete])))))

  (testing "one contained, one non-contained and one not-found patient"
    (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
      @(rs/put! store {patient-0-hash patient-0
                       patient-1-hash patient-1})

      @(rc/get cache [:fhir/Patient patient-0-hash :complete])

      (is (contains-key? cache [:fhir/Patient patient-0-hash :complete]))

      (is (= {[:fhir/Patient patient-0-hash :complete] patient-0
              [:fhir/Patient patient-1-hash :complete] patient-1}
             @(st/with-instrument-disabled
                (rc/multi-get-skip-cache-insertion
                 cache [[:fhir/Patient patient-0-hash :complete]
                        [:fhir/Patient patient-1-hash :complete]
                        [:fhir/Patient patient-2-hash :complete]]))))

      (is (not (contains-key? cache [:fhir/Patient patient-1-hash :complete])))))

  (testing "100 patients"
    (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
      (let [patients (generate-patients 100)]
        @(rs/put! store patients)

        ;; insert every second patient
        (doseq [hash (keys patients)
                :when (< (rand) 0.5)]
          @(rc/get cache [:fhir/Patient hash :complete]))

        (let [size-before (cache-size cache)]

          (given @(st/with-instrument-disabled
                    (rc/multi-get-skip-cache-insertion
                     cache (mapv #(vector :fhir/Patient % :complete) (keys patients))))
            count := 100)

          (is (= size-before (cache-size cache))))))))

(deftype CountingResourceStore [store gets multi-gets]
  rs/ResourceStore
  (-get [_ key]
    (swap! gets inc)
    (rs/-get store key))

  (-multi-get [_ keys]
    (swap! multi-gets inc)
    (rs/-multi-get store keys))

  (-put [_ entries]
    (rs/-put store entries)))

(defmethod ig/init-key ::counting-store
  [_ {:keys [store gets multi-gets]}]
  (->CountingResourceStore store gets multi-gets))

(defn- counting-config [gets multi-gets]
  (-> (assoc-in config [:blaze.db/resource-cache :resource-store]
                (ig/ref ::counting-store))
      (assoc ::counting-store {:store (ig/ref ::rs/kv)
                               :gets gets
                               :multi-gets multi-gets})))

(deftest no-bulk-loading-test
  (testing "the cache loads every key individually"
    ;; Resource stores don't load in bulk, so implementing asyncLoadAll would
    ;; gain nothing but would make Caffeine use its bulk path, where cache
    ;; entries are proxy futures completed by whichever caller happens to load
    ;; them. Functions applied after the futures of all other callers would be
    ;; executed on that arbitrary thread.
    (let [gets (atom 0)
          multi-gets (atom 0)]
      (with-system [{cache :blaze.db/resource-cache store ::rs/kv}
                    (counting-config gets multi-gets)]
        @(rs/put! store {patient-0-hash patient-0
                         patient-1-hash patient-1})

        (is (= {[:fhir/Patient patient-0-hash :complete] patient-0
                [:fhir/Patient patient-1-hash :complete] patient-1}
               @(st/with-instrument-disabled
                  (rc/multi-get cache [[:fhir/Patient patient-0-hash :complete]
                                       [:fhir/Patient patient-1-hash :complete]]))))

        (is (= 2 @gets))
        (is (zero? @multi-gets))))))

(deftest stats-test
  (testing "with non-zero max size"
    (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
      (is (zero? (.hitCount ^CacheStats (ccp/-stats cache))))
      (is (zero? (ccp/-estimated-size cache)))

      @(rs/put! store {patient-0-hash patient-0})
      @(rc/get cache [:fhir/Patient patient-0-hash :complete])

      (is (= 1 (.missCount ^CacheStats (ccp/-stats cache))))
      (is (zero? (.hitCount ^CacheStats (ccp/-stats cache))))
      (is (= 1 (ccp/-estimated-size cache)))

      @(rc/get cache [:fhir/Patient patient-0-hash :complete])

      (is (= 1 (.missCount ^CacheStats (ccp/-stats cache))))
      (is (= 1 (.hitCount ^CacheStats (ccp/-stats cache))))
      (is (= 1 (ccp/-estimated-size cache)))))

  (testing "with zero max size"
    (with-system [{cache :blaze.db/resource-cache store ::rs/kv} zero-config]

      (is (zero? (.hitCount ^CacheStats (ccp/-stats cache))))
      (is (zero? (ccp/-estimated-size cache)))

      @(rs/put! store {patient-0-hash patient-0})
      @(rc/get cache [:fhir/Patient patient-0-hash :complete])

      (is (zero? (.missCount ^CacheStats (ccp/-stats cache))))
      (is (zero? (.hitCount ^CacheStats (ccp/-stats cache))))
      (is (zero? (ccp/-estimated-size cache)))

      @(rc/get cache [:fhir/Patient patient-0-hash :complete])

      (is (zero? (.missCount ^CacheStats (ccp/-stats cache))))
      (is (zero? (.hitCount ^CacheStats (ccp/-stats cache))))
      (is (zero? (ccp/-estimated-size cache))))))

(deftest invalidate-all-test
  (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
    @(rs/put! store {patient-0-hash patient-0})
    @(rc/get cache [:fhir/Patient patient-0-hash :complete])

    (is (= 1 (ccp/-estimated-size cache)))

    (rc/invalidate-all! cache)

    (is (zero? (ccp/-estimated-size cache)))))
