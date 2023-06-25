(ns blaze.interaction.transaction.bundle
  "FHIR Bundle specific stuff."
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.fhir.spec.type :as type]
    [blaze.interaction.transaction.bundle.links :as links]
    [blaze.interaction.transaction.bundle.url :as url]
    [blaze.interaction.util :as iu]
    [clojure.string :as str]
    [ring.util.codec :as ring-codec]))


(defmulti entry-tx-op (fn [_ {{:keys [method]} :request}] (type/value method)))


(defn- conditional-clauses [if-none-exist]
  (when-not (str/blank? if-none-exist)
    (-> if-none-exist ring-codec/form-decode iu/search-clauses)))


(defmethod entry-tx-op "POST"
  [_ {:keys [resource] {if-none-exist :ifNoneExist} :request :as entry}]
  (let [clauses (conditional-clauses if-none-exist)]
    (assoc entry
      :tx-op
      (cond->
        [:create (iu/strip-meta resource)]
        (seq clauses)
        (conj clauses)))))


(defmethod entry-tx-op "PUT"
  [db {{if-match :ifMatch if-none-match :ifNoneMatch} :request :keys [resource]
       :as entry}]
  (when-ok [tx-op (iu/update-tx-op db (iu/strip-meta resource) if-match
                                   if-none-match)]
    (assoc entry :tx-op tx-op)))


(defmethod entry-tx-op "DELETE"
  [_ {{:keys [url]} :request :as entry}]
  (let [[type id] (url/match-url (type/value url))]
    (assoc entry :tx-op [:delete type id])))


(defmethod entry-tx-op :default
  [_ entry]
  entry)


(defn assoc-tx-ops
  "Returns `entries` with transaction operation associated under :tx-op. Or an
  anomaly in case of errors."
  [db entries]
  (transduce
    (comp (map (partial entry-tx-op db)) (halt-when ba/anomaly?))
    conj
    (links/resolve-entry-links entries)))


(defn tx-ops
  "Returns a coll of all transaction operators from `entries`."
  [entries]
  (into [] (keep :tx-op) entries))
