(ns blaze.db.resource-cache-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.resource-cache]
    [blaze.db.resource-cache-spec]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store-spec]
    [blaze.fhir.hash :as hash]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:refer-clojure :exclude [get]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
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


(deftest put
  (testing "on no storage success"
    (let [resource-store (reify
                           rs/ResourceLookup
                           rs/ResourceStore
                           (-put [_ _]
                             (ac/failed-future (ex-info "" {}))))
          cache (cache resource-store 1)
          entries {patient-0-hash patient-0}]
      (try
        @(rs/put cache entries)
        (catch Exception e
          (is (empty? (:successfully-stored-hashes (ex-data (ex-cause e)))))))))

  (testing "on partial storage success"
    (let [resource-store (reify
                           rs/ResourceLookup
                           rs/ResourceStore
                           (-put [_ _]
                             (-> (ex-info
                                   "" {:successfully-stored-hashes
                                       #{patient-0-hash}})
                                 (ac/failed-future))))
          cache (cache resource-store 1)
          entries {patient-0-hash patient-0
                   patient-1-hash patient-1}]
      (try
        @(rs/put cache entries)
        (catch Exception e
          (testing "storing patient-0 was successful, so it is returned in ex-data"
            (let [ex-data (ex-data (ex-cause e))]
              (is (= #{patient-0-hash} (:successfully-stored-hashes ex-data)))))))

      (testing "storing patient-0 was successful, so it is cached"
        (is (= patient-0 @(rs/get cache patient-0-hash))))))

  (testing "on successful storage of all entries"
    (let [resource-store (reify
                           rs/ResourceLookup
                           rs/ResourceStore
                           (-put [_ _]
                             (ac/completed-future nil)))
          cache (cache resource-store 2)
          entries {patient-0-hash patient-0
                   patient-1-hash patient-1}
          result @(rs/put cache entries)]

      (testing "storing was successful"
        (is (nil? result)))

      (testing "storing patient-0 was successful, so it is cached"
        (is (= patient-0 @(rs/get cache patient-0-hash))))

      (testing "storing patient-1 was successful, so it is cached"
        (is (= patient-1 @(rs/get cache patient-1-hash)))))))
