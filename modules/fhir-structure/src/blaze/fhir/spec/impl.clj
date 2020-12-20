(ns blaze.fhir.spec.impl
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.fhir.spec.type :as type]
    [blaze.fhir.util :as u]
    [clojure.alpha.spec :as s]
    [clojure.data.xml.name :as xml-name]
    [clojure.data.xml.node :as xml-node]
    [cognitect.anomalies :as anom]
    [cuerdas.core :as str])
  (:import
    [java.net URLEncoder]
    [java.nio.charset StandardCharsets]
    [clojure.data.xml.node Element]))


(xml-name/alias-uri 'f "http://hl7.org/fhir")


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
  `(fn [~'s] (re-matches #"[A-Za-z0-9\-\.]{1,64}" ~'s)))


(def uri-matcher-form
  `(fn [~'s] (re-matches #"\S*" ~'s)))


(defn element? [x]
  (instance? Element x))


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
    :xml `(s/and element? (s/conformer conform-xml-value unform-xml-value) ~id-matcher-form)
    :cbor `any?))


(defn uri-string-spec [modifier]
  (case modifier
    (:json nil) `(s/and string? ~uri-matcher-form)
    :xml `(s/and element? (s/conformer conform-xml-value unform-xml-value) ~uri-matcher-form)
    :xmlAttr `(s/and string? ~uri-matcher-form)
    :cbor `any?))


(defn- string-spec [modifier type]
  (case (find-fhir-type type)
    "id" (id-string-spec modifier)
    "uri" (uri-string-spec modifier)
    (if-let [regex (type-regex type)]
      `(s/and string? (fn [~'s] (re-matches ~regex ~'s)))
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
    (throw-anom ::anom/unsupported (format "Unsupported system type `%s`." code))))



(defn- split-path [path]
  (str/split path #"\."))


(defn- key-name [last-path-part {:keys [code]}]
  (if (str/ends-with? last-path-part "[x]")
    (str/replace last-path-part "[x]" (str/capital code))
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


(defn- choice-spec-def [modifier path path-parts code min max]
  (cond->
    {:key (path-parts->key' (str "fhir." (name modifier)) (split-path (str/replace path "[x]" (str/capital code))))
     :modifier modifier
     :min min
     :max max
     :spec-form (keyword (str "fhir." (name modifier)) code)}
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
      :spec-form (keyword "fhir.json" (:code type))}
     (cond->
       {:key (path-parts->key' "fhir.xml" (split-path path))
        :modifier :xml
        :min min
        :max max
        ;; it's a bit of an hack to use the JSON spec here, but it works for
        ;; string based values in XML attributes
        :spec-form (keyword (if rep "fhir.json" "fhir.xml") (:code type))}
       rep
       (assoc :representation rep))
     {:key (path-parts->key' "fhir.cbor" (split-path path))
      :modifier :cbor
      :min min
      :max max
      :spec-form (keyword "fhir.cbor" (:code type))}]))


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


(defn- type-check-form [key]
  `(fn [~'m] (identical? ~key (type/type ~'m))))


(defn- internal-schema-spec-def [parent-path-parts path-part elem-def child-spec-defs]
  (let [key (spec-key "fhir" parent-path-parts path-part)]
    {:key key
     :min (:min elem-def)
     :max (:max elem-def)
     :spec-form `(s/and ~(type-check-form key) ~(schema-spec-form nil child-spec-defs))}))


(defn- type-annotating-conformer-form [key]
  `(s/conformer
     (fn [~'m] (assoc ~'m :fhir/type ~key))
     (fn [~'m] (dissoc ~'m :fhir/type))))


(defn- resource-type-annotating-conformer-form [type]
  `(s/conformer
     (fn [~'m] (-> (assoc ~'m :fhir/type ~(keyword "fhir" type)) (dissoc :resourceType)))
     (fn [~'m] (-> (dissoc ~'m :fhir/type) (assoc :resourceType ~type)))))


(defn remove-choice-type
  "Removes the type suffix from the first key of a choice typed data element."
  [m typed-keys key]
  (loop [[k & keys] typed-keys]
    (if k
      (if-some [v (get m k)]
        (-> (dissoc m k) (assoc key v))
        (recur keys))
      m)))


(defn- type-suffix [x]
  (if-let [type (type/type x)]
    (str/capital (name type))
    (throw (ex-info (format "Unknown type of `%s`." x) {:x x}))))


(defn add-choice-type
  "Add the type suffix to the key of a choice typed data element."
  [m key]
  (if-some [v (get m key)]
    (-> (dissoc m key) (assoc (keyword (str (name key) (type-suffix v))) v))
    m))


(defn- remap-choice-conformer-form
  "Creates a conformer form which removes the type suffix of keys on conforming
  and adds it back on unforming."
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
  (cond
    (= "resource" kind)
    (resource-type-annotating-conformer-form path-part)
    :else
    (type-annotating-conformer-form (spec-key "fhir" parent-path-parts path-part))))


(defn- json-schema-spec-def [kind parent-path-parts path-part elem-def child-spec-defs]
  {:key (spec-key "fhir.json" parent-path-parts path-part)
   :min (:min elem-def)
   :max (:max elem-def)
   :modifier :json
   :spec-form
   (conj (seq (remap-choice-conformer-forms child-spec-defs))
         (json-type-conformer-form kind parent-path-parts path-part)
         (schema-spec-form :json child-spec-defs)
         `s/and)})


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

  Builds a map from child tags to either vector of childs or single-valued
  childs."
  {:arglists '([type element])}
  [type {:keys [attrs content]}]
  (transduce
    ;; remove mixed character content
    (filter element?)
    (completing
      (fn [ret {:keys [tag] :as element}]
        (update ret (keyword (name tag)) append-child element)))
    (-> (dissoc attrs :xmlns) (assoc :fhir/type type))
    content))


(defn xml-attrs-form [child-spec-defs]
  `(select-keys
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
            child-spec-defs)))))


(defn- xml-schema-spec-form [kind key child-spec-defs]
  (conj (seq (remap-choice-conformer-forms child-spec-defs))
        (schema-spec-form :xml child-spec-defs)
        `(s/conformer (fn [~'e] (conform-xml ~key ~'e))
                      ~(xml-unformer kind (keyword (name key)) child-spec-defs))
        `s/and))


(defn- xml-schema-spec-def [kind parent-path-parts path-part elem-def child-spec-defs]
  {:key (spec-key "fhir.xml" parent-path-parts path-part)
   :min (:min elem-def)
   :max (:max elem-def)
   :modifier :xml
   :spec-form (xml-schema-spec-form kind (spec-key "fhir" parent-path-parts path-part) child-spec-defs)})


(defn- cbor-schema-spec-def [kind parent-path-parts path-part elem-def child-spec-defs]
  {:key (spec-key "fhir.cbor" parent-path-parts path-part)
   :min (:min elem-def)
   :max (:max elem-def)
   :modifier :cbor
   :spec-form
   (conj (seq (remap-choice-conformer-forms child-spec-defs))
         (json-type-conformer-form kind parent-path-parts path-part)
         (schema-spec-form :cbor child-spec-defs)
         `s/and)})


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


