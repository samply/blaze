(ns blaze.db.impl.search-param.date
  (:require
    [blaze.anomaly :refer [if-ok when-ok]]
    [blaze.byte-string :as bs]
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
    [blaze.db.impl.index.search-param-value-resource :as sp-vr]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.util :as u]
    [blaze.db.search-param-registry :as sr]
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


(defmulti date-index-entries (fn [_ _ value] (fhir-spec/fhir-type value)))


(defmethod date-index-entries :fhir/date
  [_ entries-fn date]
  (when-let [value (type/value date)]
    (entries-fn (date-lb value) (date-ub value))))


(defmethod date-index-entries :fhir/dateTime
  [_ entries-fn date-time]
  (when-let [value (type/value date-time)]
    (entries-fn (date-lb value) (date-ub value))))


(defmethod date-index-entries :fhir/instant
  [_ entries-fn date-time]
  (when-let [value (type/value date-time)]
    (entries-fn (date-lb value) (date-ub value))))


(defmethod date-index-entries :fhir/Period
  [_ entries-fn {:keys [start end]}]
  (entries-fn
    (if-let [start (type/value start)]
      (date-lb start)
      codec/date-min-bound)
    (if-let [end (type/value end)]
      (date-ub end)
      codec/date-max-bound)))


(defmethod date-index-entries :default
  [url _ value]
  (log/warn (u/format-skip-indexing-msg value url "date")))


(def ^:private ^:const ^long date-key-offset
  (+ codec/c-hash-size codec/tid-size))


(defn- date-key-lb? [[prefix]]
  (codec/date-lb? prefix date-key-offset))


(defn- date-key-ub? [[prefix]]
  (codec/date-ub? prefix date-key-offset))


(defn- eq-key-valid? [{:keys [snapshot]} c-hash tid ub [prefix id hash-prefix]]
  (and (date-key-lb? [prefix])
       (when-let [v (r-sp-v/get-value snapshot tid id hash-prefix c-hash)]
         (bs/<= (codec/date-lb-ub->ub v) ub))))


(defn- start-key [{:keys [snapshot resource-handle]} c-hash tid value start-id]
  (if start-id
    (let [{:keys [hash]} (resource-handle tid start-id)]
      (sp-vr/encode-seek-key
        c-hash
        tid
        (codec/date-lb-ub->lb (r-sp-v/get-value snapshot tid start-id hash c-hash))
        start-id))
    (sp-vr/encode-seek-key c-hash tid (date-lb value))))


(defn- take-while-eq-key-valid [context c-hash tid value]
  (let [upper-bound (date-ub value)]
    (take-while #(eq-key-valid? context c-hash tid upper-bound %))))


(defn- eq-keys!
  "Returns a reducible collection of decoded SearchParamValueResource keys of
  values equal to `value` starting at `start-id` (optional).

  Decoded keys consist of the triple [prefix id hash-prefix]."
  ([{:keys [svri] :as context} c-hash tid value]
   (coll/eduction
     (take-while-eq-key-valid context c-hash tid value)
     (sp-vr/keys! svri (sp-vr/encode-seek-key c-hash tid (date-lb value)))))
  ([{:keys [svri] :as context} c-hash tid value start-id]
   (coll/eduction
     (take-while-eq-key-valid context c-hash tid value)
     (sp-vr/keys! svri (start-key context c-hash tid value start-id)))))


(defn- ge-keys!
  "Returns a reducible collection of decoded SearchParamValueResource keys of
  values greater or equal to `value` starting at `start-id` (optional).

  Decoded keys consist of the triple [prefix id hash-prefix]."
  ([{:keys [svri]} c-hash tid value]
   (coll/eduction
     (take-while date-key-lb?)
     (sp-vr/keys! svri (sp-vr/encode-seek-key c-hash tid (date-lb value)))))
  ([{:keys [svri] :as context} c-hash tid value start-id]
   (coll/eduction
     (take-while date-key-lb?)
     (sp-vr/keys! svri (start-key context c-hash tid value start-id)))))


(defn- le-start-key [_ c-hash tid value]
  (sp-vr/encode-seek-key-for-prev c-hash tid (date-ub value)))


(defn- le-keys!
  "Returns a reducible collection of decoded SearchParamValueResource keys of
  values less or equal to `value` starting at `start-id` (optional).

  Decoded keys consist of the triple [prefix id hash-prefix]."
  [{:keys [svri] :as context} c-hash tid value]
  (coll/eduction
    (take-while date-key-ub?)
    (sp-vr/keys-prev! svri (le-start-key context c-hash tid value))))


(defn- invalid-date-time-value-msg [code value]
  (format "Invalid date-time value `%s` in search parameter `%s`." value code))


(defn- unsupported-prefix-msg [code op]
  (format "Unsupported prefix `%s` in search parameter `%s`." (name op) code))


(defn- resource-keys!
  ([context c-hash tid [op value]]
   (case op
     :eq (eq-keys! context c-hash tid value)
     :ge (ge-keys! context c-hash tid value)
     :le (le-keys! context c-hash tid value)))
  ([context c-hash tid [op value] start-id]
   (case op
     :eq (eq-keys! context c-hash tid value start-id)
     :ge (ge-keys! context c-hash tid value start-id)
     :le (le-keys! context c-hash tid value))))


(defn- matches? [{:keys [snapshot]} c-hash {:keys [tid id hash]} [op value]]
  (when-let [v (r-sp-v/get-value snapshot tid (codec/id-byte-string id)
                                 hash c-hash)]
    (case op
      :eq (and (bs/<= (date-lb value) (codec/date-lb-ub->lb v))
               (bs/<= (codec/date-lb-ub->ub v) (date-ub value)))
      :ge (bs/<= (date-lb value) (codec/date-lb-ub->lb v))
      :le (bs/<= (codec/date-lb-ub->ub v) (date-ub value)))))


(defrecord SearchParamDate [name url type base code c-hash expression]
  p/SearchParam
  (-compile-value [_ _ value]
    (let [[op value] (u/separate-op value)]
      (case op
        (:eq :ge :le)
        (if-ok [date-time-value (system/parse-date-time value)]
          [op date-time-value]
          (assoc date-time-value
            ::anom/message
            (invalid-date-time-value-msg code value)))
        {::anom/category ::anom/unsupported
         ::anom/message (unsupported-prefix-msg code op)})))

  (-resource-handles [_ context tid _ value]
    (coll/eduction
      (comp
        (map (fn [[_ id hash-prefix]] [id hash-prefix]))
        (u/resource-handle-mapper context tid))
      (resource-keys! context c-hash tid value)))

  (-resource-handles [_ context tid _ value start-id]
    (coll/eduction
      (comp
        (map (fn [[_ id hash-prefix]] [id hash-prefix]))
        (u/resource-handle-mapper context tid))
      (resource-keys! context c-hash tid value start-id)))

  (-matches? [_ context resource-handle _ values]
    (some? (some #(matches? context c-hash resource-handle %) values)))

  (-index-entries [_ resolver hash resource _]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (let [{:keys [id]} resource
            type (clojure.core/name (fhir-spec/fhir-type resource))
            tid (codec/tid type)
            id (codec/id-byte-string id)]
        (into
          []
          (mapcat
            (partial
              date-index-entries
              url
              (fn search-param-date-entry [lb ub]
                (log/trace "search-param-value-entry" "date" code type id
                           (bs/hex hash))
                [(sp-vr/index-entry c-hash tid lb id hash)
                 (sp-vr/index-entry c-hash tid ub id hash)
                 (r-sp-v/index-entry-special tid id hash c-hash
                                             (codec/date-lb-ub lb ub))])))
          values)))))


(defmethod sr/search-param "date"
  [_ {:keys [name url type base code expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamDate name url type base code (codec/c-hash code) expression))
    {::anom/category ::anom/unsupported
     ::anom/message (u/missing-expression-msg url)}))
