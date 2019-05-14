(ns blaze.datomic.pull
  "Create Pull Patterns from FHIR Structure Definitions"
  (:require
    [blaze.datomic.quantity :as quantity]
    [blaze.spec]
    [blaze.datomic.util :as util]
    [blaze.datomic.value :as value]
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds])
  (:import
    [java.time LocalDate LocalDateTime Year Instant]
    [javax.measure Quantity]))


(set! *warn-on-reflection* true)


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
  Instant
  (-to-json [instant]
    (str instant))
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
        (pull-element db (util/cached-entity db value) entity)
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


(defn- pull-non-primitive [db type-ident value]
  (into
    {}
    (map #(pull-element db (util/cached-entity db %) value))
    (:type/elements (util/cached-entity db type-ident))))


(s/fdef pull-resource
  :args (s/cat :db ::ds/db :type string? :id string?))

(defn pull-resource [db type id]
  (when-let [resource (d/entity db [(keyword type "id") id])]
    (let [last-transaction (util/last-transaction resource)
          last-transaction-instant (util/tx-instant last-transaction)
          version-id (str (d/tx->t (:db/id last-transaction)))]
      (-> (pull-non-primitive db (keyword type) resource)
          (assoc "resourceType" type)
          (assoc-in ["meta" "versionId"] version-id)
          (assoc-in ["meta" "lastUpdated"] (-to-json last-transaction-instant))
          (with-meta {:last-transaction-instant last-transaction-instant
                      :version-id version-id})))))


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
