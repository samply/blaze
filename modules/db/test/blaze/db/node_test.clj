(ns blaze.db.node-test
  (:require
    [blaze.anomaly :as ba]
    [blaze.async.comp :as ac]
    [blaze.async.comp-spec]
    [blaze.db.api :as d]
    [blaze.db.api-spec]
    [blaze.db.impl.db-spec]
    [blaze.db.impl.index.tx-success :as tx-success]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem-spec]
    [blaze.db.node :as node]
    [blaze.db.node-spec]
    [blaze.db.node.resource-indexer :as resource-indexer]
    [blaze.db.node.tx-indexer :as-alias tx-indexer]
    [blaze.db.node.version :as version]
    [blaze.db.resource-handle-cache]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store.spec :refer [resource-store?]]
    [blaze.db.search-param-registry]
    [blaze.db.search-param-registry.spec :refer [search-param-registry?]]
    [blaze.db.spec :refer [cache? loading-cache?]]
    [blaze.db.test-util :refer [system]]
    [blaze.db.tx-log-spec]
    [blaze.db.tx-log.local-spec]
    [blaze.db.tx-log.spec :refer [tx-log?]]
    [blaze.executors :as ex]
    [blaze.log]
    [blaze.metrics.spec]
    [blaze.test-util :as tu :refer [given-failed-future given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant]
    [java.util.concurrent TimeUnit]))


(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(defmethod ig/init-key ::resource-store-failing-on-get [_ _]
  (reify
    rs/ResourceStore
    (-get [_ _]
      (ac/completed-future {::anom/category ::anom/fault}))
    (-multi-get [_ _]
      (ac/completed-future {::anom/category ::anom/fault}))
    (-put [_ _]
      (ac/completed-future nil))))


(def ^:private resource-store-failing-on-get-system
  (merge-with
    merge
    system
    {:blaze.db/node
     {:resource-store (ig/ref ::resource-store-failing-on-get)}
     :blaze.db.node/resource-indexer
     {:resource-store (ig/ref ::resource-store-failing-on-get)}
     ::resource-store-failing-on-get {}}))


(def ^:private delayed-executor
  (ac/delayed-executor 100 TimeUnit/MILLISECONDS))


(defmethod ig/init-key ::resource-store-slow-on-put [_ {:keys [resource-store]}]
  (reify
    rs/ResourceStore
    (-get [_ hash]
      (rs/get resource-store hash))
    (-multi-get [_ hashes]
      (rs/multi-get resource-store hashes))
    (-put [_ entries]
      (-> (rs/put! resource-store entries)
          (ac/then-apply-async identity delayed-executor)))))


(def ^:private resource-store-slow-on-put-system
  (merge-with
    merge
    system
    {:blaze.db/node
     {:resource-store (ig/ref ::resource-store-slow-on-put)}
     :blaze.db.node/resource-indexer
     {:resource-store (ig/ref ::resource-store-slow-on-put)}
     ::resource-store-slow-on-put
     {:resource-store (ig/ref ::rs/kv)}}))


(defn- with-index-store-version [system version]
  (assoc-in system [[::kv/mem :blaze.db/index-kv-store] :init-data]
            [[version/key (version/encode-value version)]
             (tx-success/index-entry 1 Instant/EPOCH)]))


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
      [:explain ::s/problems 5 :pred] := `(fn ~'[%] (contains? ~'% :resource-indexer))
      [:explain ::s/problems 6 :pred] := `(fn ~'[%] (contains? ~'% :resource-store))
      [:explain ::s/problems 7 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))))

  (testing "invalid tx-log"
    (given-thrown (ig/init (assoc-in system [:blaze.db/node :tx-log] ::invalid))
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `tx-log?
      [:explain ::s/problems 0 :val] := ::invalid))

  (testing "invalid resource-handle-cache"
    (given-thrown (ig/init (assoc-in system [:blaze.db/node :resource-handle-cache] ::invalid))
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `cache?
      [:explain ::s/problems 0 :val] := ::invalid))

  (testing "invalid tx-cache"
    (given-thrown (ig/init (assoc-in system [:blaze.db/node :tx-cache] ::invalid))
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `loading-cache?
      [:explain ::s/problems 0 :val] := ::invalid))

  (testing "invalid indexer-executor"
    (given-thrown (ig/init (assoc-in system [:blaze.db/node :indexer-executor] ::invalid))
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `ex/executor?
      [:explain ::s/problems 0 :val] := ::invalid))

  (testing "invalid kv-store"
    (given-thrown (ig/init (assoc-in system [:blaze.db/node :kv-store] ::invalid))
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `kv/store?
      [:explain ::s/problems 0 :val] := ::invalid))

  (testing "invalid resource-indexer"
    (given-thrown (ig/init (assoc-in system [:blaze.db/node :resource-indexer] ::invalid))
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?
      [:explain ::s/problems 0 :val] := ::invalid))

  (testing "invalid resource-store"
    (given-thrown (ig/init (assoc-in system [:blaze.db/node :resource-store] ::invalid))
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `resource-store?
      [:explain ::s/problems 0 :val] := ::invalid))

  (testing "invalid search-param-registry"
    (given-thrown (ig/init (assoc-in system [:blaze.db/node :search-param-registry] ::invalid))
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `search-param-registry?
      [:explain ::s/problems 0 :val] := ::invalid))

  (testing "invalid enforce-referential-integrity"
    (given-thrown (ig/init (assoc-in system [:blaze.db/node :enforce-referential-integrity] ::invalid))
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `boolean?
      [:explain ::s/problems 0 :val] := ::invalid))

  (testing "incompatible version"
    (given-thrown (ig/init (with-index-store-version system -1))
      :key := :blaze.db/node
      :reason := ::ig/build-threw-exception
      [:cause-data :expected-version] := 0
      [:cause-data :actual-version] := -1)))


