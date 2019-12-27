(ns blaze.datomic.transaction
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.datomic.quantity :as quantity]
    [blaze.datomic.pull :as pull]
    [blaze.datomic.schema :as schema]
    [blaze.datomic.search-parameter :as search-parameter]
    [blaze.datomic.spec]
    [blaze.datomic.value :as value]
    [blaze.datomic.util :as util]
    [blaze.executors :refer [executor?]]
    [blaze.terminology-service :as ts :refer [term-service?]]
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.walk :refer [postwalk]]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [manifold.deferred :as md :refer [deferred?]]
    [prometheus.alpha :as prom :refer [defcounter defhistogram]]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant LocalDate LocalDateTime LocalTime OffsetDateTime Year
               YearMonth]
    [java.time.format DateTimeFormatter]
    [java.util Base64]
    [java.util Date Map$Entry UUID]
    [java.util.concurrent ExecutionException RejectedExecutionException]))


(defn- ident->path [ident]
  (str (namespace ident) "." (name ident)))


(defn- coerce-decimal [{:db/keys [ident]} value]
  (cond
    (decimal? value) value
    (int? value) (BigDecimal/valueOf ^long value)
    :else
    (throw-anom
      ::anom/incorrect
      (str "Invalid decimal value `" value "`.")
      :fhir/issue "value"
      :fhir.issue/expression (ident->path ident)
      :value value)))


(defn- coerce-decimal-for-quantity [element value]
  (if (int? value)
    value
    (coerce-decimal element value)))


(defn- coerce-date-time [^String value]
  (case (.length value)
    4
    (Year/parse value)
    7
    (YearMonth/parse value)
    10
    (LocalDate/parse value)
    (try
      (OffsetDateTime/parse value)
      (catch Exception _
        (LocalDateTime/parse value)))))


(defn- coerce-date [^String value]
  (case (.length value)
    4
    (Year/parse value)
    7
    (YearMonth/parse value)
    10
    (LocalDate/parse value)))


(defn- uuid [{:db/keys [ident]} s]
  (try
    (UUID/fromString s)
    (catch Exception _
      (throw-anom
        ::anom/incorrect
        (str "Invalid UUID `" s "`.")
        :fhir/issue "value"
        :fhir.issue/expression (ident->path ident)))))


(defn- quantity [element {:strs [value comparator unit system code]}]
  (when (some? comparator)
    (throw-anom
      ::anom/unsupported
      "Unsupported comparator on Quantity type."
      :fhir/issue "not-supported"))
  (let [value (coerce-decimal-for-quantity element value)]
    (if (= "http://unitsofmeasure.org" system)
      (cond
        (nil? unit)
        (quantity/ucum-quantity-without-unit value code)
        (= unit code)
        (quantity/ucum-quantity-with-same-unit value code)
        :else
        (quantity/ucum-quantity-with-different-unit value unit code))
      (quantity/custom-quantity value unit system code))))


(defn coerce-value
  [{:element/keys [type-code] :as element} value]
  (case type-code
    ("boolean"
      "integer"
      "string"
      "uri"
      "url"
      "canonical"
      "code"
      "oid"
      "id"
      "markdown"
      "unsignedInt"
      "positiveInt"
      "xhtml"
      "http://hl7.org/fhirpath/System.String") value
    "decimal" (coerce-decimal element value)
    "instant" (Date/from (Instant/from (.parse DateTimeFormatter/ISO_DATE_TIME value)))
    "date" (coerce-date value)
    "dateTime" (coerce-date-time value)
    "time" (LocalTime/parse value)
    "base64Binary" (.decode (Base64/getDecoder) ^String value)
    "uuid" (uuid element value)
    ;; TODO: Remove direct references to special Quantity types
    ("Age" "Count" "Distance" "Duration"
      "MoneyQuantity" "SimpleQuantity" "Quantity") (quantity element value)))


(defn- write
  "Calls write on values stored as byte array."
  [{:db/keys [ident] :as element} valueType value]
  (assert (some? value) (str "Nil value in element `" (ident->path ident) "`."))
  (let [value (coerce-value element value)]
    (if (= :db.type/bytes valueType)
      (value/write value)
      value)))


(defn- check-cardinality [{:db/keys [ident cardinality]} value]
  (if (= :db.cardinality/one cardinality)
    (when (or (vector? value) (set? value))
      (throw-anom
        ::anom/incorrect
        (str "Incorrect sequential value `" (pr-str value)
             "` at element `" (ident->path ident) "`.")
        :fhir/issue "value"
        :fhir.issue/expression (ident->path ident)))
    (when (not (or (vector? value) (set? value)))
      (throw-anom
        ::anom/incorrect
        (str "Incorrect non-sequential value `" (pr-str value)
             "` at element `" (ident->path ident) "`.")
        :fhir/issue "value"
        :fhir.issue/expression (ident->path ident)))))


(defn- check-primitive [{:element/keys [primitive? type-code] :db/keys [ident]} value]
  (when-not (= "code" type-code)
    (if
      (and
        primitive?
        ;; TODO: Remove direct references to special Quantity types
        (not (#{"Age" "Count" "Distance" "Duration"
                "MoneyQuantity" "SimpleQuantity" "Quantity"} type-code)))
      (when (map? value)
        (throw-anom
          ::anom/incorrect
          (str "Incorrect non-primitive value at primitive element `"
               (ident->path ident) "`.")
          :fhir/issue "value"
          :fhir.issue/expression (ident->path ident)))
      (when-not (or (map? value) (s/valid? (s/coll-of map?) value))
        (throw-anom
          ::anom/incorrect
          (str "Incorrect primitive value `" (pr-str value)
               "` at non-primitive element `" (ident->path ident) "`.")
          :fhir/issue "value"
          :fhir.issue/expression (ident->path ident))))))


