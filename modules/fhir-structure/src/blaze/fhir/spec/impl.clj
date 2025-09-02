(ns blaze.fhir.spec.impl
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.fhir.spec.impl.intern :as intern]
   [blaze.fhir.spec.impl.specs :as specs]
   [blaze.fhir.spec.impl.xml :as xml]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.string-util :as su]
   [blaze.util :refer [str]]
   [clojure.alpha.spec :as s]
   [clojure.data.xml.name :as xml-name]
   [clojure.data.xml.node :as xml-node]
   [clojure.string :as str])
  (:import
   [java.net URLEncoder]
   [java.nio.charset StandardCharsets]))

(xml-name/alias-uri 'f "http://hl7.org/fhir")

(set! *warn-on-reflection* true)

(def ^:const fhir-namespace
  (str "xmlns." (URLEncoder/encode "http://hl7.org/fhir" StandardCharsets/UTF_8)))

(defn- find-fhir-type [{:keys [extension]}]
  (some
   #(when (= "http://hl7.org/fhir/StructureDefinition/structuredefinition-fhir-type" (:url %))
      (:valueUrl %))
   extension))

(defn- find-regex [{:keys [extension]}]
  (some
   #(when (= "http://hl7.org/fhir/StructureDefinition/regex" (:url %))
      (re-pattern (:valueString %)))
   extension))

