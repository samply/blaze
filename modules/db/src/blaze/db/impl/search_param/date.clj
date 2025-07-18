(ns blaze.db.impl.search-param.date
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.codec.date :as codec-date]
   [blaze.db.impl.index.resource-as-of :as rao]
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
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defmulti index-entries
  "Returns index entries for `value` from a resource."
  {:arglists '([url value])}
  (fn [_ value] (fhir-spec/fhir-type value)))

(defmethod index-entries :fhir/date
  [_ date]
  (when-let [value (type/value date)]
    [[nil (codec-date/encode-range value)]]))

(defmethod index-entries :fhir/dateTime
  [_ date-time]
  (when-let [value (type/value date-time)]
    [[nil (codec-date/encode-range value)]]))

(defmethod index-entries :fhir/instant
  [_ date-time]
  (when-let [value (type/value date-time)]
    [[nil (codec-date/encode-range value)]]))

(defmethod index-entries :fhir/Period
  [_ {:keys [start end]}]
  [[nil (codec-date/encode-range (type/value start) (type/value end))]])

(defmethod index-entries :default
  [url value]
  (log/warn (u/format-skip-indexing-msg value url "date")))

(defn- resource-value
  "Returns the value of the resource with `tid` and `id` according to the
  search parameter with `c-hash`."
  {:arglists '([context c-hash tid id])}
  [{:keys [snapshot t]} c-hash tid id]
  (r-sp-v/next-value snapshot (rao/resource-handle snapshot tid id t) c-hash))

(defn- all-keys
  ([{:keys [snapshot]} c-hash tid]
   (sp-vr/all-keys snapshot c-hash tid))
  ([{:keys [snapshot] :as context} c-hash tid start-id]
   (sp-vr/all-keys snapshot c-hash tid (resource-value context c-hash tid start-id)
                   start-id)))

(defn- all-keys-prev
  ([{:keys [snapshot]} c-hash tid]
   (sp-vr/all-keys-prev snapshot c-hash tid))
  ([{:keys [snapshot] :as context} c-hash tid start-id]
   (sp-vr/all-keys-prev snapshot c-hash tid (resource-value context c-hash tid start-id)
                        start-id)))

(def ^:private drop-value
  (map #(nth % 1)))

(defn- equal?
  "Returns true if the parameter value interval `[param-lb param-ub]` fully
  contains the resource value interval `value`."
  [value param-lb param-ub]
  (and (bs/<= param-lb (codec-date/lower-bound-bytes value))
       (bs/<= (codec-date/upper-bound-bytes value) param-ub)))

(defn- eq-filter [param-ub]
  (filter
   (fn [[value]]
     (bs/<= (codec-date/upper-bound-bytes value) param-ub))))

(defn- eq-stop [param-ub]
  (take-while
   (fn [[value]]
     (not (bs/< param-ub (codec-date/lower-bound-bytes value))))))

(defn- eq-keys
  "Returns a reducible collection of `SingleVersionId` instances of all keys were
  the parameter value interval `[param-lb param-ub]` fully contains the
  resource value interval starting at `start-id` (optional)."
  ([{:keys [snapshot]} c-hash tid param-lb param-ub]
   (coll/eduction
    (comp (eq-stop param-ub)
          (eq-filter param-ub)
          drop-value)
    (sp-vr/prefix-keys-value snapshot c-hash tid param-lb)))
  ([context c-hash tid _param-lb param-ub start-id]
   (coll/eduction
    (comp (eq-stop param-ub)
          (eq-filter param-ub)
          drop-value)
    (all-keys context c-hash tid start-id))))

(defn- not-equal? [value param-lb param-ub]
  (not (equal? value param-lb param-ub)))

(defn- ne-filter [param-lb param-ub]
  (filter
   (fn [[value]]
     (not-equal? value param-lb param-ub))))

(defn- ne-keys
  ([context c-hash tid param-lb param-ub]
   (coll/eduction
    (comp (ne-filter param-lb param-ub)
          drop-value)
    (all-keys context c-hash tid)))
  ([context c-hash tid param-lb param-ub start-id]
   (coll/eduction
    (comp (ne-filter param-lb param-ub)
          drop-value)
    (all-keys context c-hash tid start-id))))

(defn- greater-than? [param-ub value]
  (bs/< param-ub (codec-date/upper-bound-bytes value)))

(defn- gt-filter [param-ub]
  (filter
   (fn [[value]]
     (greater-than? param-ub value))))

(defn- gt-keys
  "Returns a reducible collection of `SingleVersionId` instances of all
  keys with overlapping date/time intervals with the interval specified by
  `param-ub` and an infinite upper bound starting at `start-id` (optional)."
  ([context c-hash tid param-ub]
   (coll/eduction
    (comp (gt-filter param-ub)
          drop-value)
    (all-keys context c-hash tid)))
  ([context c-hash tid param-ub start-id]
   (coll/eduction
    (comp (gt-filter param-ub)
          drop-value)
    (all-keys context c-hash tid start-id))))

(defn- less-than? [value param-lb]
  (bs/< (codec-date/lower-bound-bytes value) param-lb))

(defn- lt-keys
  "Returns a reducible collection of `SingleVersionId` instances of all
  keys with overlapping date/time intervals with the interval specified by
  an infinite lower bound and `param-lb` starting at `start-id` (optional)."
  ([{:keys [snapshot]} c-hash tid param-lb]
   (coll/eduction
    drop-value
    (sp-vr/prefix-keys-value-prev snapshot c-hash tid param-lb)))
  ([context c-hash tid _param-lb start-id]
   (coll/eduction
    drop-value
    (all-keys-prev context c-hash tid start-id))))

(defn- greater-equal?
  "The range above the parameter value intersects (i.e. overlaps) with the range
  of the resource value, or the range of the parameter value fully contains the
  range of the resource value."
  [param-lb param-ub value]
  (or (bs/<= param-ub (codec-date/upper-bound-bytes value))
      (bs/<= param-lb (codec-date/lower-bound-bytes value))))

(defn- ge-filter [param-lb param-ub]
  (filter
   (fn [[value]]
     (greater-equal? param-lb param-ub value))))

(defn- ge-keys
  "Returns a reducible collection of `SingleVersionId` instances of all
  keys with overlapping date/time intervals with the interval specified by
  `param-lb` and an infinite upper bound starting at `start-id` (optional)."
  ([context c-hash tid param-lb param-ub]
   (coll/eduction
    (comp (ge-filter param-lb param-ub)
          drop-value)
    (all-keys context c-hash tid)))
  ([context c-hash tid param-lb param-ub start-id]
   (coll/eduction
    (comp (ge-filter param-lb param-ub)
          drop-value)
    (all-keys context c-hash tid start-id))))

(defn- less-equal? [value param-lb param-ub]
  (or (bs/<= (codec-date/lower-bound-bytes value) param-lb)
      (bs/<= (codec-date/upper-bound-bytes value) param-ub)))

(defn- le-filter [param-lb param-ub]
  (filter
   (fn [[value]]
     (less-equal? value param-lb param-ub))))

(defn- le-keys
  "Returns a reducible collection of `SingleVersionId` instances of all
  keys with overlapping date/time intervals with the interval specified by
  an infinite lower bound and `param-ub` starting at `start-id` (optional)."
  ([context c-hash tid param-lb param-ub]
   (coll/eduction
    (comp (le-filter param-lb param-ub)
          drop-value)
    (all-keys context c-hash tid)))
  ([context c-hash tid param-lb param-ub start-id]
   (coll/eduction
    (comp (le-filter param-lb param-ub)
          drop-value)
    (all-keys context c-hash tid start-id))))

(defn- starts-after? [param-ub value]
  (bs/<= param-ub (codec-date/lower-bound-bytes value)))

(defn- sa-keys
  ([{:keys [snapshot]} c-hash tid param-ub]
   (coll/eduction
    drop-value
    (sp-vr/prefix-keys-value snapshot c-hash tid param-ub)))
  ([context c-hash tid _param-ub start-id]
   (coll/eduction
    drop-value
    (all-keys context c-hash tid start-id))))

(defn- ends-before? [value param-lb]
  (bs/<= (codec-date/upper-bound-bytes value) param-lb))

(defn- eb-filter [param-lb]
  (filter
   (fn [[value]]
     (ends-before? value param-lb))))

(defn- eb-keys
  ([context c-hash tid param-lb]
   (coll/eduction
    (comp (eb-filter param-lb)
          drop-value)
    (all-keys context c-hash tid)))
  ([context c-hash tid param-lb start-id]
   (coll/eduction
    (comp (eb-filter param-lb)
          drop-value)
    (all-keys context c-hash tid start-id))))

(defn- approximately?
  "Returns true if the interval `v` overlaps with the interval `q`."
  [value param-lb param-ub]
  (let [v-lb (codec-date/lower-bound-bytes value)
        v-ub (codec-date/upper-bound-bytes value)]
    (or (bs/<= param-lb v-lb param-ub)
        (bs/<= param-lb v-ub param-ub)
        (and (bs/< v-lb param-lb) (bs/< param-ub v-ub)))))

(defn- ap-filter [param-lb a-lb]
  (filter
   (fn [[value]]
     (approximately? value param-lb a-lb))))

(defn- ap-keys
  "Returns a reducible collection of `SingleVersionId` instances of all
  keys with overlapping date/time intervals with the interval specified by
  `param-lb` and `param-ub` starting at `start-id` (optional)."
  ([context c-hash tid param-lb param-ub]
   (coll/eduction
    (comp (ap-filter param-lb param-ub)
          drop-value)
    (all-keys context c-hash tid)))
  ([context c-hash tid param-lb param-ub start-id]
   (coll/eduction
    (comp (ap-filter param-lb param-ub)
          drop-value)
    (all-keys context c-hash tid start-id))))

(defn- resource-keys
  ([context c-hash tid
    {:keys [op] param-lb :lower-bound param-ub :upper-bound}]
   (case op
     :eq (eq-keys context c-hash tid param-lb param-ub)
     :ne (ne-keys context c-hash tid param-lb param-ub)
     :gt (gt-keys context c-hash tid param-ub)
     :lt (lt-keys context c-hash tid param-lb)
     :ge (ge-keys context c-hash tid param-lb param-ub)
     :le (le-keys context c-hash tid param-lb param-ub)
     :sa (sa-keys context c-hash tid param-ub)
     :eb (eb-keys context c-hash tid param-lb)
     :ap (ap-keys context c-hash tid param-lb param-ub)))
  ([context c-hash tid
    {:keys [op] param-lb :lower-bound param-ub :upper-bound}
    start-id]
   (case op
     :eq (eq-keys context c-hash tid param-lb param-ub start-id)
     :ne (ne-keys context c-hash tid param-lb param-ub start-id)
     :gt (gt-keys context c-hash tid param-ub start-id)
     :lt (lt-keys context c-hash tid param-lb start-id)
     :ge (ge-keys context c-hash tid param-lb param-ub start-id)
     :le (le-keys context c-hash tid param-lb param-ub start-id)
     :sa (sa-keys context c-hash tid param-ub start-id)
     :eb (eb-keys context c-hash tid param-lb start-id)
     :ap (ap-keys context c-hash tid param-lb param-ub start-id))))

(defn- matcher [{:keys [snapshot]} c-hash values]
  (r-sp-v/value-filter
   snapshot (r-sp-v/resource-search-param-encoder c-hash)
   (fn [value {:keys [op] param-lb :lower-bound param-ub :upper-bound}]
     (case op
       :eq (equal? value param-lb param-ub)
       :ne (not-equal? value param-lb param-ub)
       :gt (greater-than? param-ub value)
       :lt (less-than? value param-lb)
       :ge (greater-equal? param-lb param-ub value)
       :le (less-equal? value param-lb param-ub)
       :sa (starts-after? param-ub value)
       :eb (ends-before? value param-lb)
       :ap (approximately? value param-lb param-ub)))
   values))

(defn- invalid-date-time-value-msg [code value]
  (format "Invalid date-time value `%s` in search parameter `%s`." value code))

(defn- dual-bound-value [op date-time]
  {:op op
   :lower-bound (codec-date/encode-lower-bound date-time)
   :upper-bound (codec-date/encode-upper-bound date-time)})

(defn ge-value [date-time]
  (dual-bound-value :ge date-time))

(defn le-value [date-time]
  (dual-bound-value :le date-time))

(defrecord SearchParamDate [name url type base code c-hash expression]
  p/SearchParam
  (-compile-value [_ _ value]
    (let [[op value] (u/separate-op value)]
      (if-ok [date-time-value (system/parse-date-time value)]
        (case op
          (:eq :ne :ge :le :ap)
          (dual-bound-value op date-time-value)
          (:gt :sa)
          {:op op
           :upper-bound (codec-date/encode-upper-bound date-time-value)}
          (:lt :eb)
          {:op op
           :lower-bound (codec-date/encode-lower-bound date-time-value)}
          (ba/unsupported (u/unsupported-prefix-msg code op)))
        #(assoc % ::anom/message (invalid-date-time-value-msg code value)))))

  (-resource-handles [_ batch-db tid _ value]
    (coll/eduction
     (u/resource-handle-mapper batch-db tid)
     (resource-keys batch-db c-hash tid value)))

  (-resource-handles [_ batch-db tid _ value start-id]
    (coll/eduction
     (u/resource-handle-mapper batch-db tid)
     (resource-keys batch-db c-hash tid value start-id)))

  (-sorted-resource-handles [_ batch-db tid direction]
    (coll/eduction
     (comp drop-value
           (u/resource-handle-mapper batch-db tid))
     (if (= :asc direction)
       (all-keys batch-db c-hash tid)
       (all-keys-prev batch-db c-hash tid))))

  (-sorted-resource-handles [_ batch-db tid direction start-id]
    (coll/eduction
     (comp drop-value
           (u/resource-handle-mapper batch-db tid))
     (if (= :asc direction)
       (all-keys batch-db c-hash tid start-id)
       (all-keys-prev batch-db c-hash tid start-id))))

  (-chunked-resource-handles [_ batch-db tid _ value]
    (coll/eduction
     (u/resource-handle-chunk-mapper batch-db tid)
     (resource-keys batch-db c-hash tid value)))

  (-matcher [_ batch-db _ values]
    (matcher batch-db c-hash values))

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