(s/fdef find-json-value
  :args
  (s/cat
    ::db ::ds/db
    :element :schema/element
    :entity (s/nilable (s/map-of string? any?))))

(defn find-json-value
  "Tries to find a value suitable for `element` in a JSON `entity`.

  Returns a tuple of the value and the element resulted from a possible choice
  typed element.

  Returns the first found value on choice typed elements.

  Returns nil of nothing was found."
  {:arglists '([db element entity])}
  [db {:element/keys [choice-type? type-choices json-key] :as element} entity]
  (if choice-type?
    (transduce
      (map #(util/cached-entity db %))
      (completing
        (fn [_ {:element/keys [json-key] :as element}]
          (let [value (get entity json-key)]
            (when (some? value)
              (reduced
                [value element])))))
      nil
      type-choices)
    (when-some [value (get entity json-key)]
      (check-cardinality element value)
      (check-primitive element value)
      [value element])))



;; ---- Add Element -----------------------------------------------------------

(defn- resolve-reference [{:keys [db tempids]} type id]
  (or (:db/id (util/resource db type id))
      (get-in tempids [type id])))


(def ^:private supported-subject-indices
  #{:Observation/code
    :Condition/code
    :Specimen/type})


(defn- subject-index
  "Returns tx-data."
  {:arglists '([context code-id])}
  [{{resource-type "resourceType" :as resource} :resource
    :keys [code-ident]
    :as context}
   system code]
  (when (supported-subject-indices code-ident)
    (when (= resource-type (namespace code-ident))
      (when-let [subject (get resource "subject")]
        (when-let [sub-ref (get subject "reference")]
          (let [[type sub-id] (str/split sub-ref #"/")]
            (when (= "Patient" type)
              (when-let [sub-eid (resolve-reference context type sub-id)]
                [[:db/add sub-eid
                  (keyword (str "Patient." resource-type "." (name code-ident))
                           (str system "|" code))
                  (resolve-reference context resource-type (get resource "id"))]]))))))))


(defn- add-code
  "Returns tx-data for adding a code. All of `system`, `version` and `code` are
  optional."
  {:arglists '([context element parent-id system version code])}
  [{:keys [db] :as context} {:db/keys [ident] :as element} parent-id system
   version code]
  (let [code-id (str system "|" version "|" code)]
    (if-let [{:db/keys [id]} (d/entity db [:code/id code-id])]
      (into [[:db/add parent-id ident id]] (subject-index context system code))
      (throw-anom
        ::anom/fault
        (str "Can't find code with id `" code-id "`.")
        :context context
        :element element))))


(defn- add-primitive-element
  {:arglists '([context element id value])}
  [{{:strs [system version]} :code :as context}
   {:element/keys [type-code part-of-choice-type? type-attr-ident]
    :db/keys [ident valueType] :as element}
   id value]
  (cond->
    (case type-code
      "code"
      (if (symbol? value)
        (let [{:keys [system version]} (meta value)]
          (add-code context element id system version (str value)))
        (add-code context element id system version value))

      [[:db/add id ident (write element valueType value)]])
    part-of-choice-type?
    (conj [:db/add id type-attr-ident ident])))


(defn- index-ident [ident]
  (keyword (str (namespace ident) ".index") (name ident)))


(defn- find-code-tids [tx-data]
  (into
    #{}
    (comp
      (filter vector?)
      (filter
        (fn [[op _ a]]
          (and (= :db/add op) (= :Coding/code a))))
      (map
        (fn [[_ _ _ v]]
          v)))
    tx-data))


(declare upsert)


(defn- resolve-local-reference [{:keys [contained-resource-eids]} id]
  (get contained-resource-eids id))


(defn- add-contained-resource
  {:arglists '([context element id value])}
  [context
   {:element/keys [part-of-choice-type? type-attr-ident] :db/keys [ident]}
   id value]
  (if-let [resource-id (get value "id")]
    (let [type (get value "resourceType")
          tid (d/tempid (keyword "part" type))
          tx-data (upsert context (keyword type) {:db/id tid} (dissoc value "id"))]
      {:tx-data
       (cond->
         (conj
           tx-data
           [:db/add tid :local-id resource-id]
           [:db/add id ident tid])
         part-of-choice-type?
         (conj [:db/add id type-attr-ident ident]))
       :id resource-id
       :eid tid})
    (throw (ex-info "Invalid contained resource without `id` element."
                    {::anom/category ::anom/incorrect
                     :fhir/issue "value"
                     :contained-resource value}))))


(defn- add-non-primitive-element'
  [context
   {:element/keys [type-code part-of-choice-type? type-attr-ident]
    :db/keys [ident]}
   id value]
  (let [tid (d/tempid (keyword "part" type-code))
        tx-data (upsert context (keyword type-code) {:db/id tid} value)]
    (when-not (empty? tx-data)
      (cond->
        (conj tx-data [:db/add id ident tid])
        part-of-choice-type?
        (conj [:db/add id type-attr-ident ident])))))


(defn- add-direct-reference
  [{:keys [db] :as context} {:db/keys [ident]} id value]
  (let [ident (schema/direct-reference-attr ident)
        {:strs [reference]} value]
    (cond
      reference
      (if (str/starts-with? reference "#")
        (if-let [eid (resolve-local-reference context (subs reference 1))]
          [[:db/add id ident eid]]
          (throw (ex-info (str "Local reference `" reference "` can't be resolved.")
                          {::anom/category ::anom/incorrect
                           :fhir/issue "value"
                           :reference value
                           :fhir.issue/expression (ident->path ident)})))
        (let [[type ref-id] (str/split reference #"/")]
          (if (util/cached-entity db (keyword type))
            (if-let [eid (resolve-reference context type ref-id)]
              [[:db/add id ident eid]]
              (throw-anom
                ::anom/incorrect
                (format "Reference `%s` can't be resolved." reference)
                :fhir/issue "value"
                :reference value
                :fhir.issue/expression (ident->path ident)))
            (throw-anom
              ::anom/incorrect
              (format "Invalid reference `%s`. The type `%s` is unknown."
                      reference type)
              :fhir/issue "value"
              :reference value
              :fhir.issue/expression (ident->path ident))))))))


(defn- add-non-primitive-element
  "Returns tx-data for adding the non-primitive `value` as child of the
  existing entity with `id`."
  {:arglists '([context element id value])}
  [context
   {:element/keys [type-code type part-of-choice-type? type-attr-ident]
    :db/keys [ident]
    :as element}
   id value]
  (case type-code
    "Reference"
    (let [tx-data (add-non-primitive-element' context element id value)]
      (when-not (empty? tx-data)
        (into tx-data (add-direct-reference context element id value))))

    "BackboneElement"
    (let [tid (d/tempid (keyword "part" (str (namespace type) "." (name type))))
          tx-data (upsert context type {:db/id tid} value)]
      (when-not (empty? tx-data)
        (conj tx-data [:db/add id ident tid])))

    "CodeableConcept"
    (let [tid (d/tempid (keyword "part" type-code))
          context (assoc context :code-ident ident)
          tx-data (upsert context (keyword type-code) {:db/id tid} value)
          code-tids (find-code-tids tx-data)]
      (when-not (empty? tx-data)
        (into
          (cond->
            (conj tx-data [:db/add id ident tid])
            part-of-choice-type?
            (conj [:db/add id type-attr-ident ident]))
          (map (fn [tid] [:db/add id (index-ident ident) tid]))
          (if part-of-choice-type? [] code-tids))))

    (add-non-primitive-element' context element id value)))


(defn- add-element
  [context {:element/keys [primitive?] :as element} id value]
  (if primitive?
    (add-primitive-element context element id value)
    (add-non-primitive-element context element id value)))



;; ---- Upsert Element --------------------------------------------------------

(defn- upsert-primitive-card-one-element
  [{:keys [db] :as context} element old-entity new-value]
  (let [old-value (second (pull/pull-element db element old-entity))]
    (when-not (= old-value new-value)
      (add-primitive-element context element (:db/id old-entity) new-value))))


(declare retract-primitive-card-one-element)


(defn- upsert-primitive-card-many-element
  {:arglists '([context element old-entity new-value])}
  [{:keys [db] :as context}
   {:element/keys [type-code] :db/keys [ident valueType] :as element}
   {:db/keys [id] :as old-entity} new-values]
  (let [old-values (set (second (pull/pull-element db element old-entity)))
        new-values (set new-values)
        retract (set/difference old-values new-values)
        add (set/difference new-values old-values)]
    (-> []
        (into
          (map
            (fn [value]
              (if (= "code" type-code)
                (let [code-id (:db/id (some #(when (= (str value) (:code/code %)) %)
                                            (ident old-entity)))]
                  (assert code-id)
                  [:db/retract id ident code-id])
                [:db/retract id ident (write element valueType value)])))
          retract)
        (into
          (mapcat #(add-primitive-element context element id %))
          add))))


(defn- upsert-non-primitive-card-one-element
  [context {:db/keys [ident] :element/keys [type] :as element}
   old-entity new-value]
  (if-let [old-value (ident old-entity)]
    (let [tx-data (upsert context type old-value new-value)]
      (if (= :Reference type)
        (when-not (empty? tx-data)
          (into tx-data (add-direct-reference context element (:db/id old-entity) new-value)))
        tx-data))
    (add-non-primitive-element context element (:db/id old-entity) new-value)))


(defn- vector->set [x]
  (if (and (vector? x) (not (instance? Map$Entry x)))
    (set x)
    x))


(defn- gen-upsert-pairs [context type retract add]
  (for [old-entity retract
        new-entity add]
    {:old-entity old-entity
     :new-entity new-entity
     :retract-count
     (transduce
       (comp (filter (comp #{:db/retract} first)) (map (constantly 1)))
       +
       (upsert context type (:entity (meta old-entity)) new-entity))}))


(defn- gen-contained-resource-upsert-pairs [context retract add]
  (for [old-entity retract
        :let [old-type (keyword (util/entity-type (:entity (meta old-entity))))]
        new-entity add
        :let [new-type (keyword (get new-entity "resourceType"))]
        :when (= old-type new-type)]
    {:old-entity old-entity
     :new-entity new-entity
     :retract-count
     (transduce
       (comp (filter (comp #{:db/retract} first)) (map (constantly 1)))
       +
       (upsert context new-type (:entity (meta old-entity)) new-entity))}))


(defn- remove-duplicates
  [{:keys [seen-old seen-new] :as res} {:keys [old-entity new-entity] :as pair}]
  (if (or (contains? seen-old old-entity) (contains? seen-new new-entity))
    res
    (-> res
        (update :pairs conj pair)
        (update :seen-old conj old-entity)
        (update :seen-new conj new-entity))))


(defn- extract-upsert-pairs [num-reuse upsert-pairs]
  (->> (sort-by :retract-count upsert-pairs)
       (reduce remove-duplicates {:pairs [] :seen-old #{} :seen-new #{}})
       (:pairs)
       (take num-reuse)))


(defn- upsert-non-primitive-card-many-element
  [{:keys [db] :as context}
   {:db/keys [ident] :element/keys [type] :as element}
   {:db/keys [id] :as old-entity} new-values]
  (let [old-entities (postwalk vector->set (second (pull/pull-element db element old-entity)))
        new-entities (postwalk vector->set new-values)
        retract (set/difference old-entities new-entities)
        add (set/difference new-entities old-entities)
        num-reuse (min (count retract) (count add))
        reuse (extract-upsert-pairs num-reuse (gen-upsert-pairs context type retract add))
        retract (set/difference retract (into #{} (map :old-entity) reuse))
        add (set/difference add (into #{} (map :new-entity) reuse))]
    (-> []
        (into
          (mapcat
            (fn [old-entity]
              (assert (:entity (meta old-entity)))
              (conj
                (upsert context type (:entity (meta old-entity)) nil)
                [:db/retract id ident (:db/id (:entity (meta old-entity)))])))
          retract)
        (into
          (mapcat
            (fn [{:keys [old-entity new-entity]}]
              (let [type (if (= :Resource type) (keyword (get new-entity "resourceType")) type)]
                (upsert context type (:entity (meta old-entity)) new-entity))))
          reuse)
        (into
          (mapcat #(add-non-primitive-element context element id %))
          add))))


(defn- upsert-single-typed-element
  {:arglists '([context element old-entity new-value])}
  [context {:element/keys [primitive?] :db/keys [cardinality] :as element}
   old-entity new-value]
  (if primitive?
    (if (= :db.cardinality/one cardinality)
      (upsert-primitive-card-one-element
        context element old-entity new-value)
      (upsert-primitive-card-many-element
        context element old-entity new-value))
    (if (= :db.cardinality/one cardinality)
      (upsert-non-primitive-card-one-element
        context element old-entity new-value)
      (upsert-non-primitive-card-many-element
        context element old-entity new-value))))


(declare retract-single-typed-element)


(defn- upsert-choice-typed-element
  [{:keys [db] :as context} {:db/keys [ident]} old-entity new-value
   new-value-element]
  (if-let [old-value-ident (ident old-entity)]
    (let [old-value-element (util/cached-entity db old-value-ident)]
      (if (= old-value-element new-value-element)
        (upsert-single-typed-element
          context old-value-element old-entity new-value)
        (into
          (if-let [old-value (old-value-ident old-entity)]
            (retract-single-typed-element context old-value-element old-entity old-value)
            [])
          (add-element
            context new-value-element (:db/id old-entity) new-value))))
    (add-element
      context new-value-element (:db/id old-entity) new-value)))


(defn- upsert-element
  [context {:element/keys [choice-type?] :as element} old-entity new-value
   new-value-element]
  (if choice-type?
    (upsert-choice-typed-element
      context element old-entity new-value new-value-element)
    (upsert-single-typed-element
      context element old-entity new-value)))



;; ---- Retract Element -------------------------------------------------------


(defn- retract-primitive-card-one-element
  {:arglists '([element old-entity value])}
  [{:element/keys [type-code] :db/keys [ident]} {:db/keys [id]} value]
  (assert (some? value))
  (case type-code
    "code"
    (do (assert (:db/id value)) [[:db/retract id ident (:db/id value)]])
    [[:db/retract id ident value]]))


(defn- retract-primitive-card-many-element
  [{:db/keys [ident] :element/keys [type-code]} {:db/keys [id]} value]
  (mapv
    (fn [value]
      (assert (some? value))
      [:db/retract id ident (if (= "code" type-code) (:db/id value) value)])
    value))


(defn- retract-non-primitive-element
  [context {:element/keys [type] :db/keys [ident]} {:db/keys [id]} value]
  (assert (:db/id value))
  (conj (upsert context type value nil) [:db/retract id ident (:db/id value)]))


(defn- retract-direct-reference
  [{:db/keys [id]} attr referenced-entity]
  (assert (:db/id referenced-entity))
  [:db/retract id attr (:db/id referenced-entity)])


(defn- retract-card-one-direct-reference [tx-data entity attr]
  (cond-> tx-data
    (attr entity)
    (conj (retract-direct-reference entity attr (attr entity)))))


(defn- retract-non-primitive-card-one-element
  [context {:element/keys [type] :db/keys [ident] :as element} entity value]
  (cond->
    (retract-non-primitive-element context element entity value)

    (= :Reference type)
    (retract-card-one-direct-reference
      entity (schema/direct-reference-attr ident))))


(defn- retract-card-many-direct-reference [tx-data entity attr]
  (into
    tx-data
    (map #(retract-direct-reference entity attr %))
    (attr entity)))


(defn- retract-non-primitive-card-many-element
  [context {:element/keys [type] :db/keys [ident] :as element} entity value]
  (cond->
    (transduce
      (mapcat #(retract-non-primitive-element context element entity %))
      conj
      value)

    (= :Reference type)
    (retract-card-many-direct-reference
      entity (schema/direct-reference-attr ident))))


(defn- retract-single-typed-element
  {:arglists '([context element entity value])}
  [context {:element/keys [primitive?] :db/keys [cardinality] :as element}
   entity value]
  (if primitive?
    (if (= :db.cardinality/one cardinality)
      (retract-primitive-card-one-element
        element entity value)
      (retract-primitive-card-many-element
        element entity value))
    (if (= :db.cardinality/one cardinality)
      (retract-non-primitive-card-one-element
        context element entity value)
      (retract-non-primitive-card-many-element
        context element entity value))))


(defn- retract-choice-typed-element
  [{:keys [db] :as context} {:db/keys [ident]} {:db/keys [id] :as entity}]
  (when-let [type-ident (ident entity)]
    (-> (if-some [value (type-ident entity)]
          (retract-single-typed-element context (util/cached-entity db type-ident) entity value)
          [])
        (conj [:db/retract id ident type-ident]))))


(defn- retract-element
  "Returns tx-data for retracting `element` from `entity` if it holds some
  value."
  {:arglists '([context element entity])}
  [context {:db/keys [ident] :element/keys [choice-type?] :as element} entity]
  (if choice-type?
    (retract-choice-typed-element context element entity)
    (when-some [value (ident entity)]
      (retract-single-typed-element context element entity value))))


(def ^:private contained-resource-element-remover
  (remove
    (fn [{:element/keys [type-code]}]
      (= "Resource" type-code))))


(def ^:private coding-system-version-remover
  (remove
    (fn [{:db/keys [ident]}]
      (#{:Coding/system :Coding/version} ident))))


(s/fdef upsert
  :args (s/cat :context (s/keys :req-un [::ds/db])
               :type-ident (s/and keyword? #(not (#{:BackboneElement :Resource} %)))
               :old-entity some?
               :new-entity (s/nilable (s/map-of string? any?))))

(defn- upsert
  "Takes a `db` and `type-ident`, the old version of an entity as Datomic entity
  and the new version as JSON with string keys and returns Datomic transaction
  data for the update."
  [{:keys [db] :as context} type-ident old-entity new-entity]
  (let [context
        (cond-> context
          (= :Coding type-ident)
          (assoc :code new-entity)
          (= :CodeSystem type-ident)
          (assoc :code
                 (cond-> {"system" (get new-entity "url")}
                   (contains? new-entity "version")
                   (assoc "version" (get new-entity "version"))))
          (= :ConceptMap/group type-ident)
          (assoc :code
                 (cond-> {"system" (get new-entity "source")}
                   (contains? new-entity "sourceVersion")
                   (assoc "version" (get new-entity "sourceVersion")))
                 :target-code
                 (cond-> {"system" (get new-entity "target")}
                   (contains? new-entity "targetVersion")
                   (assoc "version" (get new-entity "targetVersion"))))
          (= :ConceptMap.group.element/target type-ident)
          (assoc :code (:target-code context))
          (= :ValueSet.compose/include type-ident)
          (assoc :code new-entity)
          (= :ValueSet.expansion/contains type-ident)
          (assoc :code new-entity)
          (get new-entity "resourceType")
          (assoc :resource new-entity))]
    (into
      []
      (comp
        (map #(util/cached-entity db %))
        contained-resource-element-remover
        coding-system-version-remover
        (mapcat
          (fn [element]
            (if-let [[new-value value-element] (find-json-value db element new-entity)]
              (upsert-element context element old-entity new-value value-element)
              (retract-element context element old-entity)))))
      (:type/elements (util/cached-entity db type-ident)))))


(defn- upsert-search-parameter
  [{:db/keys [ident] :as search-parameter} old-resource new-value]
  (let [normalized-value (search-parameter/normalize search-parameter new-value)]
    (when (not= normalized-value (ident old-resource))
      [[:db/add (:db/id old-resource) ident normalized-value]])))


(defn- retract-search-parameter [{:db/keys [ident]} old-resource]
  (when-some [value (ident old-resource)]
    [[:db/retract (:db/id old-resource) ident value]]))


(defn- upsert-search-parameters
  {:arglists '([context resource-type old-resource new-resource])}
  [{:keys [db]} resource-type old-resource new-resource]
  (into
    []
    (comp
      (map #(util/cached-entity db %))
      (mapcat
        (fn [{:search-parameter/keys [json-key] :as search-parameter}]
          (if-let [new-value (get new-resource json-key)]
            (upsert-search-parameter search-parameter old-resource new-value)
            (retract-search-parameter search-parameter old-resource)))))
    (:resource/search-parameter (util/cached-entity db (keyword resource-type)))))

(defn- upsert-contained-resource
  [context old-entity new-entity]
  (let [type (keyword (util/entity-type old-entity))]
    (upsert context type old-entity (dissoc new-entity "id"))))


(defn- contained-resource-eids [resources]
  (transduce
    (map (comp :entity meta))
    (completing
      (fn [res {:db/keys [id] :keys [local-id]}]
        (assoc res local-id id)))
    {}
    resources))


(defn- upsert-contained-resources
  [{:keys [db] :as context} type {container-id :db/id :as old-container} new-container]
  (when-let [{:db/keys [ident] :as element} (util/cached-entity db (keyword type "contained"))]
    (if-let [[new-values] (find-json-value db element new-container)]
      (let [old-entities (postwalk vector->set (second (pull/pull-element db element old-container)))
            new-entities (postwalk vector->set new-values)
            retract (set/difference old-entities new-entities)
            add (set/difference new-entities old-entities)
            num-reuse (min (count retract) (count add))
            reuse (extract-upsert-pairs num-reuse (gen-contained-resource-upsert-pairs context retract add))
            retract (set/difference retract (into #{} (map :old-entity) reuse))
            add (set/difference add (into #{} (map :new-entity) reuse))
            res
            (reduce
              (fn [res old-entity]
                (let [old-entity (:entity (meta old-entity))
                      type (util/entity-type old-entity)]
                  (-> res
                      (update
                        :tx-data
                        into
                        (conj
                          (upsert context (keyword type) old-entity nil)
                          [:db/retract container-id ident (:db/id old-entity)]))
                      (update :eids dissoc (:local-id old-entity)))))
              {:tx-data []
               :eids (contained-resource-eids old-entities)}
              retract)
            res
            (reduce
              (fn [res {:keys [old-entity new-entity]}]
                (let [tx-data (upsert-contained-resource
                                context (:entity (meta old-entity)) new-entity)]
                  (update res :tx-data into tx-data)))
              res
              reuse)]
        (reduce
          (fn [res value]
            (if-let [{:keys [tx-data id eid]}
                     (add-contained-resource context element container-id value)]
              (-> res
                  (update :tx-data into tx-data)
                  (assoc-in [:eids id] eid))
              res))
          res
          add))
      {:tx-data
       (into
         []
         (mapcat
           (fn [{resource-id :db/id :as resource}]
             (conj
               (upsert context (keyword (util/entity-type resource)) resource
                       nil)
               [:db/retract resource-id :local-id (:local-id resource)]
               [:db/retract container-id ident resource-id])))
         (ident old-container))})))


(defn- upsert-resource
  [context type old-resource new-resource]
  (let [{:keys [tx-data eids]}
        (upsert-contained-resources context type old-resource new-resource)
        context
        (cond-> context
          eids
          (assoc :contained-resource-eids eids))]
    (-> (or tx-data [])
        (into (upsert context (keyword type) old-resource new-resource))
        (into (upsert-search-parameters context type old-resource new-resource)))))


(defn- prepare-resource
  "Removes versionId and lastUpdated from meta because both will be set at read
  time from :instance/version attribute and its transaction."
  [resource]
  (update resource "meta" dissoc "versionId" "lastUpdated"))


(defn- upsert-decrement
  "Decrements the version in the upper 62 bit, clears the deletion bit (1) and
  carries over the creation-mode bit (0)."
  [version]
  (let [new-version (bit-shift-left (dec (bit-shift-right version 2)) 2)]
    (if (bit-test version 0) (bit-set new-version 0) new-version)))


(defn- version-decrement-upsert [id version]
  [:db.fn/cas id :instance/version version (upsert-decrement version)])


(defn- initial-version
  "The creation mode is encoded in the first bit of the version. Because the
  deletion state is encoded in the second bit, the values -4 and -3 are used.
  Both leave the second bit at zero."
  [tempid creation-mode]
  [:db.fn/cas tempid :instance/version nil
   (case creation-mode
     :server-assigned-id -3
     :client-assigned-id -4)])


(s/def ::tempids
  (s/map-of string? (s/map-of string? ::ds/tempid)))


(s/def ::creation-mode
  #{:server-assigned-id :client-assigned-id})


(s/def ::resource
  (s/map-of string? some?))


(defhistogram resource-upsert-duration-seconds
  "Datomic resource upsert transaction data generating latencies in seconds."
  {:namespace "datomic"
   :subsystem "transaction"}
  (take 13 (iterate #(* 2 %) 0.00001)))


(s/fdef resource-upsert
  :args (s/cat :db ::ds/db :tempids (s/nilable ::tempids)
               :creation-mode ::creation-mode
               :resource ::resource)
  :ret ::ds/tx-data)

(defn resource-upsert
  "Generates transaction data for creating or updating `resource`.

  The resource has to have at least a `resourceType` and an `id`. Additionally
  all single codes have to be annotated using `annotate-codes`.

  Resources have an internal :instance/version attribute that will be
  incremented on each update using CAS for optimistic locking. The function
  `transact-async` will return an error deferred with ::anom/conflict on
  optimistic locking conflicts.

  Resources are maps taken straight from there parsed JSON with string keys.

  Throws exceptions with `ex-data` containing an anomaly on errors or
  unsupported features."
  {:arglists '([db tempids creation-mode resource])}
  [db tempids creation-mode {type "resourceType" id "id" :as resource}]
  (assert type)
  (assert id)
  (with-open [_ (prom/timer resource-upsert-duration-seconds)]
    (let [resource (prepare-resource resource)]
      (if-let [old-resource (util/resource db type id)]

        ;; update
        (let [tx-data (upsert-resource {:db db :tempids tempids} type
                                       old-resource resource)
              {:db/keys [id] :instance/keys [version]} old-resource]
          (when (or (seq tx-data) (util/deleted? old-resource))
            (conj tx-data (version-decrement-upsert id version))))

        ;; create
        (let [tempid (get-in tempids [type id])]
          (assert tempid)
          (conj (upsert-resource {:db db :tempids tempids} type
                                 {:db/id tempid} resource)
                (initial-version tempid creation-mode)))))))


(defn- deletion-decrement
  "Decrements the version in the upper 62 bit, sets the deletion bit (1) and
  carries over the creation-mode bit (0)."
  [version]
  (let [new-version (bit-shift-left (dec (bit-shift-right version 2)) 2)
        new-version (bit-set new-version 1)]
    (if (bit-test version 0) (bit-set new-version 0) new-version)))


(defn- version-decrement-delete [{:db/keys [id] :instance/keys [version]}]
  (assert version)
  [:db.fn/cas id :instance/version version (deletion-decrement version)])


(defn- resource-id-remover [type]
  (let [id-attr (util/resource-id-attr type)]
    (remove
      (fn [{:db/keys [ident]}]
        (= id-attr ident)))))


(s/fdef resource-deletion
  :args (s/cat :db ::ds/db :type string? :id string?)
  :ret ::ds/tx-data)

(defn resource-deletion
  "Takes a `db` and the `type` and `id` of a resource and returns Datomic
  transaction data for the deletion.

  Returns nil if the resource never existed or is currently deleted."
  [db type id]
  (when-let [resource (util/resource db type id)]
    (when-not (util/deleted? resource)
      (-> [(version-decrement-delete resource)]
          (into
            (comp
              (map #(util/cached-entity db %))
              contained-resource-element-remover
              coding-system-version-remover
              (resource-id-remover type)
              (mapcat #(retract-element {:db db} % resource)))
            (:type/elements (util/cached-entity db (keyword type))))
          (into
            (comp
              (map #(util/cached-entity db %))
              (mapcat #(retract-search-parameter % resource)))
            (:resource/search-parameter (util/cached-entity db (keyword type))))))))


(defn- category [e]
  (case (:db/error (ex-data e))
    :db.error/cas-failed ::anom/conflict
    ::anom/fault))


(defhistogram execution-duration-seconds
  "Datomic transaction execution latencies in seconds."
  {:namespace "datomic"
   :subsystem "transaction"}
  (take 14 (iterate #(* 2 %) 0.001)))


(defcounter resources-total
  "Total number of FHIR resources transacted."
  {:namespace "datomic"
   :subsystem "transaction"})


(defcounter datoms-total
  "Total number of Datoms created."
  {:namespace "datomic"
   :subsystem "transaction"})


(s/fdef transact-async
  :args (s/cat :executor executor? :conn ::ds/conn :tx-data ::ds/tx-data)
  :ret deferred?)

(defn transact-async
  "Like `datomic.api/transact-async` but returns a deferred instead of a future
  and an error deferred with an anomaly in case of errors.

  Uses `executor` to carry out the transactions."
  [executor conn tx-data]
  (try
    (-> (md/future-with executor
          (with-open [_ (prom/timer execution-duration-seconds)]
            @(d/transact-async conn tx-data)))
        (md/chain'
          (fn [{:keys [db-before db-after tx-data] :as tx-result}]
            (prom/inc! resources-total (- (util/system-version db-after)
                                          (util/system-version db-before)))
            (prom/inc! datoms-total (count tx-data))
            tx-result))
        (md/catch' ExecutionException #(md/error-deferred (ex-cause %)))
        (md/catch'
          Exception
          (fn [e]
            (md/error-deferred
              (assoc (ex-data e)
                ::anom/category (category e)
                ::anom/message (ex-message e))))))
    (catch RejectedExecutionException _
      (md/error-deferred
        {::anom/category ::anom/busy
         ::anom/message "The database is busy. Please try again later."}))
    (catch Exception e
      (log/error e)
      (md/error-deferred
        {::anom/category ::anom/fault
         ::anom/message (ex-message e)}))))


(s/fdef resource-tempid
  :args (s/cat :db ::ds/db :resource ::resource)
  :ret (s/tuple string? string? keyword?))

(defn resource-tempid
  "Returns a triple of resource type, logical id and tempid when `resource`
  has to be created in `db`."
  {:arglists '([db resource])}
  [db {type "resourceType" id "id"}]
  (when-not (util/resource db type id)
    [type id (d/tempid (keyword "part" type))]))



;; ---- Create Codes ----------------------------------------------------------

(defn- create-subject-index-attr [resource-type data-element-name system code]
  {:db/ident
   (keyword (format "Patient.%s.%s" resource-type data-element-name)
            (str system "|" code))
   :db/valueType :db.type/ref
   :db/cardinality :db.cardinality/many})


(defn- create-subject-index-attrs [system code]
  (mapv
    (fn [subject-index]
      (let [resource-type (namespace subject-index)
            data-element-name (name subject-index)]
        (create-subject-index-attr resource-type data-element-name system code)))
    supported-subject-indices))


(defn- create-code
  "Returns tx-data for creating a code. All of `system`, `version` and `code`
  are optional."
  [{:keys [db]} system version code]
  (let [id (str system "|" version "|" code)]
    (when-not (d/entity db [:code/id id])
      (let [tid (d/tempid :part/code)]
        (conj
          (create-subject-index-attrs system code)
          (cond->
            {:db/id tid
             :code/id id}
            system
            (assoc :code/system system)
            version
            (assoc :code/version version)
            code
            (assoc :code/code code)))))))


(defn- create-code-primitive-element
  {:arglists '([context element value])}
  [{{:strs [system version]} :code :as context}
   {:element/keys [type-code]}
   value]
  (when (= "code" type-code)
    (if (symbol? value)
      (let [{:keys [system version]} (meta value)]
        (create-code context system version (str value)))
      (create-code context system version value))))


(declare create-codes)


(defn- create-codes-non-primitive-element
  {:arglists '([context element value])}
  [context {:element/keys [type]} value]
  (create-codes context type value))


(defn- create-codes-element
  {:arglists '([context element new-value])}
  [context {:element/keys [primitive?] :db/keys [cardinality] :as element}
   new-value]
  (if primitive?
    (if (= :db.cardinality/one cardinality)
      (create-code-primitive-element
        context element new-value)
      (into
        []
        (mapcat #(create-code-primitive-element context element %))
        new-value))
    (if (= :db.cardinality/one cardinality)
      (create-codes-non-primitive-element
        context element new-value)
      (into
        []
        (mapcat #(create-codes-non-primitive-element context element %))
        new-value))))


(defn- create-codes
  "Returns tx-data to create new codes."
  [{:keys [db] :as context} type-ident new-entity]
  (let [context
        (cond-> context
          (= :Coding type-ident)
          (assoc :code new-entity)
          (= :CodeSystem type-ident)
          (assoc :code
                 (cond-> {"system" (get new-entity "url")}
                   (contains? new-entity "version")
                   (assoc "version" (get new-entity "version"))))
          (= :ConceptMap/group type-ident)
          (assoc :code
                 (cond-> {"system" (get new-entity "source")}
                   (contains? new-entity "sourceVersion")
                   (assoc "version" (get new-entity "sourceVersion")))
                 :target-code
                 (cond-> {"system" (get new-entity "target")}
                   (contains? new-entity "targetVersion")
                   (assoc "version" (get new-entity "targetVersion"))))
          (= :ConceptMap.group.element/target type-ident)
          (assoc :code (:target-code context))
          (= :ValueSet.compose/include type-ident)
          (assoc :code new-entity)
          (= :ValueSet.expansion/contains type-ident)
          (assoc :code new-entity))]
    (into
      []
      (comp
        (map #(util/cached-entity db %))
        coding-system-version-remover
        (mapcat
          (fn [element]
            (when-let [[new-value value-element] (find-json-value db element new-entity)]
              (create-codes-element context value-element new-value)))))
      (:type/elements (util/cached-entity db type-ident)))))



(s/fdef resource-codes-creation
  :args (s/cat :db ::ds/db :resource ::resource)
  :ret ::ds/tx-data)

(defn resource-codes-creation
  "Generates transaction data for creating codes which occur in `resource`.

  Codes have to created in a separate transaction before transaction tx-data
  obtained from `resource-upsert`.

  The resource has to have at least a `resourceType` and an `id`. Additionally
  all single codes have to be annotated using `annotate-codes`."
  {:arglists '([db resource])}
  [db {type "resourceType" id "id" :as resource}]
  (assert type)
  (assert id)
  (create-codes {:db db} (keyword type) resource))



;; ---- Annotate Codes ----------------------------------------------------------

(defn- fetch-code-system
  "Returns a map with a least :system and optionally :version if something was found."
  [{:keys [term-service]} value-set-binding code]
  (let [[uri version] (str/split value-set-binding #"\|")]
    (-> (ts/expand-value-set
          term-service
          (cond-> {:url uri} version (assoc :valueSetVersion version)))
        (md/chain'
          (fn [value-set]
            (->> (-> value-set :expansion :contains)
                 (some #(when (= code (:code %)) %))))))))


(defn- annotate-code [system version code]
  (with-meta
    (symbol code)
    (cond->
      {:system system}
      version
      (assoc :version version))))


(defn- determine-static-code-system [value-set-binding]
  (let [[uri] (str/split value-set-binding #"\|")]
    (case uri
      "http://hl7.org/fhir/ValueSet/mimetypes" {:system "urn:ietf:bcp:13"}
      "http://hl7.org/fhir/ValueSet/languages" {:system "urn:ietf:bcp:47"}
      "http://hl7.org/fhir/ValueSet/all-languages" {:system "urn:ietf:bcp:47"}
      nil)))


(defn- annotate-code-according-value-set-binding
  [context value-set-binding code]
  (if-let [{:keys [system version]} (determine-static-code-system value-set-binding)]
    (annotate-code system version code)
    (-> (fetch-code-system context value-set-binding code)
        (md/chain'
          (fn [{:keys [system version]}]
            (if system
              (annotate-code system version code)
              (md/error-deferred
                {::anom/category ::anom/conflict
                 ::anom/message (format "ValueSet expansion of `%s` with code `%s` didn't return any codes." value-set-binding code)}))))
        (md/catch'
          (fn [e]
            (md/error-deferred
              {::anom/category ::anom/fault
               ::anom/message (format "ValueSet expansion of `%s` with code `%s` resulted in the following error: %s" value-set-binding code (or (::anom/message e) (ex-message e)))}))))))


(defn- annotate-code-primitive-element
  {:arglists '([context element value])}
  [context {:element/keys [type-code value-set-binding] :as element} value]
  (if (and (= "code" type-code) value-set-binding)
    (-> (annotate-code-according-value-set-binding context value-set-binding value)
        (md/catch'
          (fn [e]
            (md/error-deferred
              (if (::anom/category e)
                (assoc e :context context :element element)
                e)))))
    value))


(declare annotate-codes*)


(defn- annotate-codes-non-primitive-element
  {:arglists '([context element value])}
  [context {:element/keys [type]} value]
  (annotate-codes* context type value))


(defn- annotate-codes-element
  {:arglists '([context element value])}
  [context {:element/keys [primitive?] :db/keys [cardinality] :as element}
   value]
  (check-cardinality element value)
  (check-primitive element value)
  (if primitive?
    (if (= :db.cardinality/one cardinality)
      (annotate-code-primitive-element context element value)
      (md/loop [[value & values] value
                res []]
        (if value
          (-> (annotate-code-primitive-element context element value)
              (md/chain' #(md/recur values (conj res %))))
          res)))
    (if (= :db.cardinality/one cardinality)
      (annotate-codes-non-primitive-element context element value)
      (md/loop [[value & values] value
                res []]
        (if value
          (-> (annotate-codes-non-primitive-element context element value)
              (md/chain' #(md/recur values (conj res %))))
          res)))))


(defn- type-ident [type k]
  (if-let [ns (namespace type)]
    (keyword (str ns "." (name type)) k)
    (keyword (name type) k)))


(defn- annotate-codes*
  [{:keys [db] :as context} type entity]
  (let [type (if (= :Resource type) (keyword (get entity "resourceType")) type)]
    (md/loop [[[k v] & entity] entity
              res {}]
      (if k
        (if-let [element (util/cached-entity db (type-ident type k))]
          (-> (annotate-codes-element context element v)
              (md/chain' #(md/recur entity (assoc res k %))))
          (md/recur entity (assoc res k v)))
        res))))



(s/fdef annotate-codes
  :args (s/cat :term-service term-service? :db ::ds/db :resource ::resource)
  :ret deferred?)

(defn annotate-codes
  "Annotates values of data type `code` with a value set binding with the
  appropriate system and version.

  Doesn't touch the other values. Returns the deferred, annotated resource.

  The resource has to have at least a `resourceType` and an `id`."
  {:arglists '([term-service db resource])}
  [term-service db {type "resourceType" id "id" :as resource}]
  (assert type)
  (assert id)
  (-> (annotate-codes*
        {:term-service term-service :db db} (keyword type) resource)
      (md/chain' #(assoc % "resourceType" type))))