(deftest duration-seconds-collector-init-test
  (with-system [{collector ::node/duration-seconds} {::node/duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))


(deftest transaction-sizes-collector-init-test
  (with-system [{collector ::node/transaction-sizes} {::node/transaction-sizes {}}]
    (is (s/valid? :blaze.metrics/collector collector))))


(deftest tx-indexer-duration-seconds-collector-init-test
  (with-system [{collector ::tx-indexer/duration-seconds} {::tx-indexer/duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))


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
           (ac/failed-future (ex-info "" (ba/fault "" ::x ::y))))]

        (testing "fetching the result immediately"
          (with-system [{:blaze.db/keys [node]} resource-store-slow-on-put-system]
            (given-failed-future
              (-> (node/submit-tx node [[:put {:fhir/type :fhir/Patient :id "0"}]])
                  (ac/then-compose (partial node/tx-result node)))
              ::anom/category := ::anom/fault
              ::x ::y)))

        (testing "wait before fetching the result"
          (with-system [{:blaze.db/keys [node]} system]
            (given-failed-future
              (-> (node/submit-tx node [[:put {:fhir/type :fhir/Patient :id "0"}]])
                  (ac/then-compose
                    (fn [t]
                      (Thread/sleep 100)
                      (node/tx-result node t))))
              ::anom/category := ::anom/fault
              ::x ::y)))))))


(deftest indexer-executor-shutdown-timeout-test
  (let [{::node/keys [indexer-executor] :as system}
        (ig/init {::node/indexer-executor {}})]

    ;; will produce a timeout, because the function runs 11 seconds
    (ex/execute! indexer-executor #(Thread/sleep 11000))

    ;; ensure that the function is called before the scheduler is halted
    (Thread/sleep 100)

    (ig/halt! system)

    ;; the scheduler is shut down
    (is (ex/shutdown? indexer-executor))

    ;; but it isn't terminated yet
    (is (not (ex/terminated? indexer-executor)))))


(deftest existing-data-with-compatible-version
  (with-system [{:blaze.db/keys [node]} (with-index-store-version system 0)]
    (is node)))
