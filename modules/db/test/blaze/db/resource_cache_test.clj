(ns blaze.db.resource-cache-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.resource-cache]
    [blaze.db.resource-cache-spec]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store-spec]
    [blaze.db.test-util :refer [given-thrown]]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:refer-clojure :exclude [get]))


(st/instrument)
(log/set-level! :trace)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn new-resource-store [entries]
  (reify
    rs/ResourceLookup
    (-get [_ hash]
      (ac/completed-future (clojure.core/get entries hash)))
    (-multi-get [_ hashes]
      (-> (into {} (map #(vector % (clojure.core/get entries %))) hashes)
          (ac/completed-future)))
    rs/ResourceStore))


(def patient-0 {:fhir/type :fhir/Patient :id "0"})
(def patient-1 {:fhir/type :fhir/Patient :id "1"})


(def patient-0-hash (hash/generate patient-0))
(def patient-1-hash (hash/generate patient-1))


(def resource-store
  (new-resource-store
    {patient-0-hash patient-0
     patient-1-hash patient-1}))


(defn- cache [resource-store max-size]
  (-> (ig/init
        {:blaze.db/resource-cache
         {:resource-store resource-store
          :max-size max-size}})
      (:blaze.db/resource-cache)))


(deftest init-test
  (testing "missing store"
    (given-thrown (ig/init {:blaze.db/resource-cache {}})
      :key := :blaze.db/resource-cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :resource-store))))

  (testing "invalid store"
    (given-thrown (ig/init {:blaze.db/resource-cache {:resource-store nil}})
      :key := :blaze.db/resource-cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (satisfies? rs/ResourceLookup ~'%))))

  (testing "invalid max-size"
    (given-thrown (cache resource-store nil)
      :key := :blaze.db/resource-cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `nat-int?)))


(deftest get
  (testing ""
    (let [cache (cache resource-store 0)]
      (are [patient patient-hash] (= patient @(rs/get cache patient-hash))
        patient-0 patient-0-hash
        patient-1 patient-1-hash))))


(deftest multi-get
  (testing ""
    (let [cache (cache resource-store 0)]
      (is (= {patient-0-hash patient-0
              patient-1-hash patient-1}
             @(rs/multi-get cache [patient-0-hash patient-1-hash]))))))
