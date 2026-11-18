(ns blaze.interaction.transaction.bundle
  "FHIR Bundle specific stuff."
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.interaction.transaction.bundle.links :as links]
   [blaze.interaction.util :as iu]
   [blaze.value-set-expander :as vse]))

(defn- conditional-delete-type-query-params [{{{url :value} :url} :request}]
  (fhir-util/match-type-query-params url))

(defn- conditional-type-clauses
  "Returns a tuple of the resource type and the conditional clauses of `entry`
  or nil if `entry` has no conditional clauses."
  [{::fhir-util/keys [interaction] :as entry}]
  (case interaction
    "create"
    (when-let [clauses (iu/conditional-clauses (-> entry :request :ifNoneExist :value))]
      [(name (-> entry :resource :fhir/type)) clauses])
    "conditional-delete-type"
    (let [[type query-params] (conditional-delete-type-query-params entry)]
      (when-let [clauses (iu/conditional-clauses query-params)]
        [type clauses]))
    nil))

(defn- expand-entry-value-sets [value-set-expander entry]
  (if-let [[type clauses] (conditional-type-clauses entry)]
    (if (seq clauses)
      (do-sync [clauses (vse/expand-condition-clauses value-set-expander type clauses)]
        (assoc entry ::clauses clauses))
      (ac/completed-future entry))
    (ac/completed-future entry)))

(defn expand-value-sets
  "Returns a CompletableFuture that will complete with `entries` in which
  conditional creates and deletes carry their conditional clauses with
  expanded value sets under ::clauses, or will complete exceptionally if a
  value set can't be expanded or a value set of a condition expands to no codes
  (see `vse/expand-condition-clauses`).

  Has to be called before `assoc-tx-ops`, because the database doesn't expand
  value sets."
  [value-set-expander entries]
  (ac/all-values (mapv (partial expand-entry-value-sets value-set-expander) entries)))

(defmulti entry-tx-op
  "Returns `entry` with the transaction operation of its interaction associated
  under :tx-op. Returns `entry` unchanged if its interaction doesn't need a
  transaction operation."
  {:arglists '([db entry])}
  (fn [_ entry] (::fhir-util/interaction entry)))

(defmethod entry-tx-op "create"
  [_ {:keys [resource] ::keys [clauses] :as entry}]
  (assoc entry
         :tx-op
         (cond->
          [:create (iu/strip-meta resource)]
           (seq clauses)
           (conj clauses))))

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
  [_ {::keys [clauses] :as entry}]
  (let [[type] (conditional-delete-type-query-params entry)]
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
