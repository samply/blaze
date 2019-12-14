(ns blaze.datomic.pull
  "Create Pull Patterns from FHIR Structure Definitions"
  (:require
    [blaze.datomic.quantity]
    [blaze.datomic.util :as util]
    [blaze.datomic.value :as value]
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds])
  (:import
    [blaze.datomic.quantity
     UcumQuantityWithoutUnit
     UcumQuantityWithSameUnit
     UcumQuantityWithDifferentUnit
     CustomQuantity]
    [java.time Instant LocalDate LocalDateTime LocalTime OffsetDateTime Year
               YearMonth]
    [java.time.format DateTimeFormatter]
    [java.util Base64]))


(set! *warn-on-reflection* true)


(defprotocol ToJson
  "Converts a primitive value into it's JSON Form.

  Example: a LocalDate into it's formatted string."
  (-to-json [x]))


(extend-protocol ToJson
  (Class/forName "[B")
  (-to-json [bytes]
    (.encodeToString (Base64/getEncoder) ^bytes bytes))

  Year
  (-to-json [year]
    (str year))

  YearMonth
  (-to-json [year-month]
    (str year-month))

  LocalDate
  (-to-json [date]
    (.format (DateTimeFormatter/ISO_LOCAL_DATE) date))

  LocalDateTime
  (-to-json [date-time]
    (.format (DateTimeFormatter/ISO_LOCAL_DATE_TIME) date-time))

  LocalTime
  (-to-json [time]
    (.format (DateTimeFormatter/ISO_LOCAL_TIME) time))

  OffsetDateTime
  (-to-json [date-time]
    (.format (DateTimeFormatter/ISO_OFFSET_DATE_TIME) date-time))

  Instant
  (-to-json [instant]
    (str instant))

  UcumQuantityWithoutUnit
  (-to-json [q]
    {"value" (.value q)
     "system" "http://unitsofmeasure.org"
     "code" (.code q)})

  UcumQuantityWithSameUnit
  (-to-json [q]
    {"value" (.value q)
     "unit" (.code q)
     "system" "http://unitsofmeasure.org"
     "code" (.code q)})

  UcumQuantityWithDifferentUnit
  (-to-json [q]
    {"value" (.value q)
     "unit" (.unit q)
     "system" "http://unitsofmeasure.org"
     "code" (.code q)})

  CustomQuantity
  (-to-json [q]
    (cond->
      {"value" (.value q)}
      (.unit q) (assoc "unit" (.unit q))
      (.system q) (assoc "system" (.system q))
      (.code q) (assoc "code" (.code q))))

  Object
  (-to-json [x]
    x))


(defn to-json [x]
  (-to-json (value/read x)))


(declare pull-non-primitive)
(declare pull-element)
(declare pull-resource)


(defn- pull-backbone-element
  [db element value]
  (into
    (with-meta {} {:entity value})
    (map #(pull-element db (util/cached-entity db %) value))
    (:type/elements element)))


(defn- pull-contained-resource [db resource]
  (let [type (util/entity-type resource)]
    (-> (pull-non-primitive db (keyword type) resource)
        (assoc "resourceType" type
               "id" (:local-id resource)))))


(defn- convert-value
  "Converts `value` into it's JSON representation."
  {:arglists '([db element entity value])}
  [db {:element/keys [primitive? type type-code value-set-binding]} value]
  (case type-code
    "code"
    (if value-set-binding
      (symbol (:code/code value))
      (:code/code value))
    "BackboneElement"
    (pull-backbone-element db (util/cached-entity db type) value)
    "Resource"
    (pull-contained-resource db value)
    (if primitive?
      (to-json value)
      (pull-non-primitive db type value))))


(s/fdef pull-element
  :args (s/cat :db ::ds/db :element :schema/element :entity any?)
  :ret (s/tuple string? some?))

(defn pull-element
  "Pulls `element` from `entity` deep.

  Returns a tuple of JSON key and the value which is either a map or a vector of
  maps."
  {:arglists '([db element entity])}
  [db {:db/keys [ident cardinality]
       :element/keys [choice-type? json-key]
       :as element}
   entity]
  (case ident
    :Coding/system
    (when-let [system (-> entity :Coding/code :code/system)]
      ["system" system])
    :Coding/version
    (when-let [version (-> entity :Coding/code :code/version)]
      ["version" version])
    (when-some [value (ident entity)]
      (if choice-type?
        (pull-element db (util/cached-entity db value) entity)
        [json-key
         (if (= :db.cardinality/many cardinality)
           (mapv
             #(convert-value db element %)
             value)
           (convert-value db element value))]))))


(s/fdef pull-non-primitive
  :args (s/cat :db ::ds/db :type-ident keyword? :entity ::ds/entity))

(defn pull-non-primitive [db type-ident entity]
  (into
    (with-meta {} {:entity entity})
    (map #(pull-element db (util/cached-entity db %) entity))
    (:type/elements (util/cached-entity db type-ident))))


(s/fdef pull-resource*
  :args (s/cat :db ::ds/db :type string? :resource ::ds/entity))

(defn pull-resource*
  "Type has to be valid."
  [db type resource]
  (let [last-transaction (util/last-transaction resource)
        last-transaction-instant (util/tx-instant last-transaction)
        version-id (str (d/tx->t (:db/id last-transaction)))]
    (-> (pull-non-primitive db (keyword type) resource)
        (assoc "resourceType" type)
        (assoc-in ["meta" "versionId"] version-id)
        (assoc-in ["meta" "lastUpdated"] (-to-json last-transaction-instant))
        (with-meta {:last-transaction-instant last-transaction-instant
                    :version-id version-id
                    :deleted (util/deleted? resource)}))))


(s/fdef pull-resource
  :args (s/cat :db ::ds/db :type string? :id string?))

(defn pull-resource
  "Type has to be valid."
  [db type id]
  (some->> (util/resource db type id) (pull-resource* db type)))


(defn summary-pattern*
  [{:db/keys [ident] :type/keys [elements]}]
  (if elements
    {ident (into [] (comp (filter :ElementDefinition/isSummary) (map summary-pattern*)) elements)}
    ident))


(s/fdef summary-pattern
  :args (s/cat :type some?))

(defn summary-pattern
  [{:type/keys [elements]}]
  (into [] (comp (filter :ElementDefinition/isSummary) (map summary-pattern*)) elements))


(def type-pattern
  '[:db/ident :ElementDefinition/isSummary {:type/elements ...}])


(s/fdef pull-summary
  :args (s/cat :type-name string? :entity ::ds/entity)
  :ret map?)

(defn pull-summary
  "Pulls all summary attributes of `entity`. Needs the `type-name` of the entity
  as string. Returns a map."
  [type-name entity]
  (let [db (d/entity-db entity)
        type (d/pull db type-pattern (keyword type-name))]
    (d/pull db (summary-pattern type) (:db/id entity))))
