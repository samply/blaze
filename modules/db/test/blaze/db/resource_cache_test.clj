(ns blaze.db.resource-cache-test
  (:require
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem]
    [blaze.db.resource-cache]
    [blaze.db.resource-cache-spec]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store-spec]
    [blaze.db.resource-store.kv :as rs-kv]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.structure-definition-repo]
    [blaze.test-util :as tu :refer [given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [integrant.core :as ig]
    [taoensso.timbre :as log]))


(st/instrument)
(tu/init-fhir-specs)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def patient-0 {:fhir/type :fhir/Patient :id "0"})
(def patient-1 {:fhir/type :fhir/Patient :id "1"})


(def patient-0-hash (hash/generate patient-0))
(def patient-1-hash (hash/generate patient-1))


(def system
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
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing store"
    (given-thrown (ig/init {:blaze.db/resource-cache {}})
      :key := :blaze.db/resource-cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :resource-store))))

  (testing "invalid store"
    (given-thrown (ig/init {:blaze.db/resource-cache {:resource-store ::invalid}})
      :key := :blaze.db/resource-cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (satisfies? rs/ResourceStore ~'%))
      [:explain ::s/problems 0 :val] := ::invalid))

  (testing "invalid max-size"
    (given-thrown (ig/init (assoc-in system [:blaze.db/resource-cache :max-size] ::invalid))
      :key := :blaze.db/resource-cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `nat-int?
      [:explain ::s/problems 0 :val] := ::invalid)))


(deftest get-test
  (with-system [{cache :blaze.db/resource-cache store ::rs/kv} system]
    @(rs/put! store {patient-0-hash patient-0
                     patient-1-hash patient-1})

    (are [patient patient-hash] (= patient @(rs/get cache patient-hash))
      patient-0 patient-0-hash
      patient-1 patient-1-hash)))


(deftest multi-get-test
  (with-system [{cache :blaze.db/resource-cache store ::rs/kv} system]
    @(rs/put! store {patient-0-hash patient-0
                     patient-1-hash patient-1})

    (is (= {patient-0-hash patient-0
            patient-1-hash patient-1}
           @(rs/multi-get cache [patient-0-hash patient-1-hash])))))


(deftest put-test
  (with-system [{:blaze.db/keys [resource-cache] store ::rs/kv} system]
    (is (nil? @(rs/put! resource-cache {patient-0-hash patient-0
                                        patient-1-hash patient-1})))
    (is (= {patient-0-hash patient-0
            patient-1-hash patient-1}
           @(rs/multi-get store [patient-0-hash patient-1-hash])))))
