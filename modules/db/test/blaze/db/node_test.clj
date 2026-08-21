(ns blaze.db.node-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.async.comp-spec]
   [blaze.async.flow :as flow]
   [blaze.async.flow-spec]
   [blaze.db.api :as d]
   [blaze.db.api-spec]
   [blaze.db.impl.db-spec]
   [blaze.db.impl.index.patient-last-change :as plc]
   [blaze.db.impl.index.patient-last-change-spec]
   [blaze.db.impl.index.tx-success :as tx-success]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem-spec]
   [blaze.db.kv.protocols :as p]
   [blaze.db.node :as node]
   [blaze.db.node-spec]
   [blaze.db.node.resource-indexer :as resource-indexer]
   [blaze.db.node.resource-indexer.spec]
   [blaze.db.node.spec]
   [blaze.db.node.subscription :as sub]
   [blaze.db.node.subscription-spec]
   [blaze.db.node.tx-indexer :as-alias tx-indexer]
   [blaze.db.node.version :as version]
   [blaze.db.resource-cache.spec]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.spec]
   [blaze.db.search-param-registry]
   [blaze.db.search-param-registry.spec]
   [blaze.db.spec]
   [blaze.db.test-util :refer [config wait-for]]
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log-spec]
   [blaze.db.tx-log.local-spec]
   [blaze.db.tx-log.spec]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.metrics.core :as metrics]
   [blaze.metrics.spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.scheduler.spec]
   [blaze.scheduler.test-util :as stu]
   [blaze.test-util :as tu :refer [given-failed-future with-global-log-capture]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [prometheus.alpha :as prom]
   [taoensso.timbre :as log])
  (:import
   [java.time Instant]
   [java.util.concurrent TimeUnit]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defmethod ig/init-key ::resource-store-failing-on-get [_ _]
  (reify rs/ResourceStore
    (-get [_ _]
      (ac/completed-future {::anom/category ::anom/fault}))
    (-multi-get [_ _]
      (ac/completed-future {::anom/category ::anom/fault}))
    (-put [_ _]
      (ac/completed-future nil))))

(def ^:private resource-store-failing-on-get-config
  (merge-with
   merge
   config
   {:blaze.db/node
    {:resource-store (ig/ref ::resource-store-failing-on-get)}
    :blaze.db/resource-cache
    {:resource-store (ig/ref ::resource-store-failing-on-get)}
    ::node/resource-indexer
    {:resource-store (ig/ref ::resource-store-failing-on-get)}
    ::resource-store-failing-on-get {}}))

(def ^:private delayed-executor
  (ac/delayed-executor 100 TimeUnit/MILLISECONDS))

(defmethod ig/init-key ::resource-store-slow-on-put [_ {:keys [resource-store]}]
  (reify rs/ResourceStore
    (-get [_ key]
      (rs/get resource-store key))
    (-multi-get [_ keys]
      (rs/multi-get resource-store keys))
    (-put [_ entries]
      (-> (rs/put! resource-store entries)
          (ac/then-apply-async identity delayed-executor)))))

(def ^:private resource-store-slow-on-put-config
  (merge-with
   merge
   config
   {:blaze.db/node
    {:resource-store (ig/ref ::resource-store-slow-on-put)}
    :blaze.db/resource-cache
    {:resource-store (ig/ref ::resource-store-slow-on-put)}
    ::node/resource-indexer
    {:resource-store (ig/ref ::resource-store-slow-on-put)}
    ::resource-store-slow-on-put
    {:resource-store (ig/ref ::rs/kv)}}))

(defmethod ig/init-key ::put-counting-resource-store [_ {:keys [resource-store]}]
  (let [put-count (atom 0)]
    (with-meta
      (reify rs/ResourceStore
        (-get [_ key]
          (rs/get resource-store key))
        (-multi-get [_ keys]
          (rs/multi-get resource-store keys))
        (-put [_ entries]
          (swap! put-count inc)
          (rs/put! resource-store entries)))
      {:put-count put-count})))

(defmethod ig/init-key ::blocking-index-kv-store [_ {:keys [kv-store]}]
  (let [release-put (promise)]
    (with-meta
      (reify
        p/KvStore
        (-new-snapshot [_]
          (p/-new-snapshot kv-store))
        (-get [_ column-family key]
          (p/-get kv-store column-family key))
        (-put [_ entries]
          @release-put
          (p/-put kv-store entries)))
      {:release-put release-put})))

(def ^:private in-flight-config
  "A config with a node that indexes no transaction before the index key-value
  store is released and that allows only two in-flight transactions."
  (merge-with
   merge
   config
   {:blaze.db/node
    {:kv-store (ig/ref ::blocking-index-kv-store)
     :resource-store (ig/ref ::put-counting-resource-store)
     :max-in-flight-transactions 2}
    :blaze.db/tx-cache
    {:kv-store (ig/ref ::blocking-index-kv-store)}
    :blaze.db/resource-cache
    {:resource-store (ig/ref ::put-counting-resource-store)}
    ::node/resource-indexer
    {:kv-store (ig/ref ::blocking-index-kv-store)
     :resource-store (ig/ref ::put-counting-resource-store)}
    ::put-counting-resource-store
    {:resource-store (ig/ref ::rs/kv)}
    ::blocking-index-kv-store
    {:kv-store (ig/ref :blaze.db/index-kv-store)}}))

(defmethod ig/init-key ::resource-store-failing-on-put [_ {:keys [resource-store]}]
  (reify rs/ResourceStore
    (-get [_ key]
      (rs/get resource-store key))
    (-multi-get [_ keys]
      (rs/multi-get resource-store keys))
    (-put [_ _]
      (ac/failed-future (ba/ex-anom (ba/fault "put-error"))))))

(def ^:private failing-resource-store-on-put-config
  (merge-with
   merge
   config
   {:blaze.db/node
    {:resource-store (ig/ref ::resource-store-failing-on-put)
     :max-in-flight-transactions 2}
    :blaze.db/resource-cache
    {:resource-store (ig/ref ::resource-store-failing-on-put)}
    ::node/resource-indexer
    {:resource-store (ig/ref ::resource-store-failing-on-put)}
    ::resource-store-failing-on-put
    {:resource-store (ig/ref ::rs/kv)}}))

