(ns blaze.interaction.transaction.bundle
  "FHIR Bundle specific stuff."
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.interaction.transaction.bundle.links :as links]
   [blaze.interaction.util :as iu]
   [blaze.util.clauses :as uc]
   [clojure.string :as str]
   [ring.util.codec :as ring-codec]))

(defmulti entry-tx-op (fn [_ {{:keys [method]} :request}] (:value method)))

(defn- conditional-clauses [if-none-exist]
  (when-not (str/blank? if-none-exist)
    (-> if-none-exist ring-codec/form-decode uc/search-clauses)))

(defmethod entry-tx-op "POST"
  [_ {:keys [resource] {{if-none-exist :value} :ifNoneExist} :request :as entry}]
  (let [clauses (conditional-clauses if-none-exist)]
    (assoc entry
           :tx-op
           (cond->
            [:create (iu/strip-meta resource)]
             (seq clauses)
             (conj clauses)))))

(defmethod entry-tx-op "PUT"
  [db
   {{{if-match :value} :ifMatch {if-none-match :value} :ifNoneMatch} :request
    :keys [resource] :as entry}]
  (when-ok [tx-op (iu/update-tx-op db (iu/strip-meta resource) if-match
                                   if-none-match)]
    (assoc entry :tx-op tx-op)))

(defmethod entry-tx-op "DELETE"
  [_ {{:keys [url]} :request :as entry}]
  (if-let [[type id] (fhir-util/match-type-id (:value url))]
    (assoc entry :tx-op [:delete type id])
    (when-let [[type query-params] (fhir-util/match-type-query-params (:value url))]
      (assoc entry :tx-op (cond-> [:conditional-delete type] (not (str/blank? query-params)) (conj (-> query-params ring-codec/form-decode uc/search-clauses)))))))

(defmethod entry-tx-op :default
  [_ entry]
  entry)

(defn assoc-tx-ops
  "Returns `entries` with possible transaction operation associated under
  :tx-op for each entry. Or an anomaly in case of errors."
  [db entries]
  (transduce
   (comp (map (partial entry-tx-op db)) (halt-when ba/anomaly?))
   conj
   (links/resolve-entry-links entries)))

(defn tx-ops
  "Returns a coll of all transaction operators from `entries`."
  [entries]
  (into [] (keep :tx-op) entries))
