(ns blaze.db.db
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.db.api :as d]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index :as index]
    [blaze.db.search-param-registry :as sr]
    [cognitect.anomalies :as anom])
  (:import
    [java.io Writer]))


(set! *warn-on-reflection* true)


(defn- resolve-search-param [search-param-registry type code]
  (if-let [search-param (sr/get search-param-registry code type)]
    search-param
    {::anom/category ::anom/not-found
     ::anom/message (format "search-param with code `%s` and type `%s` not found" code type)}))


(defn resolve-search-params
  "Resolves the search parameters in `clauses`."
  [search-param-registry type clauses]
  (reduce
    (fn [ret [code value]]
      (let [res (resolve-search-param search-param-registry type code)]
        (if (::anom/category res)
          (reduced res)
          (conj ret [res value]))))
    []
    clauses))


(deftype Db [context search-param-registry basis-t t]
  d/Db
  (as-of [_ t]
    (assert (<= t basis-t))
    (Db. context search-param-registry basis-t t))

  (basis-t [_]
    basis-t)

  (as-of-t [_]
    (when (not= basis-t t) t))

  (-tx [_ t]
    (index/tx (:blaze.db/kv-store context) t))

  (-resource-exists? [this type id]
    (if-let [resource (d/-resource this type id)]
      (not (index/deleted? resource))
      false))

  (-resource [_ type id]
    (index/resource context (codec/tid type) (codec/id-bytes id) t))

  (-list-resources [this type]
    (d/-list-resources this type nil))

  (-list-resources [_ type start-id]
    (index/type-list context (codec/tid type) (some-> start-id codec/id-bytes) t))

  (-list-compartment-resources [this code id type]
    (d/-list-compartment-resources this code id type nil))

  (-list-compartment-resources [_ code id type start-id]
    (let [compartment {:c-hash (codec/c-hash code) :res-id (codec/id-bytes id)}]
      (index/compartment-list context compartment (codec/tid type) (some-> start-id codec/id-bytes) t)))

  (-type-query [_ type clauses]
    (when-ok [clauses (resolve-search-params search-param-registry type clauses)]
      (index/type-query context (codec/tid type) clauses t)))

  (-compartment-query [_ code id type clauses]
    (when-ok [clauses (resolve-search-params search-param-registry type clauses)]
      (let [compartment {:c-hash (codec/c-hash code) :res-id (codec/id-bytes id)}]
        (index/compartment-query context compartment (codec/tid type) clauses t))))

  (type-total [_ type]
    (index/type-total context (codec/tid type) t))

  (-instance-history [_ type id start-t since]
    (let [start-t (if (some-> start-t (<= t)) start-t t)
          since-t (or (some->> since (index/t-by-instant context)) 0)]
      (index/instance-history context (codec/tid type) (codec/id-bytes id) start-t since-t)))

  (total-num-of-instance-changes [_ type id since]
    (let [since-t (or (some->> since (index/t-by-instant context)) 0)]
      (index/num-of-instance-changes context (codec/tid type) (codec/id-bytes id) t since-t)))

  (type-history [_ type start-t start-id since]
    (let [start-t (if (some-> start-t (<= t)) start-t t)
          since-t (or (some->> since (index/t-by-instant context)) 0)]
      (index/type-history context (codec/tid type) start-t (some-> start-id codec/id-bytes) since-t)))

  (-total-num-of-type-changes [_ type since]
    (let [since-t (or (some->> since (index/t-by-instant context)) 0)]
      (index/num-of-type-changes context (codec/tid type) t since-t)))

  (system-history [_ start-t start-type start-id since]
    (assert (or (nil? start-id) start-type) "missing start-type on present start-id")
    (let [start-t (if (some-> start-t (<= t)) start-t t)
          since-t (or (some->> since (index/t-by-instant context)) 0)]
      (index/system-history context start-t (some-> start-type codec/tid) (some-> start-id codec/id-bytes) since-t)))

  (total-num-of-system-changes [_ since]
    (let [since-t (or (some->> since (index/t-by-instant context)) 0)]
      (index/num-of-system-changes context t since-t))))


(defmethod print-method Db [^Db db ^Writer w]
  (.write w (format "Db[t=%d]" (.t db))))


(defn db [kv-store resource-cache search-param-registry t]
  (->Db #:blaze.db{:kv-store kv-store :resource-cache resource-cache}
        search-param-registry t t))


#_(defn load-resource [kv-store ^Resource resource]
  (when-let [resource (load-resource* kv-store (.hash resource))]
    (let [state (.state resource)
          t (.t resource)]
      (-> resource
          (assoc-in [:meta :versionId] (str t))
          (with-meta {:blaze.db/t t
                      :blaze.db/op (codec/state->op state)
                      :blaze.db/num-changes (codec/state->num-changes state)
                      :blaze.db/tx (index/tx kv-store t)})))))


(defn compartment-query-batch [search-param-registry code type clauses]
  (when-ok [clauses (resolve-search-params search-param-registry type clauses)]
    (let [compartment {:c-hash (codec/c-hash code)}]
      (fn compartment-query-batch [^Db db id]
        (index/compartment-query
          (.context db)
          (assoc compartment :res-id (codec/id-bytes id))
          (codec/tid type)
          clauses
          (.t db))))))