(defn- with-index-store-version [config version]
  (assoc-in config [[::kv/mem :blaze.db/index-kv-store] :init-data]
            (cond-> [(tx-success/index-entry 1 Instant/EPOCH)]
              version
              (conj [:default version/key (version/encode-value version)]))))

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.db/node nil}
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.db/node {}}
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :tx-log))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :tx-cache))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :kv-store))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :resource-indexer))
      [:cause-data ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :resource-cache))
      [:cause-data ::s/problems 5 :pred] := `(fn ~'[%] (contains? ~'% :resource-store))
      [:cause-data ::s/problems 6 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))
      [:cause-data ::s/problems 7 :pred] := `(fn ~'[%] (contains? ~'% :scheduler))))

  (testing "missing tx-cache"
    (given-failed-system (update config :blaze.db/node dissoc :tx-cache)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :tx-cache))))

  (testing "missing kv-store"
    (given-failed-system (update config :blaze.db/node dissoc :kv-store)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :kv-store))))

  (testing "missing resource-indexer"
    (given-failed-system (update config :blaze.db/node dissoc :resource-indexer)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :resource-indexer))))

  (testing "missing resource-cache"
    (given-failed-system (update config :blaze.db/node dissoc :resource-cache)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :resource-cache))))

  (testing "missing resource-store"
    (given-failed-system (update config :blaze.db/node dissoc :resource-store)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :resource-store))))

  (testing "missing search-param-registry"
    (given-failed-system (update config :blaze.db/node dissoc :search-param-registry)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))))

  (testing "missing scheduler"
    (given-failed-system (update config :blaze.db/node dissoc :scheduler)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :scheduler))))

  (testing "invalid tx-log"
    (given-failed-system (assoc-in config [:blaze.db/node :tx-log] ::invalid)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/tx-log]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid tx-cache"
    (given-failed-system (assoc-in config [:blaze.db/node :tx-cache] ::invalid)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/tx-cache]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid kv-store"
    (given-failed-system (assoc-in config [:blaze.db/node :kv-store] ::invalid)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/kv-store]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid resource-indexer"
    (given-failed-system (assoc-in config [:blaze.db/node :resource-indexer] ::invalid)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::node/resource-indexer]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid resource-cache"
    (given-failed-system (assoc-in config [:blaze.db/node :resource-cache] ::invalid)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/resource-cache]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid resource-store"
    (given-failed-system (assoc-in config [:blaze.db/node :resource-store] ::invalid)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/resource-store]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid search-param-registry"
    (given-failed-system (assoc-in config [:blaze.db/node :search-param-registry] ::invalid)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/search-param-registry]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid scheduler"
    (given-failed-system (assoc-in config [:blaze.db/node :scheduler] ::invalid)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/scheduler]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid enforce-referential-integrity"
    (given-failed-system (assoc-in config [:blaze.db/node :enforce-referential-integrity] ::invalid)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/enforce-referential-integrity]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid allow-multiple-delete"
    (given-failed-system (assoc-in config [:blaze.db/node :allow-multiple-delete] ::invalid)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/allow-multiple-delete]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid poll-timeout"
    (given-failed-system (assoc-in config [:blaze.db/node :poll-timeout] ::invalid)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::node/poll-timeout]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid queue-capacity"
    (given-failed-system (assoc-in config [:blaze.db/node :queue-capacity] ::invalid)
      :key := :blaze.db/node
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::node/queue-capacity]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "incompatible version"
    (given-failed-system (with-index-store-version config -1)
      :key := :blaze.db/node
      :reason := ::ig/build-threw-exception
      [:cause-data :expected-version] := 0
      [:cause-data :actual-version] := -1)))

