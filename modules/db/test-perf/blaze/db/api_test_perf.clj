(ns blaze.db.api-test-perf
  (:require
   [blaze.db.api :as d]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.node :as node]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.kv :as rs-kv]
   [blaze.db.search-param-registry]
   [blaze.db.tx-cache]
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log.local]
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.writing-context]
   [blaze.log]
   [blaze.module.test-util :refer [with-system]]
   [blaze.terminology-service.not-available]
   [clojure.test :refer [deftest]]
   [criterium.core :as criterium]
   [integrant.core :as ig]
   [java-time.api :as time]
   [taoensso.timbre :as log]))

(log/set-min-level! :info)

(def config
  {:blaze.db/node
   {:tx-log (ig/ref ::tx-log/local)
    :tx-cache (ig/ref :blaze.db/tx-cache)
    :indexer-executor (ig/ref ::node/indexer-executor)
    :resource-store (ig/ref ::rs/kv)
    :kv-store (ig/ref :blaze.db/index-kv-store)
    :resource-indexer (ig/ref ::node/resource-indexer)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :poll-timeout (time/millis 10)}

   ::tx-log/local
   {:kv-store (ig/ref :blaze.db/transaction-kv-store)
    :clock (ig/ref :blaze.test/fixed-clock)}
   [::kv/mem :blaze.db/transaction-kv-store]
   {:column-families {}}
   :blaze.test/fixed-clock {}

   :blaze.db/tx-cache
   {:kv-store (ig/ref :blaze.db/index-kv-store)}

   ::node/indexer-executor {}

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
     :patient-last-change-index nil
     :type-stats-index nil
     :system-stats-index nil}}

   ::rs/kv
   {:kv-store (ig/ref :blaze.db/resource-kv-store)
    :parsing-context (ig/ref :blaze.fhir.parsing-context/resource-store)
    :writing-context (ig/ref :blaze.fhir/writing-context)
    :executor (ig/ref ::rs-kv/executor)}

   [::kv/mem :blaze.db/resource-kv-store]
   {:column-families {}}

   ::rs-kv/executor {}

   ::node/resource-indexer
   {:kv-store (ig/ref :blaze.db/index-kv-store)
    :resource-store (ig/ref ::rs/kv)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :executor (ig/ref :blaze.db.node.resource-indexer/executor)}

   :blaze.db.node.resource-indexer/executor {}

   :blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo
    :terminology-service (ig/ref :blaze.terminology-service/not-available)}

   :blaze.terminology-service/not-available {}

   [:blaze.fhir/parsing-context :blaze.fhir.parsing-context/resource-store]
   {:structure-definition-repo structure-definition-repo
    :fail-on-unknown-property false
    :include-summary-only true
    :use-regex false}

   :blaze.fhir/writing-context
   {:structure-definition-repo structure-definition-repo}})

(defmacro with-system-data
  "Runs `body` inside a system that is initialized from `config`, bound to
  `binding-form` and finally halted.

  Additionally the database is initialized with `txs`."
  [[binding-form config] txs & body]
  `(with-system [system# ~config]
     (run! #(deref (d/transact (:blaze.db/node system#) %)) ~txs)
     (let [~binding-form system#] ~@body)))

(deftest transact-test
  (with-system [{:blaze.db/keys [node]} config]
    ;;  58.8 µs / 1.76 µs - Macbook Pro M1 Pro, Oracle OpenJDK 17.0.2
    (criterium/bench
     @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]]))))

(defn- observation-tx-data
  ([version]
   (into [] (map (partial observation-tx-data version)) (range 10)))
  ([version id]
   [:put {:fhir/type :fhir/Observation :id (str id)
          :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
          :method (type/codeable-concept {:text (type/string (str version))})
          :code
          #fhir/CodeableConcept
           {:coding
            [#fhir/Coding
              {:system #fhir/uri-interned "system-191514"
               :code #fhir/code "code-191518"}]}}]))

(deftest type-test
  (with-system-data [{:blaze.db/keys [node]} config]
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
