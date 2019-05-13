(ns life-fhir-store.datomic.pull
  "Create Pull Patterns from FHIR Structure Definitions"
  (:require
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [life-fhir-store.datomic.value :as value]
    [life-fhir-store.spec]
    [life-fhir-store.structure-definition :as sd]
    [life-fhir-store.datomic.quantity :as quantity])
  (:import
    [java.time LocalDate LocalDateTime Year]
    [javax.measure Quantity]))


(defprotocol ToJson
  "Converts a primitive value into it's JSON Form.

  Example: a LocalDate into it's formatted string."
  (-to-json [x]))


(extend-protocol ToJson
  Year
  (-to-json [year]
    (str year))
  LocalDate
  (-to-json [date]
    (str date))
  LocalDateTime
  (-to-json [date-time]
    (str date-time))
  Quantity
  (-to-json [q]
    {"value" (.getValue q)
     "unit" (quantity/format-unit (.getUnit q))})
  Object
  (-to-json [x]
    x))


(defn- to-json [x]
  (-to-json (value/read x)))


(declare pull-non-primitive)


(defn pull-element
  {:arglists '([db element entity])}
  [db {:db/keys [ident cardinality]
       :element/keys [choice-type? primitive? type type-code json-key]}
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
        (pull-element db (d/entity db value) entity)
        [json-key
         (cond
           (= :db.cardinality/many cardinality)
           (mapv
             (fn [value]
               (if primitive?
                 (to-json value)
                 (pull-non-primitive db type value)))
             value)
           (= "code" type-code)
           (:code/code value)
           primitive?
           (to-json value)
           :else
           (pull-non-primitive db type value))]))))


(defn pull-non-primitive [db type-ident value]
  (into
    {}
    (map #(pull-element db (d/entity db %) value))
    (:type/elements (d/entity db type-ident))))


(defn pull-resource [db type id]
  (when-let [resource (d/entity db [(keyword type "id") id])]
    (-> (pull-non-primitive db (keyword type) resource)
        (assoc "resourceType" type)
        (with-meta {:db/id (:db/id resource)}))))


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
