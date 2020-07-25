(ns blaze.db.impl.index.resource-test
  (:require
    [blaze.async-comp :as ac]
    [blaze.db.hash :as hash]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.codec-stub :as codec-stub]
    [blaze.db.impl.index-spec]
    [blaze.db.impl.index.resource :as resource]
    [blaze.db.impl.index.resource-spec]
    [blaze.db.impl.protocols :as p]
    [blaze.db.kv :as kv]
    [blaze.db.kv-stub :as kv-stub]
    [blaze.db.kv.mem :refer [new-mem-kv-store]]
    [blaze.db.resource-store :as rs]
    [cheshire.core :as json]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]])
  (:import
    [java.time Instant]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defrecord Node [kv-store]
  p/Node)


(defn new-node []
  (->Node
    (new-mem-kv-store
      {:search-param-value-index nil
       :resource-value-index nil
       :compartment-search-param-value-index nil
       :resource-as-of-index nil
       :tx-success-index nil
       :t-by-instant-index nil})))


(defn- new-resource [node type id state t]
  (resource/new-resource node (codec/tid type) id
                         (hash/generate {:resourceType type :id id}) state t))


(deftest resource
  (testing "hash is part of equals"
    (is (not (.equals (new-resource (new-node) "Patient" "0" 0 0)
                      (new-resource (new-node) "Patient" "1" 0 0)))))

  (testing "state is not part of equals"
    (is (.equals (new-resource (new-node) "Patient" "0" 0 0)
                 (new-resource (new-node) "Patient" "0" 1 0))))

  (testing "t is part of equals"
    (is (not (.equals (new-resource (new-node) "Patient" "0" 0 0)
                      (new-resource (new-node) "Patient" "0" 0 1)))))

  (testing "resources can be serialized to JSON"
    (let [node
          (reify
            p/Node
            rs/ResourceLookup
            (-get [_ _]
              (ac/completed-future
                {:id "0"
                 :resourceType "Patient"})))
          resource (new-resource node "Patient" "0" 0 0)]
      (is (= "{\"id\":\"0\",\"resourceType\":\"Patient\",\"meta\":{\"versionId\":\"0\"}}"
             (json/generate-string resource)))))

  (testing "resources has the right meta data"
    (let [{:keys [kv-store] :as node} (new-node)
          resource (new-resource node "Patient" "0" (codec/state 1 :put) 0)]
      (kv/put kv-store (codec/tx-success-entries 0 (Instant/ofEpochSecond 194004)))
      (given (meta resource)
        :type := :fhir/Patient
        :blaze.db/num-changes := 1
        :blaze.db/op := :put
        :blaze.db/t := 0
        [:blaze.db/tx :blaze.db/t] := 0
        [:blaze.db/tx :blaze.db.tx/instant] := (Instant/ofEpochSecond 194004)
        count := 5))))


(deftest tx
  (st/unstrument `resource/tx)
  (codec-stub/t-key ::t ::t-key)

  (testing "existing transaction"
    (kv-stub/get ::kv-store :tx-success-index ::t-key #{::tx-bytes})
    (codec-stub/decode-tx ::tx-bytes ::t ::tx)

    (is (= ::tx (resource/tx ::kv-store ::t))))

  (testing "missing transaction"
    (kv-stub/get ::kv-store :tx-success-index ::t-key nil?)

    (is (nil? (resource/tx ::kv-store ::t)))))
