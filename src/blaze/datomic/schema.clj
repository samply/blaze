(ns blaze.datomic.schema
  "Creates a Datomic schema based on FHIR structure definitions."
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [datomic-tools.schema :refer [defattr defpart]]
    [blaze.datomic.element-definition]
    [blaze.spec]
    [blaze.util :as u]))


(defattr :type/elements
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
  :db/valueType :db.type/ref
  :db/cardinality :db.cardinality/one)


(defattr :element/type-code
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one)


(defattr :element/json-key
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one)


(defattr :element/code-system-url
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


(defattr :version
  :db/valueType :db.type/long
  :db/cardinality :db.cardinality/one)


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
    "decimal" :db.type/double
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


(defn- with-ns
  "Adds `ns` to all non-namespaced keywords in `m`."
  [ns m]
  (into {} (map (fn [[k v]] (if (namespace k) [k v] [(keyword ns (name k)) v]))) m))


(defn needs-partition?
  "Returns true iff the element is a parent and needs to create a partition and
  link it's childs."
  [{:keys [type]}]
  (or (nil? type) (= "BackboneElement" (-> type first :code))))


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
        (assoc type :path [butlast (str last (u/title-case code))]))
      type)))


(defn code-system-url [{:keys [path]}]
  ;; TODO: HACK use a terminology service instead
  (case path
    "Address.type"
    "http://hl7.org/fhir/address-type"

    "Address.use"
    "http://hl7.org/fhir/address-use"

    "Age.comparator"
    "http://hl7.org/fhir/quantity-comparator"

    "Attachment.contentType"
    "urn:ietf:bcp:13"

    "Attachment.language"
    "urn:ietf:bcp:47"

    "Bundle.type"
    "http://hl7.org/fhir/bundle-type"

    "ContactPoint.system"
    "http://hl7.org/fhir/contact-point-system"

    "ContactPoint.use"
    "http://hl7.org/fhir/contact-point-use"

    "HumanName.use"
    "http://hl7.org/fhir/name-use"

    "Identifier.use"
    "http://hl7.org/fhir/identifier-use"

    "Money.currency"
    "urn:iso:std:iso:4217"

    "Narrative.status"
    "http://hl7.org/fhir/narrative-status"

    "Observation.language"
    "urn:ietf:bcp:47"

    "Observation.status"
    "http://hl7.org/fhir/observation-status"

    "Patient.contact.gender"
    "http://hl7.org/fhir/administrative-gender"

    "Patient.gender"
    "http://hl7.org/fhir/administrative-gender"

    "Patient.language"
    "urn:ietf:bcp:47"

    "Patient.link.type"
    "http://hl7.org/fhir/link-type"

    "Quantity.comparator"
    "http://hl7.org/fhir/quantity-comparator"

    "Signature.targetFormat"
    "urn:ietf:bcp:13"

    "Signature.sigFormat"
    "urn:ietf:bcp:13"

    "Specimen.language"
    "urn:ietf:bcp:47"

    "Timing.repeat.dayOfWeek"
    "http://hl7.org/fhir/days-of-week"

    "Timing.repeat.durationUnit"
    "http://unitsofmeasure.org"

    "Timing.repeat.periodUnit"
    "http://unitsofmeasure.org"

    "Timing.repeat.when"
    "http://hl7.org/fhir/event-timing"
    ;; TODO: also http://terminology.hl7.org/CodeSystem/v3-TimingEvent

    nil))


(s/fdef element-definition-tx-data
  :args (s/cat :element-definition :fhir.un/ElementDefinition)
  :ret ::ds/tx-data)

(defn element-definition-tx-data
  "Returns transaction data which can be used to upsert `element-definition`."
  {:arglists '([element-definition])}
  [{:keys [path type max isSummary] :as element}]
  (let [parent-path (parent-path path)
        ident (path->ident path)
        choice-type? (str/ends-with? path "[x]")]
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
              :element/choice-type? choice-type?
              :ElementDefinition/path path}
             (and (not choice-type?) (component? type))
             (assoc :db/isComponent true)
             (and (not choice-type?) (= "id" (-> type first :code)))
             (assoc :db/unique :db.unique/identity)
             (not choice-type?)
             (assoc
               :db/valueType (fhir-type-code->db-type (-> type first :code))
               :element/primitive? (primitive? (first type))
               ;; TODO: the Extension StructureDefinition misses a value for `code`
               :element/type-code (or (-> type first :code) "string")
               :element/json-key (last (str/split path #"\.")))
             choice-type?
             (assoc :db/valueType :db.type/ref)
             (and (= "code" (-> type first :code)) (code-system-url element))
             (assoc :element/code-system-url (code-system-url element)))
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
                 (not (#{"Extension" "BackboneElement" "Bundle" "Element"}
                        code)))
            (conj [:db/add (str/join "." choice-path) :element/type code]))))
      (needs-partition? element)
      (conj
        {:db/id (str "part." path)
         :db/ident (keyword "part" path)}
        [:db/add :db.part/db :db.install/partition (str "part." path)])
      (and type (not choice-type?) (not (primitive? (first type)))
           (not (#{"Extension" "BackboneElement" "Bundle" "Element"}
                  (-> type first :code))))
      (conj [:db/add path :element/type (-> type first :code)])
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
  [{{elements :element} :snapshot}]
  (into [] (mapcat element-definition-tx-data) elements))


(def ^:private remove-unwanted
  "Removes currently not needed structure definitions."
  (remove #(#{"Bundle"} (:id %))))


(s/fdef structure-definition-schemas
  :args (s/cat :structure-definitions (s/coll-of :fhir.un/StructureDefinition))
  :ret ::ds/tx-data)

(defn structure-definition-schemas [structure-definitions]
  (into
    []
    (comp
      remove-unwanted
      (mapcat structure-definition-tx-data))
    structure-definitions))
