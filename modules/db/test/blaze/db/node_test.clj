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
   [blaze.db.test-util :refer [config]]
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log-spec]
   [blaze.db.tx-log.local-spec]
   [blaze.db.tx-log.spec]
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
               "store-tx-entries" "store-tx-success-entries"}
             (duration-seconds-ops "main"))))

    (doseq [name ["main" "admin" "name-153446"]]
      (with-system [{node [:blaze.db/node (keyword (str "blaze.db." name) "node")]}
                    (named-node-config name)]
        @(-> (node/submit-tx node [[:create {:fhir/type :fhir/Patient :id "0"}]])
             (ac/then-compose (partial tx-result-after-indexing node)))

        (is (= #{"poll-tx-log" "index-transactions" "index-resources"
                 "store-tx-entries" "store-tx-success-entries"}
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
       [resource-indexer/index-resources
        (fn [_ _]
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
