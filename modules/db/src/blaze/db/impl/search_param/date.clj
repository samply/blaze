(ns blaze.db.impl.search-param.date
  (:require
    [blaze.anomaly :as ba :refer [if-ok when-ok]]
    [blaze.byte-string :as bs]
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
    [blaze.db.impl.index.search-param-value-resource :as sp-vr]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.core :as sc]
    [blaze.db.impl.search-param.util :as u]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]
    [blaze.fhir.spec.type.system :as system]
    [cognitect.anomalies :as anom]
    [taoensso.timbre :as log])
  (:import
    [java.time ZoneId]))


(set! *warn-on-reflection* true)


(def ^:private default-zone-id (ZoneId/systemDefault))


(defn- date-lb [date-time]
  (codec/date-lb default-zone-id date-time))


(defn- date-ub [date-time]
  (codec/date-ub default-zone-id date-time))


(defmulti index-entries
  "Returns index entries for `value` from a resource."
  {:arglists '([url value])}
  (fn [_ value] (fhir-spec/fhir-type value)))


(defmethod index-entries :fhir/date
  [_ date]
  (when-let [value (type/value date)]
    [[nil (codec/date-lb-ub (date-lb value) (date-ub value))]]))


(defmethod index-entries :fhir/dateTime
  [_ date-time]
  (when-let [value (type/value date-time)]
    [[nil (codec/date-lb-ub (date-lb value) (date-ub value))]]))


(defmethod index-entries :fhir/instant
  [_ date-time]
  (when-let [value (type/value date-time)]
    [[nil (codec/date-lb-ub (date-lb value) (date-ub value))]]))


(defmethod index-entries :fhir/Period
  [_ {:keys [start end]}]
  [[nil
    (codec/date-lb-ub
      (if-let [start (type/value start)]
        (date-lb start)
        codec/date-min-bound)
      (if-let [end (type/value end)]
        (date-ub end)
        codec/date-max-bound))]])


(defmethod index-entries :default
  [url value]
  (log/warn (u/format-skip-indexing-msg value url "date")))


(defn- resource-value!
  "Returns the value of the resource with `tid` and `id` according to the
  search parameter with `c-hash`.

  Changes the state of `context`. Calling this function requires exclusive
  access to `context`."
  {:arglists '([context c-hash tid id])}
  [{:keys [rsvi resource-handle]} c-hash tid id]
  (r-sp-v/next-value! rsvi (resource-handle tid id) c-hash))


(defn- eq-overlaps?
  "Returns true if the interval `v` overlaps with the interval `q`."
  [v-lb v-ub q-lb q-ub]
  (or (bs/<= q-lb v-lb q-ub)
      (bs/<= q-lb v-ub q-ub)
      (and (bs/< v-lb q-lb) (bs/< q-ub v-ub))))


(defn- eq-filter [q-lb a-lb]
  (filter
    (fn [[value]]
      (let [v-lb (codec/date-lb-ub->lb value)
            v-ub (codec/date-lb-ub->ub value)]
        (eq-overlaps? v-lb v-ub q-lb a-lb)))))


(defn- all-keys! [{:keys [svri] :as context} c-hash tid start-id]
  (sp-vr/all-keys! svri c-hash tid (resource-value! context c-hash tid start-id)
                   start-id))


(defn- all-keys-prev! [{:keys [svri] :as context} c-hash tid start-id]
  (sp-vr/all-keys-prev! svri c-hash tid
                        (resource-value! context c-hash tid start-id) start-id))


(defn- eq-keys!
  "Returns a reducible collection of `[value id hash-prefix]` triples of all
  keys with overlapping date/time intervals with the interval specified by
  `lower-bound` and `upper-bound` starting at `start-id` (optional)."
  ([{:keys [svri]} c-hash tid lower-bound upper-bound]
   (coll/eduction
     (eq-filter lower-bound upper-bound)
     (sp-vr/all-keys! svri c-hash tid)))
  ([context c-hash tid lower-bound upper-bound start-id]
   (coll/eduction
     (eq-filter lower-bound upper-bound)
     (all-keys! context c-hash tid start-id))))


(defn- ge-overlaps? [v-lb v-ub q-lb]
  (or (bs/<= q-lb v-lb) (bs/<= q-lb v-ub)))


(defn- ge-filter [q-lb]
  (filter
    (fn [[value]]
      (let [v-lb (codec/date-lb-ub->lb value)
            v-ub (codec/date-lb-ub->ub value)]
        (ge-overlaps? v-lb v-ub q-lb)))))


(defn- ge-keys!
  "Returns a reducible collection of `[value id hash-prefix]` triples of all
  keys with overlapping date/time intervals with the interval specified by
  `lower-bound` and an infinite upper bound starting at `start-id` (optional)."
  ([{:keys [svri]} c-hash tid lower-bound]
   (coll/eduction
     (ge-filter lower-bound)
     (sp-vr/all-keys! svri c-hash tid)))
  ([context c-hash tid lower-bound start-id]
   (coll/eduction
     (ge-filter lower-bound)
     (all-keys! context c-hash tid start-id))))


(defn- le-overlaps? [v-lb v-ub q-ub]
  (or (bs/<= v-ub q-ub) (bs/<= v-lb q-ub)))


(defn- le-filter [q-ub]
  (filter
    (fn [[value]]
      (let [v-lb (codec/date-lb-ub->lb value)
            v-ub (codec/date-lb-ub->ub value)]
        (le-overlaps? v-lb v-ub q-ub)))))


(defn- le-keys!
  "Returns a reducible collection of `[value id hash-prefix]` triples of all
  keys with overlapping date/time intervals with the interval specified by
  an infinite lower bound and `upper-bound` starting at `start-id` (optional)."
  ([{:keys [svri]} c-hash tid upper-bound]
   (coll/eduction
     (le-filter upper-bound)
     (sp-vr/all-keys! svri c-hash tid)))
  ([context c-hash tid upper-bound start-id]
   (coll/eduction
     (le-filter upper-bound)
     (all-keys! context c-hash tid start-id))))


(defn- invalid-date-time-value-msg [code value]
  (format "Invalid date-time value `%s` in search parameter `%s`." value code))


(defn- resource-keys!
  ([context c-hash tid {:keys [op lower-bound upper-bound]}]
   (case op
     :eq (eq-keys! context c-hash tid lower-bound upper-bound)
     (:ge :gt) (ge-keys! context c-hash tid lower-bound)
     (:le :lt) (le-keys! context c-hash tid upper-bound)))
  ([context c-hash tid {:keys [op lower-bound upper-bound]} start-id]
   (case op
     :eq (eq-keys! context c-hash tid lower-bound upper-bound start-id)
     (:ge :gt) (ge-keys! context c-hash tid lower-bound start-id)
     (:le :lt) (le-keys! context c-hash tid upper-bound start-id))))


(defn- matches?
  [{:keys [rsvi]} c-hash resource-handle
   {:keys [op] q-lb :lower-bound q-ub :upper-bound}]
  (when-let [v (r-sp-v/next-value! rsvi resource-handle c-hash)]
    (let [v-lb (codec/date-lb-ub->lb v)
          v-ub (codec/date-lb-ub->ub v)]
      (case op
        :eq (eq-overlaps? v-lb v-ub q-lb q-ub)
        (:ge :gt) (ge-overlaps? v-lb v-ub q-lb)
        (:le :lt) (le-overlaps? v-lb v-ub q-ub)))))


(defrecord SearchParamDate [name url type base code c-hash expression]
  p/SearchParam
  (-compile-value [_ _ value]
    (let [[op value] (u/separate-op value)]
      (if-ok [date-time-value (system/parse-date-time value)]
        (case op
          :eq
          {:op op
           :lower-bound (date-lb date-time-value)
           :upper-bound (date-ub date-time-value)}
          (:ge :gt)
          {:op op
           :lower-bound (date-lb date-time-value)}
          (:le :lt)
          {:op op
           :upper-bound (date-ub date-time-value)}
          (ba/unsupported (u/unsupported-prefix-msg code op)))
        #(assoc % ::anom/message (invalid-date-time-value-msg code value)))))

  (-resource-handles [_ context tid _ value]
    (coll/eduction
      (comp
        (map (fn [[_value id hash-prefix]] [id hash-prefix]))
        (u/resource-handle-mapper context tid))
      (resource-keys! context c-hash tid value)))

  (-resource-handles [_ context tid _ value start-id]
    (coll/eduction
      (comp
        (map (fn [[_value id hash-prefix]] [id hash-prefix]))
        (u/resource-handle-mapper context tid))
      (resource-keys! context c-hash tid value start-id)))

  (-sorted-resource-handles [_ context tid direction]
    (coll/eduction
      (comp
        (map (fn [[_value id hash-prefix]] [id hash-prefix]))
        (u/resource-handle-mapper context tid))
      (if (= :asc direction)
        (sp-vr/all-keys! (:svri context) c-hash tid)
        (sp-vr/all-keys-prev! (:svri context) c-hash tid))))

  (-sorted-resource-handles [_ context tid direction start-id]
    (coll/eduction
      (comp
        (map (fn [[_value id hash-prefix]] [id hash-prefix]))
        (u/resource-handle-mapper context tid))
      (if (= :asc direction)
        (all-keys! context c-hash tid start-id)
        (all-keys-prev! context c-hash tid start-id))))

  (-matches? [_ context resource-handle _ values]
    (some? (some #(matches? context c-hash resource-handle %) values)))

  (-index-values [search-param resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction (p/-index-value-compiler search-param) values)))

  (-index-value-compiler [_]
    (mapcat (partial index-entries url))))


(defmethod sc/search-param "date"
  [_ {:keys [name url type base code expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamDate name url type base code (codec/c-hash code) expression))
    (ba/unsupported (u/missing-expression-msg url))))
