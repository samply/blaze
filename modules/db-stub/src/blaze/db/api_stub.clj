(ns blaze.db.api-stub
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-spec]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.db.node :as node]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.kv :as rs-kv]
   [blaze.db.search-param-registry]
   [blaze.db.tx-cache]
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log-spec]
   [blaze.db.tx-log.local]
   [blaze.fhir.parsing-context]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.writing-context]
   [blaze.module.test-util :refer [with-system]]
   [integrant.core :as ig]
   [java-time.api :as time]))

(def root-system
  "Root part of the system initialized for performance reasons."
  (ig/init
   {:blaze.fhir/parsing-context
    {:structure-definition-repo structure-definition-repo
     :fail-on-unknown-property false
     :include-summary-only true
     :use-regex false}
    :blaze.fhir/writing-context
    {:structure-definition-repo structure-definition-repo}}))

(def mem-node-config
  {:blaze.db/node
   {:tx-log (ig/ref ::tx-log/local)
    :tx-cache (ig/ref :blaze.db/tx-cache)
    :indexer-executor (ig/ref ::node/indexer-executor)
    :resource-cache (ig/ref :blaze.db/resource-cache)
    :resource-store (ig/ref ::rs/kv)
    :kv-store (ig/ref :blaze.db/index-kv-store)
    :resource-indexer (ig/ref ::node/resource-indexer)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :scheduler (ig/ref :blaze/scheduler)
    :poll-timeout (time/millis 10)}

   :blaze.db/resource-cache
   {:resource-store (ig/ref ::rs/kv)}

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
     :system-stats-index nil
     :cql-bloom-filter nil
     :cql-bloom-filter-by-t nil}}

   ::rs/kv
   {:kv-store (ig/ref :blaze.db/resource-kv-store)
    :parsing-context (:blaze.fhir/parsing-context root-system)
    :writing-context (:blaze.fhir/writing-context root-system)
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
   {:structure-definition-repo structure-definition-repo}

   :blaze/scheduler {}})

(defmacro with-system-data
  "Runs `body` inside a system that is initialized from `config`, bound to
  `binding-form` and finally halted.

  Additionally the database is initialized with `txs`."
  [[binding-form config] txs & body]
  `(with-system [system# ~config]
     (run! #(deref (d/transact (:blaze.db/node system#) %)) ~txs)
     (let [~binding-form system#] ~@body)))

(defn extract-txs-body [more]
  (if (vector? (first more))
    [(first more) (next more)]
    [[] more]))
