(ns blaze.db.api-stub
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-spec]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem]
    [blaze.db.kv.mem-spec]
    [blaze.db.node]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store.kv :as rs-kv]
    [blaze.db.search-param-registry]
    [blaze.db.tx-cache]
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log-spec]
    [blaze.db.tx-log.local]
    [blaze.test-util :refer [structure-definition-repo with-system]]
    [integrant.core :as ig]
    [java-time.api :as time]))


(defn create-mem-node-config [node-config]
  {:blaze.db/node
   (merge
     {:tx-log (ig/ref :blaze.db/tx-log)
      :tx-cache (ig/ref :blaze.db/tx-cache)
      :indexer-executor (ig/ref :blaze.db.node/indexer-executor)
      :resource-store (ig/ref ::rs/kv)
      :kv-store (ig/ref :blaze.db/index-kv-store)
      :resource-indexer (ig/ref :blaze.db.node/resource-indexer)
      :search-param-registry (ig/ref :blaze.db/search-param-registry)
      :poll-timeout (time/millis 10)}
     node-config)

   ::tx-log/local
   {:kv-store (ig/ref :blaze.db/transaction-kv-store)
    :clock (ig/ref :blaze.test/fixed-clock)}
   [::kv/mem :blaze.db/transaction-kv-store]
   {:column-families {}}
   :blaze.test/fixed-clock {}

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
   {:structure-definition-repo structure-definition-repo}})


(def mem-node-config
  (create-mem-node-config {}))


(defmacro with-system-data
  "Runs `body` inside a system that is initialized from `config`, bound to
  `binding-form` and finally halted.

  Additionally the database is initialized with `txs`."
  [[binding-form config] txs & body]
  `(with-system [system# ~config]
     (run! #(deref (d/transact (:blaze.db/node system#) %)) ~txs)
     (let [~binding-form system#] ~@body)))
