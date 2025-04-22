(ns blaze.fhir.spec.impl
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.fhir.spec.impl.intern :as intern]
   [blaze.fhir.spec.impl.specs :as specs]
   [blaze.fhir.spec.impl.util :as u]
   [blaze.fhir.spec.impl.xml :as xml]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.string-util :as su]
   [clojure.alpha.spec :as s]
   [clojure.data.xml.name :as xml-name]
   [clojure.data.xml.node :as xml-node]
   [clojure.string :as str])
  (:import
   [com.github.benmanes.caffeine.cache CacheLoader Caffeine LoadingCache]
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
    (:json nil) `(s/and string? ~id-matcher-form)
    :xml `(s/and xml/element? (s/conformer conform-xml-value unform-xml-value) ~id-matcher-form)
    :cbor `any?))

(defn uri-string-spec [modifier]
  (case modifier
    (:json nil :xmlAttr) `(s/and string? ~uri-matcher-form)
    :xml `(s/and xml/element? (s/conformer conform-xml-value unform-xml-value) ~uri-matcher-form)
    :cbor `(s/conformer intern-string)))

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
    (identical? :json modifier)
    (assoc :choice-group (keyword (last path-parts)))))

(defn- system-spec-defs [{:keys [path min max representation] [type] :type}]
  (let [rep (some-> representation first keyword)]
    [{:key (path-parts->key' "fhir" (split-path path))
      :min min
      :max max
      :spec-form (system-type->spec-form nil type)}
     {:key (path-parts->key' "fhir.json" (split-path path))
      :modifier :json
      :min min
      :max max
      :spec-form (system-type->spec-form :json type)}
     (cond->
      {:key (path-parts->key' "fhir.xml" (split-path path))
       :modifier :xml
       :min min
       :max max
       :spec-form (system-type->spec-form (if rep :xmlAttr :xml) type)}
       rep
       (assoc :representation rep))
     {:key (path-parts->key' "fhir.cbor" (split-path path))
      :modifier :cbor
      :min min
      :max max
      :spec-form (system-type->spec-form :cbor type)}]))

(defn- primitive-spec-defs [{:keys [path min max representation] [type] :type}]
  (let [rep (some-> representation first keyword)]
    [{:key (path-parts->key' "fhir" (split-path path))
      :min min
      :max max
      :spec-form (keyword "fhir" (:code type))}
     {:key (path-parts->key' "fhir.json" (split-path path))
      :modifier :json
      :min min
      :max max
      :spec-form
      (case path
        ("Quantity.unit" "Coding.version" "Coding.display" "CodeableConcept.text")
        `(specs/json-regex-primitive #"[\r\n\t\u0020-\uFFFF]+" type/intern-string)
        (keyword "fhir.json" (:code type)))}
     (cond->
      {:key (path-parts->key' "fhir.xml" (split-path path))
       :modifier :xml
       :min min
       :max max
        ;; it's a bit of an hack to use the JSON spec here, but it works for
        ;; string based values in XML attributes
       :spec-form
       (case path
         ("Quantity.unit" "Coding.version" "Coding.display" "CodeableConcept.text")
         (xml/primitive-xml-form #"[\r\n\t\u0020-\uFFFF]+" `type/xml->InternedString)
         (keyword (if rep "fhir.json" "fhir.xml") (:code type)))}
       rep
       (assoc :representation rep))
     {:key (path-parts->key' "fhir.cbor" (split-path path))
      :modifier :cbor
      :min min
      :max max
      :spec-form
      (case path
        ("Quantity.unit" "Coding.version" "Coding.display" "CodeableConcept.text")
        `(specs/cbor-primitive type/intern-string)
        (keyword "fhir.cbor" (:code type)))}]))

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
          [(choice-spec-def :json path path-parts code min max)
           (choice-spec-def :xml path path-parts code min max)
           (choice-spec-def :cbor path path-parts code min max)]))
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
     {:key (path-parts->key' "fhir.json" (split-path path))
      :modifier :json
      :min min
      :max max
      :spec-form (path-parts->key' "fhir.json" (split-path (subs contentReference 1)))}
     {:key (path-parts->key' "fhir.xml" (split-path path))
      :modifier :xml
      :min min
      :max max
      :spec-form (path-parts->key' "fhir.xml" (split-path (subs contentReference 1)))}
     {:key (path-parts->key' "fhir.cbor" (split-path path))
      :modifier :cbor
      :min min
      :max max
      :spec-form (path-parts->key' "fhir.cbor" (split-path (subs contentReference 1)))}]))

(defn- spec-key [prefix parent-path-parts path-part]
  (keyword (str/join "." (cons prefix parent-path-parts)) path-part))

(defn- fix-fhir-type-extension* [extensions]
  (mapv
   (fn [{:keys [url] :as extension}]
     (cond-> extension
       (= "http://hl7.org/fhir/StructureDefinition/structuredefinition-fhir-type" url)
       (assoc :valueUrl "id")))
   extensions))

(defn- fix-fhir-type-extension [types]
  (mapv #(update % :extension fix-fhir-type-extension*) types))

(defn- fix-fhir-25274
  "https://jira.hl7.org/browse/FHIR-25274"
  [{{base-path :path} :base :as elem-def}]
  (if (= "Resource.id" base-path)
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
             (if (#{:json :xml} modifier)
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

(defn- type-annotating-conformer-form [key]
  `(s/conformer
    (fn [~'m] (assoc ~'m :fhir/type ~key))
    (fn [~'m] (dissoc ~'m :fhir/type))))

(defn- resource-type-annotating-conformer-form [type]
  `(s/conformer
    (fn [~'m]
      (-> (assoc ~'m :fhir/type ~(keyword "fhir" type))
          (dissoc :resourceType)))
    (fn [~'m] (-> (dissoc ~'m :fhir/type) (assoc :resourceType ~type)))))

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

(def ^:private choice-type-key-cache
  (-> (Caffeine/newBuilder)
      (.build
       (reify CacheLoader
         (load [_ [key type]]
           (keyword (str (name key) (su/capital (name type)))))))))

(defn- choice-type-key [key type]
  (.get ^LoadingCache choice-type-key-cache [key type]))

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

(defn- json-type-conformer-form [kind parent-path-parts path-part]
  (if (= "resource" kind)
    (resource-type-annotating-conformer-form path-part)
    (type-annotating-conformer-form (spec-key "fhir" parent-path-parts path-part))))

(defn- key-map [child-spec-defs]
  (into
   {}
   (comp
    (remove (comp nil? first))
    (mapcat
     (fn [[internal-key child-spec-defs]]
       (mapv (comp #(vector % internal-key) keyword name :key) child-spec-defs))))
   (group-by :choice-group child-spec-defs)))

(defn- json-object-spec-form [create-fn child-spec-defs]
  `(specs/json-object
    ~(symbol "blaze.fhir.spec.type" create-fn)
    ~(into
      {}
      (comp
       (filter :key)
       (filter #(= :json (:modifier %)))
       (map
        (fn [{:keys [key max]}]
          [(keyword (name key)) (if (= "1" max) key `(s/coll-of ~key))])))
      child-spec-defs)
    ~(key-map child-spec-defs)))

(defn- json-schema-spec-def [kind parent-path-parts path-part elem-def child-spec-defs]
  (let [key (spec-key "fhir.json" parent-path-parts path-part)]
    {:key key
     :min (:min elem-def)
     :max (:max elem-def)
     :modifier :json
     :spec-form
     (case key
       (:fhir.json/Extension
        :fhir.json/Coding
        :fhir.json/CodeableConcept
        :fhir.json/Meta
        :fhir.json/Attachment
        :fhir.json/Quantity
        :fhir.json/Ratio
        :fhir.json/Period
        :fhir.json/Identifier
        :fhir.json/HumanName
        :fhir.json/Address
        :fhir.json/Reference)
       (json-object-spec-form (su/pascal->kebab path-part) child-spec-defs)
       :fhir.json.Bundle.entry/search
       (json-object-spec-form "bundle-entry-search" child-spec-defs)
       (conj (seq (remap-choice-conformer-forms child-spec-defs))
             (json-type-conformer-form kind parent-path-parts path-part)
             (schema-spec-form :json child-spec-defs)
             `(s/conformer u/update-extended-primitives identity)
             `s/and))}))

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

(defn- cbor-object-spec-form [create-fn child-spec-defs]
  `(specs/json-object
    ~(symbol "blaze.fhir.spec.type" create-fn)
    ~(into
      {}
      (comp
       (filter :key)
       (filter #(= :cbor (:modifier %)))
       (map
        (fn [{:keys [key max]}]
          [(keyword (name key)) (if (= "1" max) key `(s/coll-of ~key))])))
      child-spec-defs)
    ~(key-map child-spec-defs)))

(defn- cbor-schema-spec-def
  [kind parent-path-parts path-part elem-def child-spec-defs]
  (let [key (spec-key "fhir.cbor" parent-path-parts path-part)]
    {:key key
     :min (:min elem-def)
     :max (:max elem-def)
     :modifier :cbor
     :spec-form
     (case key
       (:fhir.cbor/Extension
        :fhir.cbor/Coding
        :fhir.cbor/CodeableConcept
        :fhir.cbor/Meta
        :fhir.cbor/Attachment
        :fhir.cbor/Quantity
        :fhir.cbor/Ratio
        :fhir.cbor/Period
        :fhir.cbor/Identifier
        :fhir.cbor/HumanName
        :fhir.cbor/Address
        :fhir.cbor/Reference)
       (cbor-object-spec-form (su/pascal->kebab path-part) child-spec-defs)
       :fhir.cbor.Bundle.entry/search
       (cbor-object-spec-form "bundle-entry-search" child-spec-defs)
       (conj (seq (remap-choice-conformer-forms child-spec-defs))
             (json-type-conformer-form kind parent-path-parts path-part)
             (schema-spec-form :cbor child-spec-defs)
             `(s/conformer u/update-extended-primitives identity)
             `s/and))}))

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
            (json-schema-spec-def kind parent-path-parts path-part elem-def child-spec-defs)
            (xml-schema-spec-def kind parent-path-parts path-part elem-def child-spec-defs)
            (cbor-schema-spec-def kind parent-path-parts path-part elem-def child-spec-defs)])))))
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

(defn decimal-or-int? [x]
  (or (decimal? x) (int? x)))

(defn- json-spec-form [name {:keys [element]}]
  (let [pattern (type-regex (value-type element))]
    (case name
      "boolean" `(specs/json-pred-primitive boolean? type/boolean)
      "integer" `(specs/json-pred-primitive int? type/integer)
      "string" `(specs/json-regex-primitive #"[\r\n\t\u0020-\uFFFF]+" type/string)
      "decimal" `(specs/json-pred-primitive decimal-or-int? type/decimal)
      "uri" `(specs/json-regex-primitive #"[\u0021-\uFFFF]*" type/uri)
      "url" `(specs/json-regex-primitive #"[\u0021-\uFFFF]*" type/url)
      "canonical" `(specs/json-regex-primitive #"[\u0021-\uFFFF]*" type/canonical)
      "base64Binary" `(specs/json-regex-primitive ~pattern type/base64Binary)
      "instant" `(specs/json-regex-primitive ~pattern type/instant)
      "date" `(specs/json-regex-primitive ~pattern type/date)
      "dateTime" `(specs/json-regex-primitive ~pattern type/dateTime)
      "time" `(specs/json-regex-primitive ~pattern type/time)
      "code" `(specs/json-regex-primitive #"[\u0021-\uFFFF]+([ \t\n\r][\u0021-\uFFFF]+)*" type/code)
      "oid" `(specs/json-regex-primitive ~pattern type/oid)
      "id" `(specs/json-regex-primitive ~pattern type/id)
      "markdown" `(specs/json-regex-primitive #"[\r\n\t\u0020-\uFFFF]+" type/markdown)
      "unsignedInt" `(specs/json-pred-primitive int? type/unsignedInt)
      "positiveInt" `(specs/json-pred-primitive int? type/positiveInt)
      "uuid" `(specs/json-regex-primitive ~pattern type/uuid)
      "xhtml" `(s/and string? (s/conformer type/->Xhtml identity))
      (throw (ex-info (format "Unknown primitive type `%s`." name) {})))))

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
      "xhtml" `(s/and xml/element? (s/conformer type/xml->Xhtml type/to-xml))
      (xml/primitive-xml-form pattern (symbol "blaze.fhir.spec.type" constructor)))))

(defn- cbor-spec-form [name _]
  (case name
    "boolean" `(specs/cbor-primitive type/boolean)
    "integer" `(specs/cbor-primitive type/integer)
    "string" `(specs/cbor-primitive type/string)
    "decimal" `(specs/cbor-primitive type/decimal)
    "uri" `(specs/cbor-primitive type/uri)
    "url" `(specs/cbor-primitive type/url)
    "canonical" `(specs/cbor-primitive type/canonical)
    "base64Binary" `(specs/cbor-primitive type/base64Binary)
    "instant" `(specs/cbor-primitive type/instant)
    "date" `(specs/cbor-primitive type/date)
    "dateTime" `(specs/cbor-primitive type/dateTime)
    "time" `(specs/cbor-primitive type/time)
    "code" `(specs/cbor-primitive type/code)
    "oid" `(specs/cbor-primitive type/oid)
    "id" `(specs/cbor-primitive type/id)
    "markdown" `(specs/cbor-primitive type/markdown)
    "unsignedInt" `(specs/cbor-primitive type/unsignedInt)
    "positiveInt" `(specs/cbor-primitive type/positiveInt)
    "uuid" `(specs/cbor-primitive type/uuid)
    "xhtml" `(s/conformer type/->Xhtml identity)
    (throw (ex-info (format "Unknown primitive type `%s`." name) {}))))

(defn primitive-type->spec-defs
  "Converts a primitive type structure definition into spec defs for JSON and
   internal representation."
  [{:keys [name snapshot]}]
  [{:key (keyword "fhir" name) :spec-form (internal-pred name)}
   {:key (keyword "fhir.json" name) :spec-form (json-spec-form name snapshot)}
   {:key (keyword "fhir.xml" name) :spec-form (xml-spec-form name snapshot)}
   {:key (keyword "fhir.cbor" name) :spec-form (cbor-spec-form name snapshot)}])

(defn- resolve-spec [spec-form]
  (if (keyword? spec-form) spec-form (s/resolve-spec spec-form)))

(defn register
  "Registers `spec-defs`"
  [spec-defs]
  (run!
   (fn [{:keys [key spec-form]}]
     (s/register key (resolve-spec spec-form)))
   spec-defs))

;; Resource Spec
(defmulti json-resource (constantly :default))

(defmethod json-resource :default [{json-type :resourceType :fhir/keys [type]}]
  (when-let [type (or json-type (some-> type name))]
    (keyword "fhir.json" type)))

(s/def :fhir.json/Resource
  (s/multi-spec json-resource (fn [value tag] (assoc value :resourceType tag))))

(defn conform-cbor-resource [{type :resourceType :as resource}]
  (s/conform (keyword "fhir.cbor" type) resource))

(defn unform-cbor-resource [{:fhir/keys [type] :as resource}]
  (s/unform (keyword "fhir.cbor" (name type)) resource))

(s/def :fhir.cbor/Resource
  (s/conformer conform-cbor-resource unform-cbor-resource))

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

;; should be 32784
(comment (count (keys (s/registry))))