(deftest duration-seconds-collector-init-test
  (with-system [{collector ::node/duration-seconds} {::node/duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(def ^:private publishing-lag-config
  (assoc config ::node/publishing-lag-collector
         {:nodes {:blaze.db/node (ig/ref :blaze.db/node)}}))

(deftest publishing-lag-collector-init-test
  (testing "nil config"
    (given-failed-system {::node/publishing-lag-collector nil}
      :key := ::node/publishing-lag-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing nodes"
    (given-failed-system {::node/publishing-lag-collector {}}
      :key := ::node/publishing-lag-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :nodes))))

  (testing "invalid nodes"
    (given-failed-system {::node/publishing-lag-collector {:nodes ::invalid}}
      :key := ::node/publishing-lag-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::node/nodes]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "is a collector"
    (with-system [{collector ::node/publishing-lag-collector} publishing-lag-config]
      (is (s/valid? :blaze.metrics/collector collector)))))

(deftest publishing-lag-collector-test
  (testing "without any subscriber, there is no sample"
    (with-system [{collector ::node/publishing-lag-collector} publishing-lag-config]
      (given (metrics/collect collector)
        count := 2
        [0 :name] := "blaze_db_node_publishing_lag_transactions"
        [0 :type] := :gauge
        [0 :samples count] := 0
        [1 :name] := "blaze_db_node_publishing_lag_t"
        [1 :type] := :gauge
        [1 :samples count] := 0)))

  (testing "without any transaction, the subscriber has no lag"
    (with-system [{:blaze.db/keys [node]
                   collector ::node/publishing-lag-collector} publishing-lag-config]
      (d/subscribe-changes! node "Task" "test" (flow/collector (ac/future)))

      (given (metrics/collect collector)
        count := 2
        [0 :name] := "blaze_db_node_publishing_lag_transactions"
        [0 :type] := :gauge
        [0 :samples count] := 1
        [0 :samples 0 :label-values] := ["main" "Task" "test"]
        [0 :samples 0 :value] := 0.0
        [1 :name] := "blaze_db_node_publishing_lag_t"
        [1 :type] := :gauge
        [1 :samples count] := 1
        [1 :samples 0 :label-values] := ["main" "Task" "test"]
        [1 :samples 0 :value] := 0.0)))

  (testing "one subscriber name used for two types results in two series"
    (with-system [{:blaze.db/keys [node]
                   collector ::node/publishing-lag-collector} publishing-lag-config]
      (d/subscribe-changes! node "Task" "test" (flow/collector (ac/future)))
      (d/subscribe-changes! node "Observation" "test" (flow/collector (ac/future)))

      (given (metrics/collect collector)
        count := 2
        [0 :name] := "blaze_db_node_publishing_lag_transactions"
        [0 :type] := :gauge
        [0 :samples count] := 2
        [0 :samples #(into #{} (map :label-values) %)] :=
        #{["main" "Task" "test"] ["main" "Observation" "test"]}
        [1 :name] := "blaze_db_node_publishing_lag_t"
        [1 :type] := :gauge
        [1 :samples count] := 2
        [1 :samples #(into #{} (map :label-values) %)] :=
        #{["main" "Task" "test"] ["main" "Observation" "test"]}))))

(deftest submit-rejections-total-collector-init-test
  (with-system [{collector ::node/submit-rejections-total}
                {::node/submit-rejections-total {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest transaction-sizes-collector-init-test
  (with-system [{collector ::node/transaction-sizes} {::node/transaction-sizes {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest tx-indexer-duration-seconds-collector-init-test
  (with-system [{collector ::tx-indexer/duration-seconds} {::tx-indexer/duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(defn- tx-result-after-indexing
  "Returns a CompletableFuture of the result of the transaction with `t`,
  fetching it only after `t` was either indexed or indexing failed.

  That way the result is already available when `node/tx-result` is called."
  [node t]
  (-> (d/sync node t)
      (ac/then-compose (fn [_] (node/tx-result node t)))))

(defn- named-node-config [name]
  (-> (assoc config [:blaze.db/node (keyword (str "blaze.db." name) "node")]
             (:blaze.db/node config))
      (dissoc :blaze.db/node)))

(defn- duration-seconds-ops
  "Returns the set of ops observed for the node with `node-name`."
  [node-name]
  (into
   #{}
   (comp (mapcat :samples)
         (filter (comp #{"blaze_db_node_duration_seconds_count"} :name))
         (filter (comp #{node-name} first :label-values))
         (map (comp second :label-values)))
   (metrics/collect node/duration-seconds)))

(deftest duration-seconds-node-label-test
  (testing "durations are labeled with the name of the node"
    (with-system [{:blaze.db/keys [node]} config]
      @(-> (node/submit-tx node [[:create {:fhir/type :fhir/Patient :id "0"}]])
           (ac/then-compose (partial tx-result-after-indexing node)))

      (is (= #{"poll-tx-log" "index-transactions" "index-resources"
               "await-resources" "store-tx-entries"
               "store-tx-success-entries"}
             (duration-seconds-ops "main"))))

    (doseq [name ["main" "admin" "name-153446"]]
      (with-system [{node [:blaze.db/node (keyword (str "blaze.db." name) "node")]}
                    (named-node-config name)]
        @(-> (node/submit-tx node [[:create {:fhir/type :fhir/Patient :id "0"}]])
             (ac/then-compose (partial tx-result-after-indexing node)))

        (is (= #{"poll-tx-log" "index-transactions" "index-resources"
                 "await-resources" "store-tx-entries"
                 "store-tx-success-entries"}
               (duration-seconds-ops name)))))))

(deftest transact-test
  (testing "with transaction result fetching after indexing has completed"
    (testing "create"
      (testing "one Patient"
        (with-system [{:blaze.db/keys [node]} config]
          @(-> (node/submit-tx node [[:create {:fhir/type :fhir/Patient :id "0"}]])
               (ac/then-compose (partial tx-result-after-indexing node)))

          (given @(d/pull node (d/resource-handle (d/db node) "Patient" "0"))
            :fhir/type := :fhir/Patient
            :id := "0"
            [:meta :versionId] := #fhir/id "1"
            [meta :blaze.db/op] := :create))))

    (testing "with failing resource storage"
      (testing "on get"
        (with-system [{:blaze.db/keys [node]} resource-store-failing-on-get-config]
          (try
            @(-> (node/submit-tx node [[:put {:fhir/type :fhir/Patient :id "0"}]])
                 (ac/then-compose (partial tx-result-after-indexing node)))
            (catch Exception e
              (given (ex-data (ex-cause e))
                ::anom/category := ::anom/fault))))))

    ;; a failing resource indexer fails the node, so the caller of the
    ;; transaction that hit it learns that the node stopped, not why. The
    ;; transaction is stored durable in the transaction log and will be indexed
    ;; at the next start
    (testing "with failing resource indexer"
      (with-redefs
       [resource-indexer/index-resource
        (fn [_ _ _ _]
          (ac/failed-future (ex-info "" (ba/fault "" ::x ::y))))]

        (testing "fetching the result immediately"
          (with-system [{:blaze.db/keys [node]} resource-store-slow-on-put-config]
            (given-failed-future
             (-> (node/submit-tx node [[:put {:fhir/type :fhir/Patient :id "0"}]])
                 (ac/then-compose (partial node/tx-result node)))
              ::anom/category := ::anom/unavailable
              ::anom/message := "The database node `main` stopped because of an unrecoverable error."
              ::x := nil)))

        (testing "wait before fetching the result"
          (with-system [{:blaze.db/keys [node]} config]
            (given-failed-future
             (-> (node/submit-tx node [[:put {:fhir/type :fhir/Patient :id "0"}]])
                 (ac/then-compose (partial tx-result-after-indexing node)))
              ::anom/category := ::anom/unavailable
              ::anom/message := "The database node `main` stopped because of an unrecoverable error."
              ::x := nil)))))))

(defn- submit-patient [node id]
  (node/submit-tx node [[:put {:fhir/type :fhir/Patient :id id}]]))

(defn- submit-rejections []
  (prom/get node/submit-rejections-total "main"))

(deftest max-in-flight-transactions-test
  (testing "a submit is rejected while the maximum number of in-flight
            transactions is reached"
    (with-system [{:blaze.db/keys [node]
                   kv-store ::blocking-index-kv-store
                   resource-store ::put-counting-resource-store}
                  in-flight-config]
      (let [{:keys [release-put]} (meta kv-store)
            {:keys [put-count]} (meta resource-store)
            rejections (submit-rejections)
            future-1 (submit-patient node "0")
            future-2 (submit-patient node "1")]

        (testing "both transactions are durably in the transaction log but
                  can't be indexed while the index key-value store is blocked;
                  they run concurrently, so it's undefined which of them gets
                  which `t`"
          (is (= #{1 2} #{(deref future-1 10000 ::timeout)
                          (deref future-2 10000 ::timeout)})))

        (let [puts @put-count]
          (testing "a transaction stays in-flight until it is indexed, so a
                    third submit is still rejected"
            (given-failed-future (submit-patient node "2")
              ::anom/category := ::anom/busy
              ::anom/message := "The maximum number of 2 in-flight transactions is reached. Please try again later."))

          (testing "no resource content is stored for the rejected transaction"
            (is (= puts @put-count))))

        (testing "the rejection is counted"
          (is (= (inc rejections) (submit-rejections))))

        (deliver release-put nil)

        (testing "indexing the transactions frees their in-flight places"
          @(d/sync node 2)
          (is (= 3 (deref (submit-patient node "2") 10000 ::timeout)))))))

  (testing "a transaction that never reaches the transaction log frees its
            in-flight place as well"
    (with-system [{:blaze.db/keys [node]} failing-resource-store-on-put-config]
      (dotimes [_ 4]
        (given-failed-future (submit-patient node "0")
          ::anom/category := ::anom/fault
          ::anom/message := "put-error")))))

(deftest sync-test
  (testing "callers waiting for the same t share a single waiter"
    (with-system [{:blaze.db/keys [node]} config]
      (let [futures [(d/sync node 1) (d/sync node 1)]]
        (given @(:state node)
          [:waiters count] := 1
          [:waiters keys] := [1])

        @(node/submit-tx node [[:create {:fhir/type :fhir/Patient :id "0"}]])

        (doseq [future futures]
          (is (= 1 (d/basis-t @future))))

        (testing "the waiter is removed after its t was reached"
          (is (empty? (:waiters @(:state node))))))))

  (testing "cancelling one caller doesn't affect another one waiting for the same t"
    (with-system [{:blaze.db/keys [node]} config]
      (let [cancelled (d/sync node 1)
            future (d/sync node 1)]
        (is (ac/cancel! cancelled))

        @(node/submit-tx node [[:create {:fhir/type :fhir/Patient :id "0"}]])

        (is (= 1 (d/basis-t @future)))))))

(deftest closed-node-test
  ;; a changed resources subscriber can still be busy while the node closes, so
  ;; its transactions and reads have to fail fast instead of waiting for an
  ;; indexing loop that will never run again
  (testing "submitting a transaction fails"
    (let [node (promise)]
      (with-system [{n :blaze.db/node} config]
        (deliver node n))

      (given-failed-future (node/submit-tx @node [[:create {:fhir/type :fhir/Patient :id "0"}]])
        ::anom/category := ::anom/unavailable
        ::anom/message := "The database node `main` is closed.")))

  (testing "transacting fails"
    (let [node (promise)]
      (with-system [{n :blaze.db/node} config]
        (deliver node n))

      (given-failed-future (d/transact @node [[:create {:fhir/type :fhir/Patient :id "0"}]])
        ::anom/category := ::anom/unavailable
        ::anom/message := "The database node `main` is closed.")))

  (testing "waiting for the result of a transaction that will never be indexed fails"
    (let [node (promise)]
      (with-system [{n :blaze.db/node} config]
        (deliver node n))

      (given-failed-future (node/tx-result @node 1)
        ::anom/category := ::anom/unavailable
        ::anom/message := "The database node `main` was closed before the transaction with t = 1 was indexed. But it was stored durable and will be indexed at the next start of the database node.")))

  (testing "syncing on a t that will never be reached fails"
    (let [node (promise)]
      (with-system [{n :blaze.db/node} config]
        (deliver node n))

      (given-failed-future (d/sync @node 1)
        ::anom/category := ::anom/unavailable
        ::anom/message := "The database node `main` was closed before the transaction with t = 1 was indexed. But it was stored durable and will be indexed at the next start of the database node."))))

(deftest failed-node-test
  ;; the node stopped because of an indexing or publishing error, so its
  ;; indexing loop will never run again. Transactions have to fail fast instead
  ;; of being accepted and never indexed. They fail with a general message,
  ;; because the error that stopped the node can come from any depth of the node
  ;; and would be exposed to arbitrary clients otherwise
  (testing "submitting a transaction fails"
    (with-redefs [tx-log/poll! (fn [_ _ _] (throw (Exception. "msg-095102")))]
      (with-system [{:blaze.db/keys [node]} config]
        ;; the indexing loop records the failure before it finishes
        @(:index-finished node)

        (given-failed-future
         (node/submit-tx node [[:create {:fhir/type :fhir/Patient :id "0"}]])
          ::anom/category := ::anom/unavailable
          ::anom/message := "The database node `main` stopped because of an unrecoverable error."))))

  ;; the failure of the node is more specific than the t that will never be
  ;; reached, so it's reported instead of the closed indexing loop
  (testing "waiting for the result of a transaction fails"
    (with-redefs [tx-log/poll! (fn [_ _ _] (throw (Exception. "msg-095102")))]
      (with-system [{:blaze.db/keys [node]} config]
        ;; the indexing loop records the failure before it finishes
        @(:index-finished node)

        (given-failed-future (node/tx-result node 1)
          ::anom/category := ::anom/unavailable
          ::anom/message := "The database node `main` stopped because of an unrecoverable error."))))

  (testing "syncing on a t that will never be reached fails"
    (with-redefs [tx-log/poll! (fn [_ _ _] (throw (Exception. "msg-095102")))]
      (with-system [{:blaze.db/keys [node]} config]
        ;; the indexing loop records the failure before it finishes
        @(:index-finished node)

        (given-failed-future (d/sync node 1)
          ::anom/category := ::anom/unavailable
          ::anom/message := "The database node `main` stopped because of an unrecoverable error.")))))

(deftest publish-loop-wakeup-test
  (testing "the publishing loop doesn't miss the wakeup of the finishing indexing loop"
    (let [done? ac/done?
          ;; completes after the publishing loop determined that the indexing
          ;; loop isn't finished yet
          checked (ac/future)
          ;; releases the publishing loop again
          continue (ac/future)]
      (with-redefs
       [ac/done?
        (fn [future]
          (let [result (done? future)]
            ;; delays the first negative check, which is the one of the
            ;; publishing loop on the finished state of the indexing loop, so
            ;; that the indexing loop can finish inside that window
            (when (and (false? result) (ac/complete! checked true))
              (ac/join continue))
            result))]

        (let [{:blaze.db/keys [node] :as system} (ig/init config)
              state (:state node)]

          ;; the publishing loop waits inside its check by now
          @checked

          (let [publish-future (:publish-future @state)
                ;; halting waits for the publishing loop to finish
                halted (future (ig/halt! system))]

            ;; wait until the indexing loop has finished and replaced the
            ;; publish future the publishing loop would have to wait on
            @(:index-finished node)
            (while (identical? publish-future (:publish-future @state))
              (Thread/onSpinWait))

            (ac/complete! continue true)

            (let [result (deref halted 10000 ::timeout)]
              (when (identical? ::timeout result)
                ;; release the publishing loop, so that the system can be
                ;; halted and no threads are leaked
                (ac/complete! (:publish-future @state) true)
                @halted)

              (is (not (identical? ::timeout result))))))))))

(deftest index-loop-error-test
  (testing "an error in the indexing loop is logged"
    (with-global-log-capture [captured "Error while indexing:"]
      (with-redefs [tx-log/poll! (fn [_ _ _] (throw (Exception. "msg-172037")))]
        (with-system [_ config]
          (given (deref captured 10000 ::timeout)
            :level := :error
            [:vargs 1] := "msg-172037"))))))

(deftest publish-loop-error-test
  (testing "an error in the publishing loop is logged"
    (with-global-log-capture [captured "Error while publishing changed resources:"]
      (with-redefs [sub/window-t (fn [_ _] (throw (Exception. "msg-172311")))]
        (with-system [{:blaze.db/keys [node]} config]
          (d/subscribe-changes! node "Task" "test"
                                (flow/collector (ac/future)))

          (given (deref captured 10000 ::timeout)
            :level := :error
            [:vargs 1] := "msg-172311"))))))

(deftest existing-data-without-version
  (with-system [{:blaze.db/keys [node]} (with-index-store-version config nil)]
    (is node)))

(deftest existing-data-with-compatible-version
  (with-system [{:blaze.db/keys [node]} (with-index-store-version config 0)]
    (is node)))

(def ^:private manual-scheduler-config
  (-> (assoc-in config [:blaze.db/node :scheduler] (ig/ref :blaze.test/manual-scheduler))
      (assoc :blaze.test/manual-scheduler {})))

(deftest patient-last-change-index-state-test
  (testing "the state is set to current on a fresh start of the node"
    (with-system [{:blaze.db/keys [node]
                   :blaze.test/keys [manual-scheduler]} manual-scheduler-config]
      (testing "the index is still building before the submitted task runs"
        (given (plc/state (:kv-store node))
          :type := :building))

      (stu/run-all! manual-scheduler)

      (given (plc/state (:kv-store node))
        :type := :current))))

;; The following tests exercise the pipelining of the resource indexing across
;; transactions. They drive the indexing loop with a transaction log that
;; delivers a fixed batch of transactions and replace
;; `resource-indexer/index-resource` with a stub that records what was
;; dispatched and gates when it completes.

(defmethod ig/init-key ::batch-tx-log [_ {:keys [batch released]}]
  (let [polls (atom 0)]
    (with-meta
      (reify tx-log/TxLog
        (-submit [_ _ _]
          (ac/completed-future (ba/unavailable "The transaction log is read-only.")))
        (-last-t [_]
          (ac/completed-future (:t (peek batch))))
        (-poll [_ offset _]
          ;; the batch becomes available only after the test released it, so
          ;; that the test can prepare the resource store before the indexing
          ;; starts
          (deref released 10000 nil)
          (swap! polls inc)
          (let [batch (filterv (comp #(<= offset %) :t) batch)]
            (when (empty? batch)
              ;; don't spin while there is nothing left to index
              (Thread/sleep 10))
            batch)))
      {:polls polls})))

(defn- batch-tx-log-config
  "Returns a config of a node whose transaction log delivers `batch` as a single
  poll result, once `released` is delivered, and whose resource indexer has
  `num-threads` threads."
  ([batch released]
   (batch-tx-log-config batch released 4))
  ([batch released num-threads]
   (-> (dissoc config ::tx-log/local [::kv/mem :blaze.db/transaction-kv-store])
       (assoc-in [:blaze.db/node :tx-log] (ig/ref ::batch-tx-log))
       (assoc ::batch-tx-log {:batch batch :released released})
       (assoc-in [:blaze.db.node.resource-indexer/executor :num-threads]
                 num-threads))))

(defn- patient [id]
  {:fhir/type :fhir/Patient :id id})

(defn- patients
  "Returns `n` patients with an id prefixed by `prefix`."
  [prefix n]
  (mapv #(patient (str prefix "-" %)) (range n)))

(defn- put-cmd [{:keys [id] :as resource}]
  {:op "put" :type "Patient" :id id :hash (hash/generate resource)})

(defn- payload [resources]
  (into {} (map (juxt hash/generate identity)) resources))

(defn- tx-data
  "Returns transaction data with `t` that puts `resources`, carrying them as
  local payload."
  [t resources]
  {:t t
   :instant (.plusSeconds Instant/EPOCH t)
   :tx-cmds (mapv put-cmd resources)
   :local-payload (payload resources)})

(defn- remote-tx-data
  "Returns transaction data with `t` that puts `resources` without carrying a
  local payload, so that the resources have to be fetched from the resource
  store."
  [t resources]
  (dissoc (tx-data t resources) :local-payload))

(defn- num-threads-config
  "Returns a config whose resource indexer has `num-threads` threads."
  [num-threads]
  (assoc-in config [:blaze.db.node.resource-indexer/executor :num-threads]
            num-threads))

(deftest index-bounds-test
  (testing "the node derives the bounds its indexing loop works with from the
            number of threads of the resource indexer executor"
    (doseq [[num-threads chunk-size look-ahead] [[1 2 8] [2 4 16] [4 8 32]]]
      (with-system [{:blaze.db/keys [node]} (num-threads-config num-threads)]
        (given (:index-bounds node)
          :chunk-size := chunk-size
          :look-ahead := look-ahead)))))

(deftest index-look-ahead-test
  (testing "the resources of a later transaction are dispatched before an
            earlier transaction is applied"
    (let [released (promise)
          dispatched (atom [])
          ;; the resource indexing completes only after two resources were
          ;; dispatched, which are the resources of two different transactions
          gate (ac/future)
          index-resource resource-indexer/index-resource
          batch (mapv #(tx-data (inc %) [(patient (str %))]) (range 4))]
      (with-redefs
       [resource-indexer/index-resource
        (fn [resource-indexer last-updated hash resource]
          (when (<= 2 (count (swap! dispatched conj (:id resource))))
            (ac/complete! gate true))
          (-> gate
              (ac/then-compose
               (fn [_]
                 (index-resource resource-indexer last-updated hash resource)))))]

        (with-system [{:blaze.db/keys [node]} (batch-tx-log-config batch released)]
          (deliver released true)

          (let [db (deref (d/sync node 4) 10000 ::timeout)
                ids @dispatched]
            ;; release the gate in any case, so that the node can be closed
            (ac/complete! gate true)

            (testing "all transactions of the batch are indexed"
              (is (not (identical? ::timeout db))))

            (testing "the resources are dispatched in transaction order"
              (is (= ["0" "1" "2" "3"] ids)))))))))

(defn- gated-index-resource
  "Returns a replacement of `resource-indexer/index-resource` that counts the
  resources dispatched in `dispatched`, completes `full` as soon as `look-ahead`
  of them are dispatched, completes `overshoot` if more are and gates the
  indexing itself on `gate`."
  [look-ahead {:keys [dispatched full overshoot gate]}]
  (let [index-resource resource-indexer/index-resource]
    (fn [resource-indexer last-updated hash resource]
      (let [n (swap! dispatched inc)]
        (cond
          (= look-ahead n) (ac/complete! full true)
          (< look-ahead n) (ac/complete! overshoot true)))
      (-> gate
          (ac/then-compose
           (fn [_]
             (index-resource resource-indexer last-updated hash resource)))))))

(deftest index-look-ahead-bound-test
  (testing "the number of resources dispatched but not yet awaited never
            exceeds the look-ahead, whatever the size of a transaction"
    (let [released (promise)
          gate (ac/future)
          state {:dispatched (atom 0) :full (ac/future) :overshoot (ac/future)
                 :gate gate}
          ;; two transactions with far more resources than the look-ahead of
          ;; 8 * 2 = 16 resources
          batch (mapv #(tx-data (inc %) (patients % 40)) (range 2))]
      (with-redefs [resource-indexer/index-resource (gated-index-resource 16 state)]

        (with-system [{:blaze.db/keys [node]}
                      (batch-tx-log-config batch released 2)]

          (is (= 16 (:look-ahead (:index-bounds node))))

          (deliver released true)

          (testing "the look-ahead is filled"
            (is (true? (deref (:full state) 10000 false))))

          (testing "but never exceeded"
            (is (identical? ::timeout (deref (:overshoot state) 200 ::timeout))))

          (ac/complete! gate true)

          (is (not (identical? ::timeout (deref (d/sync node 2) 10000 ::timeout)))))))))

(defmethod ig/init-key ::multi-get-recording-resource-store
  [_ {:keys [resource-store sizes]}]
  (reify rs/ResourceStore
    (-get [_ key]
      (rs/get resource-store key))
    (-multi-get [_ keys]
      (swap! sizes conj (count keys))
      (rs/multi-get resource-store keys))
    (-put [_ entries]
      (rs/put! resource-store entries))))

(defn- with-multi-get-recording-resource-store
  "Records the number of keys of every `multi-get` the node of `config` issues
  while building the work list of a transaction in `sizes`."
  [config sizes]
  (-> (assoc-in config [:blaze.db/node :resource-store]
                (ig/ref ::multi-get-recording-resource-store))
      (assoc ::multi-get-recording-resource-store
             {:resource-store (ig/ref ::rs/kv) :sizes sizes})))

(deftest index-chunk-test
  (testing "the resources of a single transaction are fetched and indexed in
            chunks"
    (let [released (promise)
          gate (ac/future)
          sizes (atom [])
          state {:dispatched (atom 0) :full (ac/future) :overshoot (ac/future)
                 :gate gate}
          resources (patients 0 40)
          batch [(remote-tx-data 1 resources)]]
      (with-redefs [resource-indexer/index-resource (gated-index-resource 16 state)]

        (with-system [{:blaze.db/keys [node] resource-store ::rs/kv}
                      (-> (batch-tx-log-config batch released 2)
                          (with-multi-get-recording-resource-store sizes))]

          (is (= 4 (:chunk-size (:index-bounds node))))

          @(rs/put! resource-store (payload resources))
          (deliver released true)

          (testing "the look-ahead is filled"
            (is (true? (deref (:full state) 10000 false))))

          (testing "but never exceeded"
            (is (identical? ::timeout (deref (:overshoot state) 200 ::timeout))))

          (ac/complete! gate true)

          (is (not (identical? ::timeout (deref (d/sync node 1) 10000 ::timeout))))

          (testing "no fetch of the resource store exceeds the chunk size"
            (is (= 10 (count @sizes)))
            (is (every? #(= 4 %) @sizes))))))))

(def ^:private slow-executor
  (ac/delayed-executor 20 TimeUnit/MILLISECONDS))

(defn- slow-index-resource
  "Returns a replacement of `resource-indexer/index-resource` that counts the
  resources it started and finished indexing, completes `full` as soon as
  `look-ahead` of them were started and takes 20 ms per resource, so that
  closing the node leaves chunks outstanding."
  [look-ahead {:keys [started finished full]}]
  (let [index-resource resource-indexer/index-resource]
    (fn [resource-indexer last-updated hash resource]
      (when (= look-ahead (swap! started inc))
        (ac/complete! full true))
      (-> (index-resource resource-indexer last-updated hash resource)
          (ac/then-apply-async identity slow-executor)
          (ac/when-complete (fn [_ _] (swap! finished inc)))))))

(deftest index-drain-test
  (testing "no resource indexing task outlives the indexing loop"
    (let [released (promise)
          state {:started (atom 0) :finished (atom 0) :full (ac/future)}
          batch (mapv #(tx-data (inc %) (patients % 40)) (range 2))]
      (with-redefs [resource-indexer/index-resource (slow-index-resource 16 state)]

        (with-system [_ (batch-tx-log-config batch released 2)]
          (deliver released true)

          (testing "the look-ahead is filled"
            (is (true? (deref (:full state) 10000 false)))))

        (testing "all started tasks are finished after the node was closed"
          (is (= @(:started state) @(:finished state))))))))

(defmethod ig/init-key ::shared-index-kv-store [_ kv-store] kv-store)

(defn- new-index-kv-store []
  (let [key [::kv/mem :blaze.db/index-kv-store]]
    (get (ig/init {key (config key)}) key)))

(defn- with-shared-index-kv-store
  "Replaces the index key-value store of `config` with `kv-store`, so that two
  systems can be started against the same index."
  [config kv-store]
  (-> (dissoc config [::kv/mem :blaze.db/index-kv-store])
      (assoc ::shared-index-kv-store kv-store)
      (assoc-in [:blaze.db/node :kv-store] (ig/ref ::shared-index-kv-store))
      (assoc-in [:blaze.db/tx-cache :kv-store] (ig/ref ::shared-index-kv-store))
      (assoc-in [::node/resource-indexer :kv-store] (ig/ref ::shared-index-kv-store))))

(deftest index-stop-mid-batch-test
  (testing "closing the node stops the indexing inside the batch and the
            remaining transactions are indexed at the next start"
    (let [kv-store (new-index-kv-store)
          batch (mapv #(tx-data (inc %) (patients % 40)) (range 2))
          node-config #(-> (batch-tx-log-config batch % 2)
                           (with-shared-index-kv-store kv-store))]

      (let [released (promise)
            state {:started (atom 0) :finished (atom 0) :full (ac/future)}]
        (with-redefs [resource-indexer/index-resource (slow-index-resource 16 state)]
          (with-system [{:blaze.db/keys [node]} (node-config released)]
            (deliver released true)
            (is (true? (deref (:full state) 10000 false)))

            (testing "the batch isn't indexed completely"
              (is (> 2 (:t @(:state node))))))))

      (testing "the remaining transactions are indexed at the next start"
        (let [released (promise)]
          (with-system [{:blaze.db/keys [node]} (node-config released)]
            (deliver released true)

            (is (not (identical? ::timeout (deref (d/sync node 2) 10000 ::timeout))))))))))

(defn- recording-index-resource
  "Returns a replacement of `resource-indexer/index-resource` that records the
  id of every resource it indexes in `ids`."
  [ids]
  (let [index-resource resource-indexer/index-resource]
    (fn [resource-indexer last-updated hash resource]
      (swap! ids conj (:id resource))
      (index-resource resource-indexer last-updated hash resource))))

(deftest index-resource-selection-test
  (let [kept (patient "kept")
        put (patient "put")
        tx-cmds [(put-cmd put)
                 (assoc (put-cmd kept) :op "keep")
                 {:op "delete" :type "Patient" :id "deleted"}]]

    (testing "with a local payload, the payload is indexed"
      (let [released (promise)
            ids (atom [])
            batch [{:t 1 :instant Instant/EPOCH :tx-cmds tx-cmds
                    :local-payload (payload [put])}]]
        (with-redefs [resource-indexer/index-resource (recording-index-resource ids)]
          (with-system [{:blaze.db/keys [node]} (batch-tx-log-config batch released)]
            (deliver released true)

            (is (not (identical? ::timeout (deref (d/sync node 1) 10000 ::timeout))))
            (is (= ["put"] @ids))))))

    (testing "without a local payload, the commands that have a hash and aren't
              keep are fetched from the resource store and indexed"
      (let [released (promise)
            ids (atom [])
            sizes (atom [])
            batch [{:t 1 :instant Instant/EPOCH :tx-cmds tx-cmds}]]
        (with-redefs [resource-indexer/index-resource (recording-index-resource ids)]
          (with-system [{:blaze.db/keys [node] resource-store ::rs/kv}
                        (-> (batch-tx-log-config batch released)
                            (with-multi-get-recording-resource-store sizes))]
            @(rs/put! resource-store (payload [put kept]))
            (deliver released true)

            (is (not (identical? ::timeout (deref (d/sync node 1) 10000 ::timeout))))
            (is (= ["put"] @ids))

            (testing "only the resource of the put command is fetched"
              (is (= [1] @sizes)))))))))

(defmethod ig/init-key ::tx-entries-signalling-kv-store [_ {:keys [kv-store signal]}]
  (reify p/KvStore
    (-new-snapshot [_]
      (p/-new-snapshot kv-store))
    (-get [_ column-family key]
      (p/-get kv-store column-family key))
    (-put [_ entries]
      ;; the transaction index entries of step 2 are the only ones written into
      ;; the ResourceAsOf index
      (when (some (comp #{:resource-as-of-index} first) entries)
        (ac/complete! signal true))
      (p/-put kv-store entries))))

(defn- with-tx-entries-signalling-kv-store
  "Completes `signal` as soon as the node of `config` writes the transaction
  index entries of a transaction."
  [config signal]
  (-> (assoc-in config [:blaze.db/node :kv-store]
                (ig/ref ::tx-entries-signalling-kv-store))
      (assoc ::tx-entries-signalling-kv-store
             {:kv-store (ig/ref :blaze.db/index-kv-store) :signal signal})))

(deftest index-tx-entries-before-resources-test
  (testing "the transaction index entries of a transaction are written while its
            own resources are still being indexed, so that a batch of a single
            transaction overlaps them as well"
    (let [released (promise)
          ;; the resource indexing completes only after the transaction index
          ;; entries of that same transaction were written
          gate (ac/future)
          index-resource resource-indexer/index-resource
          batch [(tx-data 1 (patients 0 2))]]
      (with-redefs
       [resource-indexer/index-resource
        (fn [resource-indexer last-updated hash resource]
          (-> gate
              (ac/then-compose
               (fn [_]
                 (index-resource resource-indexer last-updated hash resource)))))]

        (with-system [{:blaze.db/keys [node]}
                      (-> (batch-tx-log-config batch released)
                          (with-tx-entries-signalling-kv-store gate))]
          (deliver released true)

          (let [db (deref (d/sync node 1) 10000 ::timeout)]
            ;; release the gate in any case, so that the node can be closed
            (ac/complete! gate true)

            (is (not (identical? ::timeout db)))))))))

(deftest index-batch-transactions-collector-init-test
  (with-system [{collector ::node/index-batch-transactions}
                {::node/index-batch-transactions {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(defn- indexed-batches
  "Returns the number of non-empty batches observed for the node `main`.

  The last bucket of a Prometheus histogram is the +Inf bucket that counts all
  observations."
  []
  (peek (:histogram/buckets (prom/get node/index-batch-transactions "main"))))

(defn- index-batch-transactions-sum
  "Returns the sum of the number of transactions of all batches observed for the
  node `main`."
  []
  (:histogram/sum (prom/get node/index-batch-transactions "main")))

(deftest index-batch-transactions-test
  (testing "the number of transactions of each indexed batch is observed"
    (let [released (promise)
          batch (mapv #(tx-data (inc %) [(patient (str %))]) (range 3))]
      (with-system [{:blaze.db/keys [node] tx-log ::batch-tx-log}
                    (batch-tx-log-config batch released)]
        (let [{:keys [polls]} (meta tx-log)
              batches (indexed-batches)
              transactions (index-batch-transactions-sum)]
          (deliver released true)

          (is (not (identical? ::timeout (deref (d/sync node 3) 10000 ::timeout))))

          (testing "one batch with three transactions was indexed"
            (is (= (inc batches) (indexed-batches)))
            (is (= (+ transactions 3.0) (index-batch-transactions-sum))))

          (testing "the empty batches polled afterwards aren't observed"
            (is (true? (wait-for polls #(<= 3 %))))
            (is (= (inc batches) (indexed-batches)))))))))

(defn- duration-observations
  "Returns the number of durations of `op` observed for the node `main`.

  The last bucket of a Prometheus histogram is the +Inf bucket that counts all
  observations."
  [op]
  (peek (:histogram/buckets (prom/get node/duration-seconds "main" op))))

(defn- index-resources-observations
  "Returns the number of `index-resources` durations observed for the node
  `main`.

  Only meaningful while no node is running, because that duration is observed by
  the thread completing the last chunk of a transaction, which can happen after
  the indexing loop committed that transaction and so after `d/sync` returned.
  Halting the system awaits both the indexing loop and the resource indexer
  executor, and those are the only threads that observe it."
  []
  (duration-observations "index-resources"))

(defn- rejected-tx-data
  "Returns transaction data with `t` that puts `resources` and is rejected by the
  tx-indexer, because it also keeps a resource that doesn't exist."
  [t resources]
  (update (tx-data t resources) :tx-cmds conj
          (assoc (put-cmd (patient "missing")) :op "keep")))

(deftest index-rejected-transaction-test
  (testing "a rejected transaction doesn't index the chunks it didn't dispatch
            before its verification failed"
    (let [released (promise)
          ids (atom [])
          ;; far more resources than the look-ahead of 8 * 2 = 16 resources
          batch [(rejected-tx-data 1 (patients 0 40))]]
      (with-redefs [resource-indexer/index-resource (recording-index-resource ids)]
        (let [observations (index-resources-observations)]
          (with-system [{:blaze.db/keys [node]}
                        (batch-tx-log-config batch released 2)]
            (deliver released true)

            (is (not (identical? ::timeout (deref (d/sync node 1) 10000 ::timeout))))

            (testing "the transaction was rejected"
              (given @(:state node)
                :t := 0
                :error-t := 1))

            (testing "only the resources dispatched before the verification
                      failed are indexed, which is one look-ahead worth of them"
              (is (= (:look-ahead (:index-bounds node))
                     (count @ids)))))

          ;; the resources were indexed and did cost time, so dropping the
          ;; chunks left must not drop the observation of the ones dispatched
          (testing "the resource indexing is still observed"
            (is (= (inc observations) (index-resources-observations)))))))))

(deftest await-resources-duration-test
  (testing "the time the indexing loop is blocked awaiting the resources it
            dispatched is observed once per chunk it awaits"
    (let [released (promise)
          ;; a chunk is 2 * 2 = 4 resources, so 10 resources are 3 chunks
          batch [(tx-data 1 (patients 0 10))]]
      (with-system [{:blaze.db/keys [node]}
                    (batch-tx-log-config batch released 2)]
        (let [observations (duration-observations "await-resources")]
          (deliver released true)

          (is (not (identical? ::timeout (deref (d/sync node 1) 10000 ::timeout))))

          (is (= (+ observations 3.0)
                 (duration-observations "await-resources"))))))))

(deftest index-resources-duration-test
  (testing "the resource indexing of a transaction is observed once, whatever
            the number of chunks it is indexed in"
    (let [released (promise)
          ;; a chunk is 2 * 2 = 4 resources, so 40 resources are 10 chunks
          batch [(tx-data 1 (patients 0 40))]
          observations (index-resources-observations)]
      (with-system [{:blaze.db/keys [node]}
                    (batch-tx-log-config batch released 2)]
        (deliver released true)

        (is (not (identical? ::timeout (deref (d/sync node 1) 10000 ::timeout)))))

      (is (= (inc observations) (index-resources-observations))))))

(deftest index-rejected-transaction-without-chunks-left-test
  (testing "a rejected transaction that dispatched all its chunks already
            observes its resource indexing only once"
    (let [released (promise)
          ;; a chunk is 2 * 2 = 4 resources, so 4 resources are a single chunk
          ;; that is dispatched before the verification fails
          batch [(rejected-tx-data 1 (patients 0 4))]
          observations (index-resources-observations)]
      (with-system [{:blaze.db/keys [node]}
                    (batch-tx-log-config batch released 2)]
        (deliver released true)

        (is (not (identical? ::timeout (deref (d/sync node 1) 10000 ::timeout))))

        (testing "the transaction was rejected"
          (given @(:state node)
            :t := 0
            :error-t := 1)))

      (is (= (inc observations) (index-resources-observations))))))
