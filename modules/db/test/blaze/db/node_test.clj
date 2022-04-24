(ns blaze.db.node-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.async.comp-spec]
    [blaze.db.api :as d]
    [blaze.db.api-spec]
    [blaze.db.impl.db-spec]
    [blaze.db.kv.mem-spec]
    [blaze.db.node :as node]
    [blaze.db.node-spec]
    [blaze.db.node.resource-indexer :as resource-indexer]
    [blaze.db.node.tx-indexer :as-alias tx-indexer]
    [blaze.db.resource-handle-cache]
    [blaze.db.resource-store :as rs]
    [blaze.db.search-param-registry]
    [blaze.db.test-util :refer [system]]
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log-spec]
    [blaze.db.tx-log.local-spec]
    [blaze.executors :as ex]
    [blaze.fhir.structure-definition-repo]
    [blaze.log]
    [blaze.metrics.spec]
    [blaze.test-util :refer [given-failed-future given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defmethod ig/init-key ::resource-store-failing-on-get [_ _]
  (reify
    rs/ResourceStore
    (-get [_ _]
      (ac/completed-future {::anom/category ::anom/fault}))
    (-multi-get [_ _]
      (ac/completed-future {::anom/category ::anom/fault}))
    (-put [_ _]
      (ac/completed-future nil))))


(def resource-store-failing-on-get-system
  (-> (assoc-in system [:blaze.db/node :resource-store]
                (ig/ref ::resource-store-failing-on-get))
      (assoc ::resource-store-failing-on-get {})))


(defmethod ig/init-key ::resource-store-slow-on-put [_ {:keys [resource-store]}]
  (reify
    rs/ResourceStore
    (-get [_ hash]
      (rs/get resource-store hash))
    (-multi-get [_ hashes]
      (rs/multi-get resource-store hashes))
    (-put [_ entries]
      (-> (rs/put! resource-store entries)
          (ac/then-apply-async (fn [_] (Thread/sleep 100)))))))


(def resource-store-slow-on-put
  (-> (assoc-in system [:blaze.db/node :resource-store]
                (ig/ref ::resource-store-slow-on-put))
      (assoc ::resource-store-slow-on-put
             {:resource-store (ig/ref :blaze.db/resource-store)})))


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
    (given-thrown (ig/init {:blaze.db/node {:tx-log ::invalid}})
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :resource-handle-cache))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :tx-cache))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :indexer-executor))
      [:explain ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :kv-store))
      [:explain ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :resource-indexer))
      [:explain ::s/problems 5 :pred] := `(fn ~'[%] (contains? ~'% :resource-store))
      [:explain ::s/problems 6 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))
      [:explain ::s/problems 7 :pred] := `(fn ~'[%] (satisfies? tx-log/TxLog ~'%))
      [:explain ::s/problems 7 :val] := ::invalid))

  (testing "invalid enforce-referential-integrity"
    (given-thrown (ig/init {:blaze.db/node {:enforce-referential-integrity ::invalid}})
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :tx-log))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :resource-handle-cache))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :tx-cache))
      [:explain ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :indexer-executor))
      [:explain ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :kv-store))
      [:explain ::s/problems 5 :pred] := `(fn ~'[%] (contains? ~'% :resource-indexer))
      [:explain ::s/problems 6 :pred] := `(fn ~'[%] (contains? ~'% :resource-store))
      [:explain ::s/problems 7 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))
      [:explain ::s/problems 8 :pred] := `boolean?
      [:explain ::s/problems 8 :val] := ::invalid)))


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
           (ac/failed-future (ex-info "" {::anom/category ::anom/fault ::x ::y})))]

        (testing "fetching the result immediately"
          (with-system [{:blaze.db/keys [node]} resource-store-slow-on-put]
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
