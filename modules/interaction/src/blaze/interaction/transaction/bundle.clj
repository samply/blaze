(ns blaze.interaction.transaction.bundle
  "FHIR Bundle specific stuff."
  (:require
    [blaze.fhir.spec.type :as type]
    [blaze.interaction.transaction.bundle.links :as links]
    [blaze.interaction.transaction.bundle.url :as url]
    [blaze.interaction.util :as iu]))


(defmulti entry-tx-op (fn [{{:keys [method]} :request}] (type/value method)))


(defmethod entry-tx-op "POST"
  [{:keys [resource] {if-none-exist :ifNoneExist} :request}]
  (cond-> [:create resource]
    if-none-exist
    (conj (iu/clauses if-none-exist))))


(defmethod entry-tx-op "PUT"
  [{{if-match :ifMatch} :request :keys [resource]}]
  (let [t (iu/etag->t if-match)]
    (cond-> [:put resource] t (conj t))))


(defmethod entry-tx-op "DELETE"
  [{{:keys [url]} :request}]
  (let [[type id] (url/match-url (type/value url))]
    [:delete type id]))


(defn tx-ops
  "Returns transaction operations of all `entries` of a transaction bundle."
  [entries]
  (mapv entry-tx-op (links/resolve-entry-links entries)))