(defn- type-regex [type]
  (or (when (= "base64Binary" (find-fhir-type type))
        #"([0-9a-zA-Z\\+/=]{4})+")
      (find-regex type)
      (when (= "id" (find-fhir-type type))
        #"[A-Za-z0-9\-\.]{1,64}")
      (when (= "url" (find-fhir-type type))
        #"[A-Za-z0-9\-\.]{1,64}")))

(def id-matcher-form
  `(fn [~'s] (.matches (re-matcher #"[A-Za-z0-9\-\.]{1,64}" ~'s))))

(def ^{:arglists '([s])} intern-string
  "Returns an interned String without using String/intern."
  (intern/intern-value identity))

(def uri-matcher-form
  `(specs/regex #"[\u0021-\uFFFF]*" intern-string))

(def conform-xml-value
  "Takes the value out of an XML element."
  (comp :value :attrs))

(defn unform-xml-value
  "Creates an XML element with `value` as attribute."
  [value]
  (xml-node/element nil {:value value}))

(defn id-string-spec [modifier]
  (case modifier
    (nil :xmlAttr) `(s/and string? ~id-matcher-form)
    :xml `(s/and xml/element? (s/conformer conform-xml-value unform-xml-value) ~id-matcher-form)))

(defn uri-string-spec [modifier]
  (case modifier
    (nil :xmlAttr) `(s/and string? ~uri-matcher-form)
    :xml `(s/and xml/element? (s/conformer conform-xml-value unform-xml-value) ~uri-matcher-form)))

(defn- string-spec [modifier type]
  (case (find-fhir-type type)
    "id" (id-string-spec modifier)
    "uri" (uri-string-spec modifier)
    (if-let [regex (type-regex type)]
      `(s/and string? (fn [~'s] (.matches (re-matcher ~regex ~'s))))
      `string?)))

(defn- system-type->spec-form [modifier {:keys [code] :as type}]
  (case code
    "http://hl7.org/fhirpath/System.String" (string-spec modifier type)
    "http://hl7.org/fhirpath/System.Time" `string?
    "http://hl7.org/fhirpath/System.Date" (string-spec modifier type)
    "http://hl7.org/fhirpath/System.DateTime" `string?
    "http://hl7.org/fhirpath/System.Integer" `int?
    "http://hl7.org/fhirpath/System.Decimal" `decimal?
    "http://hl7.org/fhirpath/System.Boolean" `boolean?
    (throw-anom (ba/unsupported (format "Unsupported system type `%s`." code)))))

(defn- split-path [path]
  (str/split path #"\."))

(defn- key-name [last-path-part {:keys [code]}]
  (if (str/ends-with? last-path-part "[x]")
    (str/replace last-path-part "[x]" (su/capital code))
    last-path-part))

(defn- path-parts->key [path-parts type]
  (keyword
   (str/join "." (cons "fhir" (butlast path-parts)))
   (key-name (last path-parts) type)))

(defn- path->key [path type]
  (path-parts->key (split-path path) type))

(defn- path-parts->key' [prefix path-parts]
  (keyword
   (str/join "." (cons prefix (butlast path-parts)))
   (last path-parts)))

(defn- choice-pair [path type]
  [(keyword (name (path->key path type)))
   (keyword "fhir" (:code type))])

(defn- choice-spec-form [path types]
  `(s/or ~@(mapcat #(choice-pair path %) types)))

(defn- choice-spec-def* [modifier path code min max]
  {:key (path-parts->key' (str "fhir." (name modifier)) (split-path (str/replace path "[x]" (su/capital code))))
   :modifier modifier
   :min min
   :max max
   :spec-form (keyword (str "fhir." (name modifier)) code)})

(defn- choice-spec-def [modifier path path-parts code min max]
  (cond-> (choice-spec-def* modifier path code min max)
    (identical? :xml modifier)
    (assoc :choice-group (keyword (last path-parts)))))

(defn- system-spec-defs [{:keys [path min max representation] [type] :type}]
  (let [rep (some-> representation first keyword)]
    [{:key (path-parts->key' "fhir" (split-path path))
      :min min
      :max max
      :spec-form (system-type->spec-form nil type)}
     (cond->
      {:key (path-parts->key' "fhir.xml" (split-path path))
       :modifier :xml
       :min min
       :max max
       :spec-form (system-type->spec-form (if rep :xmlAttr :xml) type)}
       rep
       (assoc :representation rep))]))

(defn- primitive-spec-defs [{:keys [path min max] [type] :type}]
  [{:key (path-parts->key' "fhir" (split-path path))
    :min min
    :max max
    :spec-form (keyword "fhir" (:code type))}
   {:key (path-parts->key' "fhir.xml" (split-path path))
    :modifier :xml
    :min min
    :max max
    :spec-form
    (case path
      ("Quantity.unit" "Coding.version" "Coding.display" "CodeableConcept.text")
      (xml/primitive-xml-form #"[\r\n\t\u0020-\uFFFF]+" `type/xml->InternedString)
      (keyword "fhir.xml" (:code type)))}])

(defn elem-def->spec-def
  "Takes an element definition and returns a coll of spec definitions."
  [{:keys [path min max contentReference] [type & more :as types] :type :as elem-def}]
  (cond
    more
    (let [path-parts (split-path (str/replace path "[x]" ""))]
      (into
       [{:key (path-parts->key' "fhir" path-parts)
         :min min
         :max max
         :spec-form (choice-spec-form path types)}]
       (mapcat
        (fn [{:keys [code]}]
          [(choice-spec-def :xml path path-parts code min max)]))
       types))
    type
    (if (str/starts-with? (:code type) "http://hl7.org/fhirpath/System")
      (system-spec-defs elem-def)
      (primitive-spec-defs elem-def))
    :else
    [{:key (path-parts->key' "fhir" (split-path path))
      :min min
      :max max
      :spec-form (path-parts->key' "fhir" (split-path (subs contentReference 1)))}
     {:key (path-parts->key' "fhir.xml" (split-path path))
      :modifier :xml
      :min min
      :max max
      :spec-form (path-parts->key' "fhir.xml" (split-path (subs contentReference 1)))}]))

(defn- spec-key [prefix parent-path-parts path-part]
  (keyword (str/join "." (cons prefix parent-path-parts)) path-part))

(defn- fix-fhir-type-extension* [extensions]
  (mapv
   (fn [{:keys [url] :as extension}]
     (cond-> extension
       (= "http://hl7.org/fhir/StructureDefinition/structuredefinition-fhir-type" url)
       (assoc :valueUrl "string")))
   extensions))

(defn- fix-fhir-type-extension [types]
  (mapv #(update % :extension fix-fhir-type-extension*) types))

(defn- fix-fhir-25274
  "https://jira.hl7.org/browse/FHIR-41183"
  [{:keys [path] :as elem-def}]
  (if (= "ElementDefinition.id" path)
    (update elem-def :type fix-fhir-type-extension)
    elem-def))

(defn ensure-coll [x]
  (cond (vector? x) x (some? x) [x]))

(defn- schema-spec-form [modifier child-spec-defs]
  `(s/schema
    ~(into
      {}
      (comp
       (filter :key)
       (filter #(= modifier (:modifier %)))
       (map
        (fn [{:keys [key max]}]
          [(keyword (name key))
           (if (= "1" max)
             key
             (if (= :xml modifier)
               `(s/and
                 (s/conformer ensure-coll identity)
                 (s/coll-of ~key))
               `(s/coll-of ~key)))])))
      child-spec-defs)))

(defn- record-spec-form [class-name child-spec-defs]
  `(specs/record
    ~(symbol (str "blaze.fhir.spec.type." class-name))
    ~(into
      {}
      (comp
       (filter :key)
       (filter #(nil? (:modifier %)))
       (map
        (fn [{:keys [key max]}]
          [(keyword (name key)) (if (= "1" max) key `(s/coll-of ~key))])))
      child-spec-defs)))

(defn- type-check-form [key]
  `(fn [~'m] (identical? ~key (type/type ~'m))))

(defn- internal-schema-spec-def [parent-path-parts path-part elem-def child-spec-defs]
  (let [key (spec-key "fhir" parent-path-parts path-part)]
    {:key key
     :min (:min elem-def)
     :max (:max elem-def)
     :spec-form
     (case key
       (:fhir/Attachment
        :fhir/Extension
        :fhir/Coding
        :fhir/CodeableConcept
        :fhir/CodeableReference
        :fhir/Quantity
        :fhir/Ratio
        :fhir/Period
        :fhir/Identifier
        :fhir/HumanName
        :fhir/Address
        :fhir/Reference
        :fhir/Meta)
       (record-spec-form path-part child-spec-defs)
       :fhir.Bundle.entry/search
       (record-spec-form "BundleEntrySearch" child-spec-defs)
       `(s/and ~(type-check-form key) ~(schema-spec-form nil child-spec-defs)))}))

(defn remove-choice-type
  "Removes the type suffix from the first key of a choice typed data element.

  Also removes bare properties with key `key` if no typed keys were found."
  [m typed-keys key]
  (loop [[k & keys] typed-keys]
    (if k
      (if-some [v (get m k)]
        (-> (dissoc m k) (assoc key v))
        (recur keys))
      (dissoc m key))))

(defn- choice-type-key [key type]
  (keyword (str (name key) (su/capital (name type)))))

(defn add-choice-type
  "Add the type suffix to the key of a choice typed data element."
  [m key]
  (if-some [v (get m key)]
    (-> (dissoc m key) (assoc (choice-type-key key (type/type v)) v))
    m))

(defn- remap-choice-conformer-form
  "Creates a conformer form which removes the type suffix of keys on conforming
  and adds it back on uniforming."
  [[internal-key child-spec-defs]]
  `(s/conformer
    (fn [~'m]
      (remove-choice-type ~'m ~(mapv (comp keyword name :key) child-spec-defs)
                          ~internal-key))
    (fn [~'m]
      (add-choice-type ~'m ~internal-key))))

(defn- remap-choice-conformer-forms [child-spec-defs]
  (into
   []
   (comp
    (remove (comp nil? first))
    (map remap-choice-conformer-form))
   (group-by :choice-group child-spec-defs)))

(defn- append-child [old element]
  (cond
    (vector? old)
    (conj old element)
    old
    [old element]
    :else
    element))

(defn conform-xml
  "First step in conforming an XML `element` into the internal form.

  Builds a map from child tags to either vector of children or single-valued
  children."
  {:arglists '([element])}
  [{:keys [attrs content]}]
  (transduce
   ;; remove mixed character content
   (filter xml/element?)
   (completing
    (fn [ret {:keys [tag] :as element}]
      (update ret (keyword (name tag)) append-child element)))
   (dissoc attrs :xmlns)
   content))

(defn select-non-nil-keys [m ks]
  (into {} (remove (comp nil? val)) (select-keys m ks)))

(defn- xml-attrs-form [child-spec-defs]
  `(select-non-nil-keys
    ~'m
    ~(into
      []
      (comp
       (filter :key)
       (filter :representation)
       (filter #(= :xml (:modifier %)))
       (map
        (fn [{:keys [key]}]
          (keyword (name key)))))
      child-spec-defs)))

(defn conj-when [coll x]
  (cond-> coll (some? x) (conj x)))

(defn conj-all [to tag from]
  (transduce (map #(assoc % :tag tag)) conj to from))

(defn- xml-unformer
  [kind type child-spec-defs]
  `(fn [~'m]
     (when ~'m
       (xml-node/element*
        ~(when (= "resource" kind) (keyword fhir-namespace (name type)))
        ~(if (= "resource" kind)
           `(assoc ~(xml-attrs-form child-spec-defs) :xmlns "http://hl7.org/fhir")
           (xml-attrs-form child-spec-defs))
        ~(seq
          (into
           [`-> []]
           (comp
            (filter :key)
            (remove :representation)
            (filter #(= :xml (:modifier %)))
            (map
             (fn [{:keys [key max]}]
               (let [key (keyword (name key))
                     tag (keyword fhir-namespace (name key))]
                 (cond
                   (or (and (= :entry type) (= :resource key))
                       (and (= :Narrative type) (= :div key)))
                   `(conj-when (~key ~'m))
                   (= "1" max)
                   `(conj-when (some-> ~'m ~key (assoc :tag ~tag)))
                   :else
                   `(conj-all ~tag (~key ~'m)))))))
           child-spec-defs))))))

(defn- xml-schema-spec-form [kind key child-spec-defs]
  (conj (seq (remap-choice-conformer-forms child-spec-defs))
        `(s/conformer (fn [~'m] (assoc ~'m :fhir/type ~key)) identity)
        (schema-spec-form :xml child-spec-defs)
        `(s/conformer conform-xml
                      ~(xml-unformer kind (keyword (name key)) child-spec-defs))
        `s/and))

(defn- special-xml-schema-spec-form [kind type-name child-spec-defs]
  (let [constructor-sym (symbol "blaze.fhir.spec.type" (str "map->" type-name))
        constructor (resolve constructor-sym)]
    (conj (seq (conj (remap-choice-conformer-forms child-spec-defs)
                     `(s/conformer ~constructor identity)))
          (schema-spec-form :xml child-spec-defs)
          `(s/conformer conform-xml
                        ~(xml-unformer kind (keyword type-name) child-spec-defs))
          `s/and)))

(defn- xml-schema-spec-def
  [kind parent-path-parts path-part elem-def child-spec-defs]
  (let [key (spec-key "fhir.xml" parent-path-parts path-part)]
    {:key key
     :min (:min elem-def)
     :max (:max elem-def)
     :modifier :xml
     :spec-form
     (case key
       (:fhir.xml/Attachment
        :fhir.xml/Extension
        :fhir.xml/Coding
        :fhir.xml/CodeableConcept
        :fhir.xml/CodeableReference
        :fhir.xml/Quantity
        :fhir.xml/Ratio
        :fhir.xml/Period
        :fhir.xml/Identifier
        :fhir.xml/HumanName
        :fhir.xml/Address
        :fhir.xml/Reference
        :fhir.xml/Meta)
       (special-xml-schema-spec-form kind (name key) child-spec-defs)
       :fhir.xml.Bundle.entry/search
       (special-xml-schema-spec-form kind "BundleEntrySearch" child-spec-defs)
       (xml-schema-spec-form kind (spec-key "fhir" parent-path-parts path-part)
                             child-spec-defs))}))

(defn- build-spec-defs [kind parent-path-parts indexed-elem-defs]
  (into
   []
   (comp
    (filter (comp string? first))
    (mapcat
     (fn [[path-part coll-or-elem-def]]
       (if (map? coll-or-elem-def)
         (elem-def->spec-def (fix-fhir-25274 coll-or-elem-def))
         (let [elem-def (ffirst coll-or-elem-def)
               child-spec-defs (build-spec-defs "backbone-element" (conj parent-path-parts path-part) coll-or-elem-def)]
           [child-spec-defs
            (internal-schema-spec-def parent-path-parts path-part elem-def child-spec-defs)
            (xml-schema-spec-def kind parent-path-parts path-part elem-def child-spec-defs)])))))
   indexed-elem-defs))

(defn index-by-path* [elem-defs]
  (into
   []
   (comp
    (partition-by first)
    (map
     (fn [xs]
       (if (< 1 (count xs))
         [(ffirst xs) (index-by-path* (map rest xs))]
         (first xs)))))
   elem-defs))

(defn- index-by-path [elem-defs]
  (index-by-path*
   (map
    (fn [{:keys [path] :as elem-def}]
      (conj (split-path path) elem-def))
    elem-defs)))

(defn struct-def->spec-def [{:keys [kind] {elem-defs :element} :snapshot}]
  (flatten (build-spec-defs kind [] (index-by-path elem-defs))))

(defn- internal-pred [name]
  (case name
    "boolean" `type/boolean?
    "integer" `type/integer?
    "integer64" `type/integer64?
    "string" `type/string?
    "decimal" `type/decimal?
    "uri" `type/uri?
    "url" `type/url?
    "canonical" `type/canonical?
    "base64Binary" `type/base64Binary?
    "instant" `type/instant?
    "date" `type/date?
    "dateTime" `type/dateTime?
    "time" `type/time?
    "code" `type/code?
    "oid" `type/oid?
    "id" `type/id?
    "markdown" `type/markdown?
    "unsignedInt" `type/unsignedInt?
    "positiveInt" `type/positiveInt?
    "uuid" `type/uuid?
    "xhtml" `type/xhtml?
    (throw (ex-info (format "Unknown primitive type `%s`." name) {}))))

(defn- value-type [element]
  (some #(when (str/ends-with? (:path %) "value") (first (:type %))) element))

(defn- xml-spec-form [name {:keys [element]}]
  (let [pattern (type-regex (value-type element))
        constructor (str "xml->" (su/capital name))]
    (case name
      "string" (xml/primitive-xml-form #"[\r\n\t\u0020-\uFFFF]+" `type/xml->String)
      "uri" (xml/primitive-xml-form #"[\u0021-\uFFFF]*" `type/xml->Uri)
      "url" (xml/primitive-xml-form #"[\u0021-\uFFFF]*" `type/xml->Url)
      "canonical" (xml/primitive-xml-form #"[\u0021-\uFFFF]*" `type/xml->Canonical)
      "code" (xml/primitive-xml-form #"[\u0021-\uFFFF]+([ \t\n\r][\u0021-\uFFFF]+)*" `type/xml->Code)
      "markdown" (xml/primitive-xml-form #"[\r\n\t\u0020-\uFFFF]+" `type/xml->Markdown)
      "decimal" (xml/primitive-xml-form #"-?(0|[1-9][0-9]{0,17})(\.[0-9]{1,17})?([eE][+-]?[0-9]{1,9})?" `type/xml->Decimal)
      "xhtml" `(s/and xml/element? (s/conformer type/xml->Xhtml type/to-xml))
      (xml/primitive-xml-form pattern (symbol "blaze.fhir.spec.type" constructor)))))

(defn primitive-type->spec-defs
  "Converts a primitive type structure definition into spec defs for XML and
   internal representation."
  [{:keys [name snapshot]}]
  [{:key (keyword "fhir" name) :spec-form (internal-pred name)}
   {:key (keyword "fhir.xml" name) :spec-form (xml-spec-form name snapshot)}])

(defn- resolve-spec [spec-form]
  (if (keyword? spec-form) spec-form (s/resolve-spec spec-form)))

(defn register
  "Registers `spec-defs`"
  [spec-defs]
  (run!
   (fn [{:keys [key spec-form]}]
     (s/register key (resolve-spec spec-form)))
   spec-defs))

(defmulti xml-resource (constantly :default))

(defmethod xml-resource :default [{:keys [tag] :fhir/keys [type]}]
  (->> (or tag type) name (keyword "fhir.xml")))

(defn conform-xml-resource [{:keys [content]}]
  (or (some #(when (xml/element? %) %) content) ::s/invalid))

(defn unform-xml-resource [resource]
  (xml-node/element ::f/resource {} resource))

(s/def :fhir.xml/Resource
  (s/and (s/conformer conform-xml-resource unform-xml-resource)
         (s/multi-spec xml-resource (fn [value _] value))))

(defmulti resource (constantly :default))

(defmethod resource :default [{:fhir/keys [type]}]
  (when type
    (keyword "fhir" (name type))))

(s/def :fhir/Resource
  (s/multi-spec resource :fhir/type))

;; should be 15992
(comment (count (keys (s/registry))))
