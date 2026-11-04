(ns blaze.interaction.transaction.bundle
  "FHIR Bundle specific stuff."
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.interaction.transaction.bundle.links :as links]
   [blaze.interaction.util :as iu]))

(defmulti entry-tx-op
  "Returns `entry` with the transaction operation of its interaction associated
  under :tx-op. Returns `entry` unchanged if its interaction doesn't need a
  transaction operation."
  {:arglists '([db entry])}
  (fn [_ entry] (::fhir-util/interaction entry)))

(defmethod entry-tx-op "create"
  [_ {:keys [resource] {{if-none-exist :value} :ifNoneExist} :request :as entry}]
  (let [clauses (iu/conditional-clauses if-none-exist)]
    (assoc entry
           :tx-op
           (cond->
            [:create (iu/strip-meta resource)]
             (seq clauses)
             (conj clauses)))))

(defmethod entry-tx-op "update"
  [db
   {{{if-match :value} :ifMatch {if-none-match :value} :ifNoneMatch} :request
    :keys [resource] :as entry}]
  (when-ok [tx-op (iu/update-tx-op db (iu/strip-meta resource) if-match
                                   if-none-match)]
    (assoc entry :tx-op tx-op)))

(defmethod entry-tx-op "delete"
  [_ {::fhir-util/keys [type id] :as entry}]
  (assoc entry :tx-op [:delete type id]))

(defmethod entry-tx-op "delete-history-version"
  [_ {::fhir-util/keys [type id vid] :as entry}]
  (let [t (iu/vid->t vid)]
    (cond-> entry
      t (assoc :tx-op [:delete-history-version type id t]))))

(defmethod entry-tx-op "conditional-delete-type"
  [_ {{{url :value} :url} :request :as entry}]
  (let [[type query-params] (fhir-util/match-type-query-params url)
        clauses (iu/conditional-clauses query-params)]
    (assoc entry :tx-op (cond-> [:conditional-delete type] (seq clauses) (conj clauses)))))

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
