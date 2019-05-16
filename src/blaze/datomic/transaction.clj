(ns blaze.datomic.transaction
  (:require
    [blaze.datomic.quantity :as quantity]
    [blaze.datomic.value :as value]
    [blaze.datomic.util :as util]
    [blaze.spec]
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [manifold.deferred :as md])
  (:import
    [java.time Instant LocalDate LocalDateTime LocalTime OffsetDateTime Year
               YearMonth]
    [java.time.format DateTimeFormatter]
    [java.util Date]
    [java.util.concurrent ExecutionException]))


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


(defn- quantity [{:strs [value unit] :as quantity}]
  (-> (cond
        (decimal? value) value
        (int? value) (BigDecimal/valueOf ^long value)
        :else (throw (ex-info "Invalid quantity." quantity)))
      (quantity/quantity unit)))


(s/fdef coerce-value
  :args (s/cat :type-code string? :value some?))

(defn coerce-value
  [type-code value]
  (case type-code
    ("string" "code" "id" "markdown" "uri" "xhtml" "boolean") value
    "instant" (Date/from (Instant/from (.parse DateTimeFormatter/ISO_DATE_TIME value)))
    "decimal" (double value)
    "dateTime" (coerce-date-time value)
    "date" (coerce-date value)
    "time" (LocalTime/parse value)
    "Quantity" (quantity value)
    nil))


(defn- to-decimal [value]
  (if (int? value)
    (BigDecimal/valueOf ^long value)
    value))


