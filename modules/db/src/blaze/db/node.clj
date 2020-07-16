(ns blaze.db.node
  "Local Database Node"
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.db.api :as d]
    [blaze.db.impl.batch-db :as batch-db]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.db :as db]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.indexer :as indexer]
    [blaze.db.search-param-registry :as sr]
    [blaze.db.search-param-registry.spec]
    [blaze.db.tx-log :as tx-log]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [manifold.deferred :as md]
    [taoensso.timbre :as log]))


(defn- resolve-search-param [search-param-registry type code]
  (if-let [search-param (sr/get search-param-registry code type)]
    search-param
    {::anom/category ::anom/not-found
     ::anom/message (format "search-param with code `%s` and type `%s` not found" code type)}))


(defn- resolve-search-params [search-param-registry type clauses]
  (reduce
    (fn [ret [code & values]]
      (let [[code modifier] (str/split code #":" 2)
            res (resolve-search-param search-param-registry type code)]
        (if (::anom/category res)
          (reduced res)
          (conj ret [res modifier (search-param/compile-values res values)]))))
    []
    clauses))


(defrecord Node [tx-log tx-indexer kv-store resource-cache search-param-registry]
  p/Node
  (-db [this]
    (db/db this (indexer/last-t tx-indexer)))

  (-sync [this t]
    (-> (indexer/tx-result tx-indexer t)
        (md/chain' (fn [_] (d/db this)))))

  (-submit-tx [this tx-ops]
    (-> (tx-log/submit tx-log tx-ops)
        (md/chain' #(indexer/tx-result tx-indexer %))
        (md/chain' #(db/db this %))))

  p/ResourceContentLookup
  (-get-content [_ hash]
    (p/-get-content resource-cache hash))

  p/QueryCompiler
  (-compile-type-query [_ type clauses]
    (when-ok [clauses (resolve-search-params search-param-registry type clauses)]
      (batch-db/->TypeQuery (codec/tid type) (seq clauses))))

  (-compile-system-query [_ clauses]
    (when-ok [clauses (resolve-search-params search-param-registry "Resource" clauses)]
      (batch-db/->SystemQuery (seq clauses))))

  (-compile-compartment-query [_ code type clauses]
    (when-ok [clauses (resolve-search-params search-param-registry type clauses)]
      (batch-db/->CompartmentQuery (codec/c-hash code) (codec/tid type) (seq clauses)))))


(defn new-node
  "Creates a new local database node."
  [tx-log tx-indexer kv-store resource-cache search-param-registry]
  (->Node tx-log tx-indexer kv-store resource-cache search-param-registry))


(defmethod ig/pre-init-spec :blaze.db/node [_]
  (s/keys
    :req-un
    [:blaze.db/tx-log
     ::indexer/tx-indexer
     :blaze.db/kv-store
     :blaze.db/resource-cache
     :blaze.db/search-param-registry]))


(defmethod ig/init-key :blaze.db/node
  [_ {:keys [tx-log tx-indexer kv-store resource-cache search-param-registry]}]
  (log/info "Open local database node")
  (new-node tx-log tx-indexer kv-store resource-cache search-param-registry))
