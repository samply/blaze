(ns blaze.db.api-test-perf
  (:require
    [blaze.db.api :as d]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem]
    [blaze.db.node]
    [blaze.db.resource-handle-cache]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store.kv :as rs-kv]
    [blaze.db.search-param-registry]
    [blaze.db.tx-cache]
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log.local]
    [blaze.fhir.spec.type :as type]
    [blaze.fhir.structure-definition-repo]
    [blaze.log]
    [blaze.test-util :refer [with-system]]
    [clojure.test :refer [deftest]]
    [criterium.core :as criterium]
    [integrant.core :as ig]
    [java-time :as time]
    [taoensso.timbre :as log]))


(log/set-level! :info)


(def system
  {:blaze.db/node
   {:tx-log (ig/ref :blaze.db/tx-log)
    :resource-handle-cache (ig/ref :blaze.db/resource-handle-cache)
    :tx-cache (ig/ref :blaze.db/tx-cache)
    :indexer-executor (ig/ref :blaze.db.node/indexer-executor)
    :resource-store (ig/ref :blaze.db/resource-store)
    :kv-store (ig/ref :blaze.db/index-kv-store)
    :resource-indexer (ig/ref :blaze.db.node/resource-indexer)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :poll-timeout (time/millis 10)}

   ::tx-log/local
   {:kv-store (ig/ref :blaze.db/transaction-kv-store)
    :clock (ig/ref :blaze.test/clock)}
   [::kv/mem :blaze.db/transaction-kv-store]
   {:column-families {}}
   :blaze.test/clock {}

   :blaze.db/resource-handle-cache {:max-size 1000000}

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

   :blaze.db.node/resource-indexer
   {:kv-store (ig/ref :blaze.db/index-kv-store)
    :resource-store (ig/ref ::rs/kv)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :executor (ig/ref :blaze.db.node.resource-indexer/executor)}

   :blaze.db.node.resource-indexer/executor {}

   :blaze.db/search-param-registry
   {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}

   :blaze.fhir/structure-definition-repo {}})


(defmacro with-system-data [[binding-form system] txs & body]
  `(with-system [system# ~system]
     (run! #(deref (d/transact (:blaze.db/node system#) %)) ~txs)
     (let [~binding-form system#] ~@body)))


(deftest transact-test
  (with-system [{:blaze.db/keys [node]} system]
    ;;  58.8 µs / 1.76 µs - Macbook Pro M1 Pro, Oracle OpenJDK 17.0.2
    (criterium/bench
      @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]]))))


(defn- observation-tx-data
  ([version]
   (into [] (map (partial observation-tx-data version)) (range 10)))
  ([version id]
   [:put {:fhir/type :fhir/Observation :id (str id)
          :subject #fhir/Reference{:reference "Patient/0"}
          :method (type/map->CodeableConcept {:text (str version)})
          :code
          #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                   {:system #fhir/uri"system-191514"
                    :code #fhir/code"code-191518"}]}}]))



(deftest type-test
  (with-system-data [{:blaze.db/keys [node]} system]
    (into [[[:put {:fhir/type :fhir/Patient :id "0"}]]]
          (map observation-tx-data)
          (range 2))

    (let [query (d/compile-compartment-query
                  node "Patient" "Observation"
                  [["code" "system-191514|code-191518"]])]
      ;; 5.75 µs / 120 ns - Macbook Pro M1 Pro, Oracle OpenJDK 17.0.2
      (with-open [db (d/new-batch-db (d/db node))]
        (criterium/bench
          (count (d/execute-query db query "0")))))))