(defn- coerce-value*
  [{:db/keys [cardinality] :element/keys [type-code]} value]
  (if (= :db.cardinality/one cardinality)
    (coerce-value type-code value)
    (into #{} (map #(coerce-value type-code %)) value)))


(defn- write
  "Calls write on values stored as byte array."
  [valueType value]
  (if (= :db.type/bytes valueType) (value/write value) value))


(defn- type-choice [{:element/keys [type-choices]} type-code]
  (some #(when (= type-code (:element/type-code %)) %) type-choices))


(defn- primitive-card-one-value-from-db
  {:arglists '([element entity])}
  [{:element/keys [type-code] :db/keys [ident]} entity]
  (case ident
    :Coding/system
    (-> entity :Coding/code :code/system)
    :Coding/version
    (-> entity :Coding/code :code/version)
    (when-some [value (ident entity)]
      (cond
        (= "code" type-code)
        (:code/code value)
        :else
        (value/read value)))))


(defn- non-primitive-value-from-db
  "Returns a map like if would be formatted by `find-json-value` of `entity`.

  The map preserves the original entity in its meta-data under :entity."
  [db type-ident entity]
  (into
    (with-meta {} {:entity entity})
    (comp
      (map #(util/cached-entity db %))
      (map
        (fn [{:element/keys [json-key] :as element}]
          (when-some [value (primitive-card-one-value-from-db element entity)]
            [json-key value]))))
    (:type/elements (util/cached-entity db type-ident))))


(defn- non-primitive-card-many-values-from-db
  "Returns the set of non-primitive values from the cardinality-many `element`
  of `entity` from `db`."
  {:arglists '([db element entity])}
  [db {:db/keys [ident] :element/keys [type]} entity]
  (case ident
    (when-let [values (ident entity)]
      (into
        #{}
        (map #(non-primitive-value-from-db db type %))
        values))))


(defn find-json-value
  "Tries to find a value suitable for `element` in a JSON `entity`.

  Returns a tuple of the value and the element resulted from a possible choice
  typed element.

  Returns the first found value on choice typed elements.

  Returns nil of nothing was found."
  [db {:element/keys [choice-type? primitive? type-choices json-key] :as element}
   entity]
  (if choice-type?
    (transduce
      (map #(util/cached-entity db %))
      (completing
        (fn [_ {:element/keys [json-key primitive?] :as element}]
          (let [value (get entity json-key)]
            (when (some? value)
              (reduced
                (when-some [value (if primitive? (coerce-value* element value) value)]
                  [value element]))))))
      nil
      type-choices)
    (when-some [value (get entity json-key)]
      (when-some [value (if primitive? (coerce-value* element value) value)]
        [value element]))))



;; ---- Add Element -----------------------------------------------------------


(defn- add-primitive-element
  {:arglists '([element id entity value])}
  [{:element/keys [type-code part-of-choice-type? type-attr-ident]
    :ElementDefinition/keys [path]
    :db/keys [ident valueType] :as element}
   id {:strs [system version]} value]
  (cond->
    (case type-code
      "code"
      (if-let [system (or system (:element/code-system-url element))]
        (let [tid (d/tempid :part/code)]
          [(cond->
             {:db/id tid
              :code/id
              (str (cond-> system version (str "|" version)) "|" value)
              :code/system system
              :code/code value}
             version
             (assoc :code/version version))
           [:db/add id ident tid]])
        (throw (ex-info (str "Can't refer to code `" value "` in element `"
                             path "` because its code system is unknown.")
                        {::anom/category ::anom/not-found
                         :element element})))
      [[:db/add id ident (write valueType value)]])
    part-of-choice-type?
    (conj [:db/add id type-attr-ident ident])))


(defn- json-reference->lookup-ref
  [{:strs [reference]}]
  ;; TODO: check for valid internal references
  (let [[type id] (str/split reference #"/")]
    [(keyword type "id") id]))


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


(defn- add-non-primitive-element
  "Returns tx-data for adding the non-primitive `value` as child of the
  existing entity with `id`."
  {:arglists '([db element id value])}
  [db {:element/keys [type-code part-of-choice-type? type-attr-ident]
       :ElementDefinition/keys [path] :db/keys [ident]}
   id value]
  (case type-code
    "Reference"
    (when-let [reference (get value "reference")]
      ;; TODO: check for valid internal references
      (let [[type ref-id] (str/split reference #"/")]
        (let [tid (d/tempid (keyword "part" type))]
          [{:db/id tid
            (keyword type "id") ref-id}
           [:db/add id ident tid]])))

    "BackboneElement"
    (let [tid (d/tempid (keyword "part" path))]
      (conj
        (upsert db ident {:db/id tid} value)
        [:db/add id ident tid]))

    "CodeableConcept"
    (let [tid (d/tempid (keyword "part" type-code))
          tx-data (upsert db (keyword type-code) {:db/id tid} value)
          code-tids (find-code-tids tx-data)]
      (into
        (cond->
          (conj tx-data [:db/add id ident tid])

          part-of-choice-type?
          (conj [:db/add id type-attr-ident ident]))
        (map (fn [tid] [:db/add id (index-ident ident) tid]))
        (if part-of-choice-type? [] code-tids)))

    (let [tid (d/tempid (keyword "part" type-code))]
      (cond->
        (conj
          (upsert db (keyword type-code) {:db/id tid} value)
          [:db/add id ident tid])

        part-of-choice-type?
        (conj [:db/add id type-attr-ident ident])))))


(defn add-element
  [db {:element/keys [primitive?] :as element} id entity value]
  (if primitive?
    (add-primitive-element element id entity value)
    (add-non-primitive-element db element id value)))



;; ---- Upsert Element --------------------------------------------------------

(defn- upsert-primitive-card-one-element
  [element old-entity new-entity new-value]
  (let [old-value (primitive-card-one-value-from-db element old-entity)]
    (when-not (= old-value new-value)
      (add-primitive-element element (:db/id old-entity) new-entity new-value))))


(defn- upsert-primitive-card-many-element
  [{:db/keys [ident valueType]} {:db/keys [id] :as old-entity} new-values]
  (let [old-values (into #{} value/read (ident old-entity))
        new-values (set new-values)
        retract (set/difference old-values new-values)
        add (set/difference new-values old-values)]
    (-> []
        (into
          (map (fn [value] [:db/retract id ident (write valueType value)]))
          retract)
        (into
          (map (fn [value] [:db/add id ident (write valueType value)]))
          add))))


(defn- upsert-reference
  [db {:db/keys [ident] :as element} entity reference]
  (when-not (= (ident entity) (d/entity db (json-reference->lookup-ref reference)))
    (add-non-primitive-element db element (:db/id entity) reference)))


(defn- upsert-non-primitive-card-one-element
  [db {:db/keys [ident] :element/keys [type-code] :as element} old-entity
   new-value]
  (if-let [old-value (ident old-entity)]
    (case type-code
      "Reference"
      (upsert-reference db element old-entity new-value)
      (upsert db (keyword type-code) old-value new-value))
    (add-non-primitive-element db element (:db/id old-entity) new-value)))


(defn- upsert-non-primitive-card-many-element
  [db {:db/keys [ident] :element/keys [type-code] :as element}
   {:db/keys [id] :as old-entity} new-values]
  (let [old-entities (non-primitive-card-many-values-from-db db element old-entity)
        new-entities (set new-values)
        retract (set/difference old-entities new-entities)
        add (set/difference new-entities old-entities)]
    (-> []
        (into
          (mapcat
            (fn [old-entity]
              (conj
                (upsert db (keyword type-code) (:entity (meta old-entity)) nil)
                [:db/retract id ident (:db/id (:entity (meta old-entity)))])))
          retract)
        (into
          (mapcat #(add-non-primitive-element db element id %))
          add))))


(defn upsert-single-typed-element
  {:arglists '([db element old-entity new-entity new-value])}
  [db {:element/keys [primitive?] :db/keys [cardinality] :as element}
   old-entity new-entity new-value]
  (if primitive?
    (if (= :db.cardinality/one cardinality)
      (upsert-primitive-card-one-element
        element old-entity new-entity new-value)
      (upsert-primitive-card-many-element
        element old-entity new-value))
    (if (= :db.cardinality/one cardinality)
      (upsert-non-primitive-card-one-element
        db element old-entity new-value)
      (upsert-non-primitive-card-many-element
        db element old-entity new-value))))


(declare retract-single-typed-element)


(defn upsert-choice-typed-element
  [db {:db/keys [ident]} old-entity new-entity new-value new-value-element]
  (if-let [old-value-ident (ident old-entity)]
    (let [old-value-element (util/cached-entity db old-value-ident)]
      (if (= old-value-element new-value-element)
        (upsert-single-typed-element
          db old-value-element old-entity new-entity new-value)
        (into
          (if-let [old-value (old-value-ident old-entity)]
            (retract-single-typed-element db old-value-element old-entity old-value)
            [])
          (add-element
            db new-value-element (:db/id old-entity) new-entity new-value))))
    (add-element
      db new-value-element (:db/id old-entity) new-entity new-value)))


(defn upsert-element
  [db {:element/keys [choice-type?] :as element} old-entity new-entity
   new-value new-value-element]
  (if choice-type?
    (upsert-choice-typed-element
      db element old-entity new-entity new-value new-value-element)
    (upsert-single-typed-element
      db element old-entity new-entity new-value)))



;; ---- Retract Element -------------------------------------------------------


(defn- retract-primitive-card-one-element
  [{:element/keys [type-code] :db/keys [ident]} {:db/keys [id]} value]
  (case type-code
    "code"
    [[:db/retract id ident (:db/id value)]]
    [[:db/retract id ident value]]))


(defn- retract-primitive-card-many-element
  [{:db/keys [ident]} {:db/keys [id]} value]
  (mapv (fn [value] [:db/retract id ident value]) value))


(defn- retract-non-primitive-card-one-element
  [db {:element/keys [type-code] :db/keys [ident]} {:db/keys [id]} value]
  (conj
    (upsert db (keyword type-code) value nil)
    [:db/retract id ident (:db/id value)]))


(defn- retract-non-primitive-card-many-element
  [db {:element/keys [type-code] :db/keys [ident]} {:db/keys [id]} value]
  (into
    []
    (mapcat
      (fn [value]
        (conj
          (upsert db (keyword type-code) value nil)
          [:db/retract id ident (:db/id value)])))
    value))


(defn- retract-single-typed-element
  {:arglists '([db element entity value])}
  [db {:element/keys [primitive?] :db/keys [cardinality] :as element} entity
   value]
  (if primitive?
    (if (= :db.cardinality/one cardinality)
      (retract-primitive-card-one-element
        element entity value)
      (retract-primitive-card-many-element
        element entity value))
    (if (= :db.cardinality/one cardinality)
      (retract-non-primitive-card-one-element
        db element entity value)
      (retract-non-primitive-card-many-element
        db element entity value))))


(defn- retract-choice-typed-element
  [db {:db/keys [ident]} {:db/keys [id] :as entity}]
  (when-let [type-ident (ident entity)]
    (-> (if-some [value (type-ident entity)]
          (retract-single-typed-element db (util/cached-entity db type-ident) entity value)
          [])
        (conj [:db/retract id ident type-ident]))))


(defn- retract-element
  "Returns tx-data for retracting `element` from `entity` if it holds some
  value."
  {:arglists '([db element entity])}
  [db {:db/keys [ident] :element/keys [choice-type?] :as element} entity]
  (if choice-type?
    (retract-choice-typed-element db element entity)
    (when-some [value (ident entity)]
      (retract-single-typed-element db element entity value))))


(defn- upsert
  "Takes a `db` and `type-ident`, the old version of an entity as Datomic entity
  and the new version as JSON with string keys and returns Datomic transaction
  data for the update."
  [db type-ident old-entity new-entity]
  (into
    []
    (comp
      (map #(util/cached-entity db %))
      (filter
        (fn [{:db/keys [ident]}]
          (not (#{:Coding/system :Coding/version} ident))))
      (mapcat
        (fn [element]
          (if-let [[new-value value-element] (find-json-value db element new-entity)]
            (upsert-element db element old-entity new-entity new-value value-element)
            (retract-element db element old-entity)))))
    (:type/elements (util/cached-entity db type-ident))))


(defn- version-increment [{:db/keys [id] :keys [version]}]
  [:db.fn/cas id :version version (if version (inc version) 0)])


(s/fdef resource-update
  :args (s/cat :db ::ds/db :resource (s/nilable (s/map-of string? some?)))
  :ret ::ds/tx-data)

(defn resource-update
  "Takes a `db` and the new version of a `resource` as JSON with string keys and
  returns Datomic transaction data for the update.

  The resource has to have at least a `resourceType` and an `id`.

  The resource is compared with its current version in the database. If no
  current version is found, transaction data for creating it is returned."
  {:arglists '([db resource])}
  [db {:strs [resourceType id] :as resource}]
  (assert resourceType)
  (assert id)
  (let [old-resource (or (d/entity db [(keyword resourceType "id") id])
                         {:db/id (d/tempid (keyword "part" resourceType))})
        tx-data (->> (update resource "meta" dissoc "versionId" "lastUpdated")
                     (upsert db (keyword resourceType) old-resource))]
    (conj tx-data (version-increment old-resource))))


(defn- category [e]
  (case (:db/error (ex-data e))
    :db.error/cas-failed ::anom/conflict
    ::anom/fault))


(s/fdef transact-async
  :args (s/cat :conn ::ds/conn :tx-data ::ds/tx-data))

(defn transact-async [conn tx-data]
  (-> (d/transact-async conn tx-data)
      (md/catch ExecutionException #(md/error-deferred (.getCause ^Exception %)))
      (md/catch
        (fn [e]
          (md/error-deferred
            {::anom/category (category e)
             :data (ex-data e)})))))
