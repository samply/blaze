(ns blaze.datomic.schema
  "Creates a Datomic schema based on FHIR structure definitions."
  (:require
    [clojure.spec.alpha :as s]
    [cuerdas.core :as str]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [datomic-tools.schema :refer [defattr defunc defpart]]
    [blaze.datomic.element-definition]))


(defattr :type/elements
  "References to all data elements of a non-primitive data type."
  :db/valueType :db.type/ref
  :db/cardinality :db.cardinality/many
  :db/isComponent true)


(defattr :element/primitive?
  :db/valueType :db.type/boolean
  :db/cardinality :db.cardinality/one)


(defattr :element/choice-type?
  :db/valueType :db.type/boolean
  :db/cardinality :db.cardinality/one)


(defattr :element/part-of-choice-type?
  :db/valueType :db.type/boolean
  :db/cardinality :db.cardinality/one)


(defattr :element/type-attr-ident
  :db/valueType :db.type/keyword
  :db/cardinality :db.cardinality/one)


(defattr :element/type-choices
  :db/valueType :db.type/ref
  :db/cardinality :db.cardinality/many)


(defattr :element/type
  "Reference to the data type of a non-primitive data element."
  :db/valueType :db.type/ref
  :db/cardinality :db.cardinality/one)


(defattr :element/type-code
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one)


(defattr :element/json-key
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one)


(defattr :element/value-set-binding
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one)


(defpart :part/code)

(defattr :code/id
  "Concatenation of system, possibly version and code."
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one
  :db/unique :db.unique/identity)


(defattr :code/system
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one)


(defattr :code/version
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one)


(defattr :code/code
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one)


(defattr :instance/version
  "The version of one particular resource instance."
  :db/valueType :db.type/long
  :db/cardinality :db.cardinality/one)


(defattr :type/version
  "The version (number of changes) of all resources of a particular resource
  type."
  :db/valueType :db.type/long
  :db/cardinality :db.cardinality/one)


(defattr :system/version
  "The version (number of changes) of all resources in the whole system."
  :db/valueType :db.type/long
  :db/cardinality :db.cardinality/one)


(defattr :type/total
  "Total number of non-deleted resources of a particular type.

  The total number of non-deleted resources of a particular type can be found at
  the type idents like :Patient or :Observation."
  :db/valueType :db.type/long
  :db/cardinality :db.cardinality/one
  :db/noHistory true)


(defattr :system/total
  "Total number of non-deleted resources of the whole system.

  The total number of all non-deleted resources of the whole system can be
  found at :system."
  :db/valueType :db.type/long
  :db/cardinality :db.cardinality/one
  :db/noHistory true)


(defattr :local-id
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one)


(defattr :tx/resources
  "A set of resources changed in a transaction."
  :db/valueType :db.type/ref
  :db/cardinality :db.cardinality/many)


(defunc fn/increment-type-total
  "Increments the total number of resources of a particular type by `amount`.

  Type is the ident of the resources type like :Patient. The amount will be
  negative if resources are deleted."
  [db type amount]
  [[:db/add type :type/total
    (- (get (d/entity db type) :type/total 0) amount)]])


(defunc fn/increment-system-total
  "Increments the total number of resources in the whole system by `amount`.

  The amount will be negative if resources are deleted."
  [db amount]
  [[:db/add :system :system/total
    (- (get (d/entity db :system) :system/total 0) amount)]])


(defunc fn/increment-type-version
  "Increments the version (number of resource changes) of a particular resource
  type by `amount`.

  Type is the ident of the resources type like :Patient. The amount will be
  always positive."
  [db type amount]
  [[:db/add type :type/version
    (- (get (d/entity db type) :type/version 0) amount)]])


(defunc fn/increment-system-version
  "Increments the version (number of resource changes) in the whole system by
  `amount`.

  The amount will be always positive."
  [db amount]
  [[:db/add :system :system/version
    (- (get (d/entity db :system) :system/version 0) amount)]])


(defn- fhir-type-code->db-type
  "http://hl7.org/fhir/datatypes.html"
  [code]
  ;; TODO: the Extension StructureDefinition misses a value for `code`
  (case (or code "string")
    "boolean" :db.type/boolean
    ("integer" "unsignedInt" "positiveInt") :db.type/long
    "code" :db.type/ref
    ("string" "id" "markdown" "uri" "url" "canonical" "oid" "xhtml") :db.type/string
    ("date" "dateTime" "time") :db.type/bytes
    "decimal" :db.type/bigdec
    "base64Binary" :db.type/bytes
    "instant" :db.type/instant
    "uuid" :db.type/uuid
    "Quantity" :db.type/bytes
    :db.type/ref))


(defn- remove-choice-suffix
  "Removes `[x]` from the end of `path`."
  [path]
  (if (str/ends-with? path "[x]")
    (subs path 0 (- (count path) 3))
    path))


(s/fdef path->ident
  :args (s/cat :path :ElementDefinition/path)
  :ret keyword?)