(defn- to-predefine-spec-def [{:keys [key] :as spec-def}]
  (when (#{"fhir" "fhir.json"} (namespace key))
    [(assoc spec-def :spec-form `any?)]))


(defn- internal-spec-form [name]
  (case name
    "boolean" `boolean?
    "integer" `(fn [~'x] (instance? Integer ~'x))
    "string" `type/string?
    "decimal" `type/decimal?
    "uri" `type/uri?
    "url" `type/url?
    "canonical" `type/canonical?
    "base64Binary" `type/base64Binary?
    "instant" `type/instant?
    "date" `type/date?
    "dateTime" `type/date-time?
    "time" `type/time?
    "code" `type/code?
    "oid" `type/oid?
    "id" `type/id?
    "markdown" `type/markdown?
    "unsignedInt" `type/unsignedInt?
    "positiveInt" `type/positiveInt?
    "uuid" `uuid?
    "xhtml" `type/xhtml?
    (throw (ex-info (format "Unknown primitive type `%s`." name) {}))))


(defn- value-type [element]
  (some #(when (str/ends-with? (:path %) "value") (first (:type %))) element))


(defn- re-matches-form-json [regex]
  `(fn [~'s] (re-matches ~regex ~'s)))


(defn conform-decimal-json [x]
  (cond (int? x) (BigDecimal/valueOf ^long x) (decimal? x) x :else ::s/invalid))


(defn- json-spec-form [name {:keys [element]}]
  (let [regex (type-regex (value-type element))]
    (case name
      "boolean" `boolean?
      "integer" `(s/and int? (s/conformer int type/to-json))
      "string" `(s/and string? ~(re-matches-form-json regex) (s/conformer identity type/to-json))
      "decimal" `(s/conformer conform-decimal-json identity)
      "uri" `(s/and string? ~(re-matches-form-json regex) (s/conformer type/->Uri type/to-json))
      "url" `(s/and string? ~(re-matches-form-json regex) (s/conformer type/->Url type/to-json))
      "canonical" `(s/and string? ~(re-matches-form-json regex) (s/conformer type/->Canonical type/to-json))
      "base64Binary" `(s/and string? ~(re-matches-form-json regex) (s/conformer type/->Base64Binary type/to-json))
      "instant" `(s/and string? ~(re-matches-form-json regex) (s/conformer type/->Instant type/to-json))
      "date" `(s/and string? ~(re-matches-form-json regex) (s/conformer type/->Date type/to-json))
      "dateTime" `(s/and string? ~(re-matches-form-json regex) (s/conformer type/->DateTime type/to-json))
      "time" `(s/and string? ~(re-matches-form-json regex) (s/conformer type/->Time type/to-json))
      "code" `(s/and string? ~(re-matches-form-json regex) (s/conformer type/->Code type/to-json))
      "oid" `(s/and string? ~(re-matches-form-json regex) (s/conformer type/->Oid type/to-json))
      "id" `(s/and string? ~(re-matches-form-json regex) (s/conformer type/->Id type/to-json))
      "markdown" `(s/and string? ~(re-matches-form-json regex) (s/conformer type/->Markdown type/to-json))
      "unsignedInt" `(s/and int? (s/conformer type/->UnsignedInt type/to-json))
      "positiveInt" `(s/and int? (s/conformer type/->PositiveInt type/to-json))
      "uuid" `(s/and string? ~(re-matches-form-json regex) (s/conformer type/->Uuid type/to-json))
      "xhtml" `(s/and string? (s/conformer type/->Xhtml type/to-json))
      (throw (ex-info (format "Unknown primitive type `%s`." name) {})))))


(defn xml-value-matches?
  {:arglists '([regex element])}
  [regex {{:keys [value]} :attrs}]
  (and (string? value) (.matches (re-matcher regex value))))


(defn set-extension-tag [element]
  (update element :content (partial map #(assoc % :tag ::f/extension))))


(defn- primitive-xml-form [regex constructor]
  `(s/and
     element?
     (fn [~'e] (xml-value-matches? ~regex ~'e))
     (s/conformer identity set-extension-tag)
     (s/schema {:content (s/coll-of :fhir.xml/Extension)})
     (s/conformer ~constructor type/to-xml)))


(defn- xml-spec-form [name {:keys [element]}]
  (let [regex (type-regex (value-type element))
        constructor (str "xml->" (str/capital name))]
    (case name
      "xhtml" `(s/and element? (s/conformer type/xml->Xhtml type/to-xml))
      (primitive-xml-form regex (symbol "blaze.fhir.spec.type" constructor)))))


(defn- cbor-spec-form [name _]
  (case name
    "boolean" `any?
    "integer" `(s/conformer int type/to-json)
    "string" `any?
    "decimal" `any?
    "uri" `(s/conformer type/->Uri type/to-json)
    "url" `(s/conformer type/->Url type/to-json)
    "canonical" `(s/conformer type/->Canonical type/to-json)
    "base64Binary" `(s/conformer type/->Base64Binary type/to-json)
    "instant" `(s/conformer type/->Instant type/to-json)
    "date" `(s/conformer type/->Date type/to-json)
    "dateTime" `(s/conformer type/->DateTime type/to-json)
    "time" `(s/conformer type/->Time type/to-json)
    "code" `(s/conformer type/->Code type/to-json)
    "oid" `(s/conformer type/->Oid type/to-json)
    "id" `(s/conformer type/->Id type/to-json)
    "markdown" `(s/conformer type/->Markdown type/to-json)
    "unsignedInt" `(s/conformer type/->UnsignedInt type/to-json)
    "positiveInt" `(s/conformer type/->PositiveInt type/to-json)
    "uuid" `(s/conformer type/->Uuid type/to-json)
    "xhtml" `(s/conformer type/->Xhtml type/to-json)
    (throw (ex-info (format "Unknown primitive type `%s`." name) {}))))


(defn primitive-type->spec-defs
  "Converts a primitive type structure definition into spec defs for JSON and
   internal representation."
  [{:keys [name snapshot]}]
  [{:key (keyword "fhir" name) :spec-form (internal-spec-form name)}
   {:key (keyword "fhir.json" name) :spec-form (json-spec-form name snapshot)}
   {:key (keyword "fhir.xml" name) :spec-form (xml-spec-form name snapshot)}
   {:key (keyword "fhir.cbor" name) :spec-form (cbor-spec-form name snapshot)}])


(defn- register
  "Registers `spec-defs`"
  [spec-defs]
  (doseq [{:keys [key spec-form]} spec-defs]
    (s/register key (if (keyword? spec-form) spec-form (s/resolve-spec spec-form)))))


;; register all primitive type specs without id and extension
(register (mapcat primitive-type->spec-defs (u/primitive-types)))


;; preregister all complex type specs with any? because there are circles which
;; cause spec registration to fail
(register (mapcat to-predefine-spec-def (mapcat struct-def->spec-def (u/complex-types))))


;; register all complex type specs
(register (mapcat struct-def->spec-def (u/complex-types)))


;; Resource Spec
(defmulti json-resource (constantly :default))


(defmethod json-resource :default [{json-type :resourceType :fhir/keys [type]}]
  (keyword "fhir.json" (or json-type (name type))))


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
  (some #(when (element? %) %) content))


(defn unform-xml-resource [resource]
  (xml-node/element ::f/resource {} resource))


(s/def :fhir.xml/Resource
  (s/and (s/conformer conform-xml-resource unform-xml-resource)
         (s/multi-spec xml-resource (fn [value _] value))))


;; register all resource specs
(defn register-resources [spec-defs]
  (register spec-defs)
  (s/register
    :fhir/Resource
    (s/resolve-spec
      `(s/or
         ~@(into
             []
             (comp
               (map :key)
               (filter (comp #{"fhir"} namespace))
               (mapcat #(vector (keyword (name %)) %)))
             spec-defs)))))


(register-resources (mapcat struct-def->spec-def (remove :abstract (u/resources))))


;; should be 32564
(comment (count (keys (s/registry))))
