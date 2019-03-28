(ns life-fhir-store.datomic.schema
  (:require
    [clojure.spec.alpha :as s]
    [datomic-tools.schema :as dts]
    [datomic-spec.core :as ds]
    [life-fhir-store.datomic.coding]
    [life-fhir-store.spec]
    [life-fhir-store.structure-definition :as sd]))


(defn- db-type
  "http://hl7.org/fhir/datatypes.html"
  [code]
  (case code
    "boolean" :db.type/boolean
    ("integer" "unsignedInt" "positiveInt") :db.type/long
    ("string" "code" "id" "markdown" "uri" "url" "canonical" "oid" "xhtml") :db.type/string
    ("date" "dateTime" "time") :db.type/bytes
    "decimal" :db.type/double
    "base64Binary" :db.type/bytes
    "instant" :db.type/instant
    "uuid" :db.type/uuid
    "Quantity" :db.type/bytes
    :db.type/ref))


(s/fdef attribute-definition
  :args (s/cat :element :life/element-definition))

(defn- attribute-definition
  [{:life.element-definition/keys [key]
    {type-code :life.element-definition.type/code}
    :life.element-definition/type
    :as element}]
  (let [valueType (db-type type-code)]
    (cond->
      #:db{:ident key
           :valueType valueType
           :cardinality
           (if (sd/cardinality-many? element)
             :db.cardinality/many
             :db.cardinality/one)
           :isComponent
           (and (= :db.type/ref valueType)
                (not (#{"Coding" "Quantity" "Reference"} type-code)))}
      (= "id" (name key))
      (assoc :db/unique :db.unique/identity))))


(s/fdef schema
  :args (s/cat :structure-definition :life/structure-definition))

(defn schema
  "Converts a Life structure definition into a seq of Datomic attribute
  definitions."
  [{:life.structure-definition/keys [name elements]}]
  (into
    [{:db/id (str "partition." name)
      :db/ident (keyword "life.part" name)}
     [:db/add :db.part/db :db.install/partition (str "partition." name)]]
    (map attribute-definition)
    (vals elements)))


(def ^:private remove-fixed
  "Removes hard-coded structure definitions."
  (remove #(#{"Coding"} (:life.structure-definition/id %))))


(def ^:private remove-unwanted
  "Removes currently not needed structure definitions."
  (remove #(#{"Bundle" "Meta"} (:life.structure-definition/id %))))


(s/fdef all-schema
  :args (s/cat :structure-definitions (s/coll-of :life/structure-definition))
  :ret ::ds/tx-data)

(defn all-schema [structure-definitions]
  (into
    (dts/schema)
    (comp remove-fixed
          remove-unwanted
          (mapcat schema))
    structure-definitions))
