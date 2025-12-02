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
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.util :as fu]
   [blaze.fhir.writing-context]
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

(def config
  {:blaze.db/resource-cache
   {:resource-store (ig/ref ::rs/kv)
    :max-size 100}
   ::rs/kv
   {:kv-store (ig/ref ::kv/mem)
    :parsing-context (ig/ref :blaze.fhir.parsing-context/resource-store)
    :writing-context (ig/ref :blaze.fhir/writing-context)
    :executor (ig/ref ::rs-kv/executor)}
   ::rs-kv/executor {}
   ::kv/mem {:column-families {}}
   [:blaze.fhir/parsing-context :blaze.fhir.parsing-context/resource-store]
   {:structure-definition-repo structure-definition-repo
    :fail-on-unknown-property false
    :include-summary-only true
    :use-regex false}
   :blaze.fhir/writing-context
   {:structure-definition-repo structure-definition-repo}})

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

  (testing "invalid max-size"
    (given-failed-system (assoc-in config [:blaze.db/resource-cache :max-size] ::invalid)
      :key := :blaze.db/resource-cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::rc/max-size]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest get-test
  (testing "success"
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
        [:fhir/CodeSystem code-system-0-hash :complete] code-system-0)))

  (testing "not-found"
    (with-system [{cache :blaze.db/resource-cache} config]

      (is (nil? @(rc/get cache [:fhir/Patient patient-0-hash :complete]))))))

(deftest multi-get-test
  (testing "found both"
    (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
      @(rs/put! store {patient-0-hash patient-0
                       patient-1-hash patient-1})

      (is (= {[:fhir/Patient patient-0-hash :complete] patient-0
              [:fhir/Patient patient-1-hash :complete] patient-1}
             @(st/with-instrument-disabled
                (rc/multi-get cache [[:fhir/Patient patient-0-hash :complete]
                                     [:fhir/Patient patient-1-hash :complete]]))))))

  (testing "found one"
    (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
      @(rs/put! store {patient-0-hash patient-0})

      (is (= {[:fhir/Patient patient-0-hash :complete] patient-0}
             @(st/with-instrument-disabled
                (rc/multi-get cache [[:fhir/Patient patient-0-hash :complete]
                                     [:fhir/Patient patient-1-hash :complete]])))))))

(defn- generate-patients [n]
  (into
   {}
   (map
    (fn [i]
      (let [patient {:fhir/type :fhir/Patient :id (str i)}]
        [(hash/generate patient) patient])))
   (range n)))

(defn- cache-size [cache]
  (.size (.asMap ^AsyncCache (.cache ^DefaultResourceCache cache))))

(deftest multi-get-skip-cache-insertion-test
  (testing "just returns two existing patients"
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
                        [:fhir/Patient patient-1-hash :complete]]))))))

  (testing "not inserting both patients"
    (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
      @(rs/put! store {patient-0-hash patient-0
                       patient-1-hash patient-1})

      (is (= {[:fhir/Patient patient-0-hash :complete] patient-0
              [:fhir/Patient patient-1-hash :complete] patient-1}
             @(st/with-instrument-disabled
                (rc/multi-get-skip-cache-insertion
                 cache [[:fhir/Patient patient-0-hash :complete]
                        [:fhir/Patient patient-1-hash :complete]]))))

      (is (not (rc/contains? cache [:fhir/Patient patient-0-hash :complete])))
      (is (not (rc/contains? cache [:fhir/Patient patient-1-hash :complete])))))

  (testing "not inserting second patient"
    (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
      @(rs/put! store {patient-0-hash patient-0
                       patient-1-hash patient-1})

      @(rc/get cache [:fhir/Patient patient-0-hash :complete])

      (is (rc/contains? cache [:fhir/Patient patient-0-hash :complete]))

      (is (= {[:fhir/Patient patient-0-hash :complete] patient-0
              [:fhir/Patient patient-1-hash :complete] patient-1}
             @(st/with-instrument-disabled
                (rc/multi-get-skip-cache-insertion
                 cache [[:fhir/Patient patient-0-hash :complete]
                        [:fhir/Patient patient-1-hash :complete]]))))

      (is (not (rc/contains? cache [:fhir/Patient patient-1-hash :complete])))))

  (testing "one contained, one non-contained and one not-found patient"
    (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
      @(rs/put! store {patient-0-hash patient-0
                       patient-1-hash patient-1})

      @(rc/get cache [:fhir/Patient patient-0-hash :complete])

      (is (rc/contains? cache [:fhir/Patient patient-0-hash :complete]))

      (is (= {[:fhir/Patient patient-0-hash :complete] patient-0
              [:fhir/Patient patient-1-hash :complete] patient-1}
             @(st/with-instrument-disabled
                (rc/multi-get-skip-cache-insertion
                 cache [[:fhir/Patient patient-0-hash :complete]
                        [:fhir/Patient patient-1-hash :complete]
                        [:fhir/Patient patient-2-hash :complete]]))))

      (is (not (rc/contains? cache [:fhir/Patient patient-1-hash :complete])))))

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
    (with-system [{cache :blaze.db/resource-cache store ::rs/kv}
                  (assoc-in config [:blaze.db/resource-cache :max-size] 0)]

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
