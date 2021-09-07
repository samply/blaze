(ns blaze.db.node-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.async.comp-spec]
    [blaze.db.api :as d]
    [blaze.db.api-spec]
    [blaze.db.impl.db-spec]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem]
    [blaze.db.kv.mem-spec]
    [blaze.db.node :as node]
    [blaze.db.node-spec]
    [blaze.db.node.resource-indexer :as resource-indexer]
    [blaze.db.resource-handle-cache]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store.kv :as rs-kv]
    [blaze.db.search-param-registry]
    [blaze.db.tx-cache]
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log-spec]
    [blaze.db.tx-log.local]
    [blaze.db.tx-log.local-spec]
    [blaze.log]
    [blaze.test-util :refer [given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [java-time :as time]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def system
  {:blaze.db/node
   {:tx-log (ig/ref :blaze.db/tx-log)
    :resource-handle-cache (ig/ref :blaze.db/resource-handle-cache)
    :tx-cache (ig/ref :blaze.db/tx-cache)
    :indexer-executor (ig/ref :blaze.db.node/indexer-executor)
    :resource-store (ig/ref :blaze.db/resource-store)
    :kv-store (ig/ref :blaze.db/index-kv-store)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :poll-timeout (time/millis 10)}

   ::tx-log/local
   {:kv-store (ig/ref :blaze.db/transaction-kv-store)
    :clock (ig/ref :blaze.test/clock)}
   [::kv/mem :blaze.db/transaction-kv-store]
   {:column-families {}}
   :blaze.test/clock {}

   :blaze.db/resource-handle-cache {}

   :blaze.db/tx-cache
   {:kv-store (ig/ref :blaze.db/index-kv-store)}

   :blaze.db.node/indexer-executor {}

   [::kv/mem :blaze.db/index-kv-store]
   {:column-families
    {:search-param-value-index nil
     :resource-value-index nil
     :compartment-search-param-value-index nil
     :compartment-resource-type-index nil
     :active-search-params nil
     :tx-success-index {:reverse-comparator? true}
     :tx-error-index nil
     :t-by-instant-index {:reverse-comparator? true}
     :resource-as-of-index nil
     :type-as-of-index nil
     :system-as-of-index nil
     :type-stats-index nil
     :system-stats-index nil}}

   ::rs/kv
   {:kv-store (ig/ref :blaze.db/resource-kv-store)
    :executor (ig/ref ::rs-kv/executor)}
   [::kv/mem :blaze.db/resource-kv-store]
   {:column-families {}}
   ::rs-kv/executor {}

   :blaze.db/search-param-registry {}})


(defmethod ig/init-key ::resource-store-failing-on-get [_ _]
  (reify
    rs/ResourceLookup
    (-get [_ _]
      (ac/completed-future {::anom/category ::anom/fault}))
    (-multi-get [_ _]
      (ac/completed-future {::anom/category ::anom/fault}))
    rs/ResourceStore
    (-put [_ _]
      (ac/completed-future nil))))


(def resource-store-failing-on-get-system
  (-> (assoc-in system [:blaze.db/node :resource-store]
                (ig/ref ::resource-store-failing-on-get))
      (assoc ::resource-store-failing-on-get {})))


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.db/node nil})
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.db/node {}})
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :tx-log))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :resource-handle-cache))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :tx-cache))
      [:explain ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :indexer-executor))
      [:explain ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :kv-store))
      [:explain ::s/problems 5 :pred] := `(fn ~'[%] (contains? ~'% :resource-store))
      [:explain ::s/problems 6 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry)))))


(deftest transact-test
  (testing "with slow transaction result fetching"
    (testing "create"
      (testing "one Patient"
        (with-system [{:blaze.db/keys [node]} system]
          @(-> (node/submit-tx node [[:create {:fhir/type :fhir/Patient :id "0"}]])
               (ac/then-compose
                 (fn [t]
                   (Thread/sleep 100)
                   (node/tx-result node t))))

          (given @(d/pull node (d/resource-handle (d/db node) "Patient" "0"))
            :fhir/type := :fhir/Patient
            :id := "0"
            [:meta :versionId] := #fhir/id"1"
            [meta :blaze.db/op] := :create))))

    (testing "with failing resource storage"
      (testing "on get"
        (with-system [{:blaze.db/keys [node]} resource-store-failing-on-get-system]
          (try
            @(-> (node/submit-tx node [[:put {:fhir/type :fhir/Patient :id "0"}]])
                 (ac/then-compose
                   (fn [t]
                     (Thread/sleep 100)
                     (node/tx-result node t))))
            (catch Exception e
              (given (ex-data (ex-cause e))
                ::anom/category := ::anom/fault))))))

    (testing "with failing resource indexer"
      (with-redefs
        [resource-indexer/index-resources
         (fn [_ _]
           (ac/completed-future {::anom/category ::anom/fault ::x ::y}))]
        (with-system [{:blaze.db/keys [node]} system]
          (try
            @(-> (node/submit-tx node [[:put {:fhir/type :fhir/Patient :id "0"}]])
                 (ac/then-compose
                   (fn [t]
                     (Thread/sleep 100)
                     (node/tx-result node t))))
            (catch Exception e
              (given (ex-data (ex-cause e))
                ::anom/category := ::anom/fault
                ::x ::y))))))))
