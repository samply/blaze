(ns blaze.db.resource-cache-test
  (:require
   [blaze.cache-collector.protocols :as ccp]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.resource-cache :as resource-cache]
   [blaze.db.resource-cache-spec]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store-spec]
   [blaze.db.resource-store.kv :as rs-kv]
   [blaze.db.resource-store.spec :refer [resource-store?]]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [integrant.core :as ig]
   [taoensso.timbre :as log])
  (:import
   [com.github.benmanes.caffeine.cache.stats CacheStats]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def patient-0 {:fhir/type :fhir/Patient :id "0"})
(def patient-1 {:fhir/type :fhir/Patient :id "1"})

(def patient-0-hash (hash/generate patient-0))
(def patient-1-hash (hash/generate patient-1))

(def config
  {:blaze.db/resource-cache
   {:resource-store (ig/ref ::rs/kv)
    :max-size 100}
   ::rs/kv
   {:kv-store (ig/ref ::kv/mem)
    :executor (ig/ref ::rs-kv/executor)}
   ::rs-kv/executor {}
   ::kv/mem {:column-families {}}})

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.db/resource-cache nil})
      :key := :blaze.db/resource-cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing store"
    (given-thrown (ig/init {:blaze.db/resource-cache {}})
      :key := :blaze.db/resource-cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :resource-store))))

  (testing "invalid store"
    (given-thrown (ig/init {:blaze.db/resource-cache {:resource-store ::invalid}})
      :key := :blaze.db/resource-cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `resource-store?
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid max-size"
    (given-thrown (ig/init (assoc-in config [:blaze.db/resource-cache :max-size] ::invalid))
      :key := :blaze.db/resource-cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `nat-int?
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest get-test
  (testing "success"
    (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
      @(rs/put! store {patient-0-hash patient-0
                       patient-1-hash patient-1})

      (are [patient patient-hash] (= patient @(rs/get cache patient-hash))
        patient-0 patient-0-hash
        patient-1 patient-1-hash)))

  (testing "not-found"
    (with-system [{cache :blaze.db/resource-cache} config]

      (is (nil? @(rs/get cache patient-0-hash))))))

(deftest multi-get-test
  (testing "found both"
    (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
      @(rs/put! store {patient-0-hash patient-0
                       patient-1-hash patient-1})

      (is (= {patient-0-hash patient-0
              patient-1-hash patient-1}
             @(rs/multi-get cache [patient-0-hash patient-1-hash])))))

  (testing "found one"
    (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
      @(rs/put! store {patient-0-hash patient-0})

      (is (= {patient-0-hash patient-0}
             @(rs/multi-get cache [patient-0-hash patient-1-hash]))))))

(deftest put-test
  (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
    (is (nil? @(rs/put! cache {patient-0-hash patient-0
                               patient-1-hash patient-1})))
    (is (= {patient-0-hash patient-0
            patient-1-hash patient-1}
           @(rs/multi-get store [patient-0-hash patient-1-hash])))))

(deftest stats-test
  (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
    (is (zero? (.hitCount ^CacheStats (ccp/-stats cache))))
    (is (zero? (ccp/-estimated-size cache)))

    @(rs/put! store {patient-0-hash patient-0})
    @(rs/get cache patient-0-hash)

    (is (= 1 (.missCount ^CacheStats (ccp/-stats cache))))
    (is (zero? (.hitCount ^CacheStats (ccp/-stats cache))))
    (is (= 1 (ccp/-estimated-size cache)))

    @(rs/get cache patient-0-hash)

    (is (= 1 (.missCount ^CacheStats (ccp/-stats cache))))
    (is (= 1 (.hitCount ^CacheStats (ccp/-stats cache))))
    (is (= 1 (ccp/-estimated-size cache)))))

(deftest invalidate-all-test
  (with-system [{cache :blaze.db/resource-cache store ::rs/kv} config]
    @(rs/put! store {patient-0-hash patient-0})
    @(rs/get cache patient-0-hash)

    (is (= 1 (ccp/-estimated-size cache)))

    (resource-cache/invalidate-all! cache)

    (is (zero? (ccp/-estimated-size cache)))))