(defn path->ident [path]
  (let [path (remove-choice-suffix path)
        parts (str/split path #"\.")]
    (keyword
      (some->> (butlast parts) (str/join "."))
      (last parts))))


(defn- component? [type]
  (if (= 1 (count type))
    (let [code (-> type first :code)]
      (and (= :db.type/ref (fhir-type-code->db-type code))
           (not (#{"code" "Reference"} code))))
    false))


(defn- backbone-element?
  {:arglists '([element])}
  [{[{:keys [code]}] :type}]
  (= "BackboneElement" code))


(defn- has-content-reference?
  {:arglists '([element])}
  [{content-reference :contentReference}]
  (some? content-reference))


(defn- needs-partition?
  "Returns true iff the `element` is a parent and needs to create a partition
  and link it's childs."
  {:arglists '([element])}
  [{:keys [type] :as element}]
  (and (or (nil? type) (backbone-element? element))
       (not (has-content-reference? element))))


;; TODO: the Extension StructureDefinition misses a value for `code`
(defn primitive? [{:keys [code] :or {code "string"}}]
  (or (Character/isLowerCase ^char (first code)) (= "Quantity" code)))


(defn parent-path [path]
  (some->> (butlast (str/split path #"\.")) (str/join ".")))


(defn choice-paths [{:keys [path type]}]
  (let [path (remove-choice-suffix path)
        path (str/split path #"\.")
        butlast (str/join "." (butlast path))
        last (last path)]
    (mapv
      (fn [{:keys [code] :as type}]
        (assoc type :path [butlast (str last (str/capital code))]))
      type)))


(defn- normal-non-primitive-type? [choice-type? type]
  (and type (not choice-type?) (not (primitive? (first type)))
       (not (#{"BackboneElement" "Bundle"} (-> type first :code)))))


(defn- resolve-element [structure-definition reference]
  (->> (-> structure-definition :snapshot :element)
       (some #(when (= (subs reference 1) (:id %)) %))))


(s/fdef element-definition-tx-data
  :args (s/cat :structure-definition :fhir.un/StructureDefinition
               :element-definition :fhir.un/ElementDefinition)
  :ret ::ds/tx-data)

(defn element-definition-tx-data
  "Returns transaction data which can be used to upsert `element-definition`."
  {:arglists '([structure-definition element-definition])}
  [structure-definition
   {:keys [path type max isSummary] content-reference :contentReference
    :as element}]
  (let [parent-path (parent-path path)
        ident (path->ident path)
        choice-type? (str/ends-with? path "[x]")
        content-element (some->> content-reference (resolve-element structure-definition))
        type (if content-element (:type content-element) type)]
    (cond->
      [(cond->
         (if type
           (cond->
             {:db/id path
              :db/ident ident
              :db/cardinality
              (if (= "*" max)
                :db.cardinality/many
                :db.cardinality/one)
              :element/choice-type? choice-type?}

             (and (not choice-type?) (component? type))
             (assoc :db/isComponent true)

             (let [{:keys [name kind]} structure-definition]
               (and (= "resource" kind) (= (str name ".id") path)))
             (assoc :db/unique :db.unique/identity)

             (#{"Library.url" "Measure.url"} path)
             (assoc :db/index true)

             (not choice-type?)
             (assoc
               :db/valueType (fhir-type-code->db-type (-> type first :code))
               :element/primitive? (primitive? (first type))
               ;; TODO: the Extension StructureDefinition misses a value for `code`
               :element/type-code (or (-> type first :code) "string")
               :element/json-key (last (str/split path #"\.")))

             choice-type?
             (assoc :db/valueType :db.type/ref)

             (and (= "code" (-> type first :code)) (-> element :binding :valueSet))
             (assoc :element/value-set-binding (-> element :binding :valueSet)))

           {:db/id path
            :db/ident ident})

         isSummary
         (assoc :ElementDefinition/isSummary true))]

      choice-type?
      (into
        (mapcat identity)
        (for [{[ns name :as choice-path] :path :keys [code]} (choice-paths element)]
          (cond->
            [{:db/id (str/join "." choice-path)
              :db/ident (keyword ns name)
              :db/valueType (fhir-type-code->db-type code)
              :db/cardinality
              (if (= "*" max)
                :db.cardinality/many
                :db.cardinality/one)
              :element/primitive? (primitive? {:code code})
              :element/part-of-choice-type? true
              :element/type-attr-ident ident
              :element/type-code code
              :element/json-key name}
             [:db/add path :element/type-choices (str/join "." choice-path)]]
            (and (not (primitive? {:code code}))
                 (not (#{"BackboneElement" "Bundle"} code)))
            (conj [:db/add (str/join "." choice-path) :element/type code]))))

      (needs-partition? element)
      (conj
        {:db/id (str "part." path)
         :db/ident (keyword "part" path)}
        [:db/add :db.part/db :db.install/partition (str "part." path)])

      (normal-non-primitive-type? choice-type? type)
      (conj [:db/add path :element/type (-> type first :code)])

      content-element
      (conj [:db/add path :element/type (:path content-element)])

      (backbone-element? element)
      (conj [:db/add path :element/type path])

      parent-path
      (conj [:db/add parent-path :type/elements path])

      ;; extra attribute consisting of a direct list of codes of the CodeableConcept
      (and (not choice-type?) (= "CodeableConcept" (-> type first :code)))
      (conj
        {:db/ident (keyword (str (namespace ident) ".index") (name ident))
         :db/valueType :db.type/ref
         :db/cardinality :db.cardinality/many}))))


(s/fdef structure-definition-tx-data
  :args (s/cat :structure-definition :fhir.un/StructureDefinition)
  :ret ::ds/tx-data)

(defn structure-definition-tx-data
  "Returns transaction data which can be used to upsert `structure-definition`."
  [{{elements :element} :snapshot :as structure-definition}]
  (into [] (mapcat #(element-definition-tx-data structure-definition %)) elements))


(s/fdef structure-definition-schemas
  :args (s/cat :structure-definitions (s/coll-of :fhir.un/StructureDefinition))
  :ret ::ds/tx-data)

(defn structure-definition-schemas [structure-definitions]
  (into
    [{:db/id "system"
      :db/ident :system}]
    (comp
      (remove :experimental)
      (mapcat structure-definition-tx-data))
    structure-definitions))
