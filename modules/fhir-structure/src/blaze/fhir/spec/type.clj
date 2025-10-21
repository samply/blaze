(ns blaze.fhir.spec.type
  "Functions for primitive and complex types."
  (:refer-clojure
   :exclude
   [boolean boolean? decimal? integer? long meta range str string? time type uri? uuid?])
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-string]
   [blaze.fhir.spec.type.protocols :as p]
   [blaze.fhir.spec.type.string-util :as su]
   [blaze.util :refer [str]]
   [clojure.data.xml :as xml]
   [clojure.data.xml.name :as xml-name]
   [clojure.data.xml.node :as xml-node]
   [clojure.string :as str]
   [cognitect.anomalies :as anom])
  (:import
   [blaze.fhir.spec.type
    Address Annotation Attachment Base Base64Binary BundleEntrySearch Canonical Code
    CodeableConcept Coding ContactDetail ContactPoint Date DateTime Decimal
    Expression Extension HumanName Id Identifier Instant Markdown Meta Oid
    Period PositiveInt Primitive Quantity Range Ratio Reference RelatedArtifact String$Interned String$Normal Time
    UnsignedInt Uri Uri$Interned Uri$Normal Url Uuid Xhtml]
   [clojure.lang IPersistentMap Keyword]
   [com.google.common.hash PrimitiveSink]
   [java.io Writer]
   [java.time LocalTime OffsetDateTime]
   [java.util Comparator List Map$Entry]))

(xml-name/alias-uri 'f "http://hl7.org/fhir")

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn type
  "Returns the FHIR type if `x` if it has some."
  [x]
  (or (and (instance? Base x) (.fhirType ^Base x)) (:fhir/type x)))

(defn value
  "Returns the possible value of the primitive value `x` as FHIRPath system
  type."
  [x]
  (p/-value x))

(defn assoc-id
  "Associates `id` to `x`."
  [x id]
  (assoc x :id id))

(defn assoc-extension
  "Associates `extension` to `x`."
  [x extension]
  (assoc x :extension extension))

(defn assoc-value
  "Associates `value` to `x`."
  [x value]
  (assoc x :value value))

(defn to-xml [^Primitive x]
  (xml-node/element*
   nil
   (let [value (.valueAsString x)]
     (cond-> {}
       (some? (.id x))
       (assoc :id (.id x))
       (some? value)
       (assoc :value value)))
   (.extension x)))

(defn hash-into [x sink]
  (p/-hash-into x sink))

(defn references
  "Returns a collection of local references which are tuples of FHIR resource
  type name and FHIR resource id."
  [x]
  (p/-references x))

(defn- into! [to from]
  (reduce conj! to from))

;; ---- nil -------------------------------------------------------------------

;; Be sure all methods can be called on nil.
(extend-protocol p/FhirType
  nil
  (-assoc-id [_ _])
  (-assoc-extension [_ _])
  (-value [_])
  (-assoc-value [_ _])
  (-hash-into [_ _])
  (-references [_] []))

;; ---- Macros ----------------------------------------------------------------

(defmacro def-extend-protocol-primitive [name]
  `(extend-protocol p/FhirType
     ~name
     (~'-value [~'v]
       (.value ~'v))
     (~'-hash-into [~'v ~'sink]
       (.hashInto ~'v ~'sink))
     (~'-references [~'v]
       (p/-references (.extension ~'v)))))

(defn print-data-element [separate? ^Writer writer entry]
  (when separate?
    (.write writer " "))
  (.write writer (str ":" (name (key entry)) " "))
  (print-method (val entry) writer)
  true)

(defmacro print-type [name & [tag-name]]
  `(do (.write ~'w ~(format "#fhir/%s{" (or tag-name name)))
       (reduce #(print-data-element %1 ~'w %2) false ~'v)
       (.write ~'w "}")))

(defmacro def-print-method-primitive [name & [tag-name]]
  (let [class-sym (symbol (str "blaze.fhir.spec.type." (su/capital (str name))))]
    `(defmethod print-method ~class-sym
       [~(with-meta 'v {:tag class-sym}) ~(with-meta 'w {:tag 'Writer})]
       (if (or (.id ~'v) (seq (.extension ~'v)))
         (print-type ~name ~tag-name)
         (do (.write ~'w ~(format "#fhir/%s " (or tag-name name)))
             (print-method (.value ~'v) ~'w))))))

;; ---- boolean ---------------------------------------------------------------

(defn boolean? [x]
  (instance? blaze.fhir.spec.type.Boolean x))

(defn boolean [x]
  (cond
    (map? x)
    (blaze.fhir.spec.type.Boolean/create x)

    (true? x) blaze.fhir.spec.type.Boolean/TRUE
    (false? x) blaze.fhir.spec.type.Boolean/FALSE

    :else (ba/incorrect (format "Invalid boolean value `%s`." x))))

(def-extend-protocol-primitive blaze.fhir.spec.type.Boolean)

(def-print-method-primitive boolean)

(defmethod print-dup blaze.fhir.spec.type.Boolean [^blaze.fhir.spec.type.Boolean e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Boolean. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- integer ---------------------------------------------------------------

(defn integer? [x]
  (instance? blaze.fhir.spec.type.Integer x))

(defn integer [x]
  (cond
    (map? x) (blaze.fhir.spec.type.Integer/create ^IPersistentMap x)
    (int? x) (blaze.fhir.spec.type.Integer/create ^Number x)
    :else (ba/incorrect (format "Invalid integer value `%s`." x))))

(def-extend-protocol-primitive blaze.fhir.spec.type.Integer)

(def-print-method-primitive integer)

(defmethod print-dup blaze.fhir.spec.type.Integer [^blaze.fhir.spec.type.Integer e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Integer. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- string ----------------------------------------------------------------

(defn string? [x]
  (instance? blaze.fhir.spec.type.String x))

(defn string [x]
  (if (clojure.core/string? x)
    (blaze.fhir.spec.type.String/create ^String x)
    (blaze.fhir.spec.type.String/create ^IPersistentMap x)))

(defn string-interned [x]
  (if (clojure.core/string? x)
    (blaze.fhir.spec.type.String/createForceIntern ^String x)
    (blaze.fhir.spec.type.String/createForceIntern ^IPersistentMap x)))

(def-extend-protocol-primitive blaze.fhir.spec.type.String)

(def-print-method-primitive String$Normal "string")
(def-print-method-primitive String$Interned "string-interned")

;; ---- decimal ---------------------------------------------------------------

(defn decimal? [x]
  (instance? Decimal x))

(defn decimal [x]
  (if (clojure.core/decimal? x)
    (Decimal/create ^BigDecimal x)
    (Decimal/create ^IPersistentMap x)))

(def-extend-protocol-primitive Decimal)

(def-print-method-primitive decimal)

;; ---- uri -------------------------------------------------------------------

(defn uri? [x]
  (instance? Uri x))

(defn uri [x]
  (if (clojure.core/string? x)
    (Uri/create ^String x)
    (Uri/create ^IPersistentMap x)))

(defn uri-interned [x]
  (if (clojure.core/string? x)
    (Uri/createForceIntern ^String x)
    (Uri/createForceIntern ^IPersistentMap x)))

(def-extend-protocol-primitive Uri)

(def-print-method-primitive Uri$Normal "uri")
(def-print-method-primitive Uri$Interned "uri-interned")

;; ---- url -------------------------------------------------------------------

(defn url? [x]
  (instance? Url x))

(defn url [x]
  (if (clojure.core/string? x)
    (Url/create ^String x)
    (Url/create ^IPersistentMap x)))

(def-extend-protocol-primitive Url)

(def-print-method-primitive url)

;; ---- canonical -------------------------------------------------------------

(defn canonical? [x]
  (instance? Canonical x))

(defn canonical [x]
  (if (clojure.core/string? x)
    (Canonical/create ^String x)
    (Canonical/create ^IPersistentMap x)))

(def-extend-protocol-primitive Canonical)

(def-print-method-primitive canonical)

;; ---- base64Binary ----------------------------------------------------------

(defn base64Binary? [x]
  (instance? Base64Binary x))

(defn base64Binary [x]
  (if (clojure.core/string? x)
    (Base64Binary/create ^String x)
    (Base64Binary/create ^IPersistentMap x)))

(def-extend-protocol-primitive Base64Binary)

(def-print-method-primitive base64Binary)

;; ---- instant ---------------------------------------------------------------

(defn instant? [x]
  (instance? Instant x))

(defn instant [x]
  (if (map? x)
    (Instant/create ^IPersistentMap x)
    (Instant/create ^OffsetDateTime x)))

(def-extend-protocol-primitive Instant)

(def-print-method-primitive instant)

;; -- date --------------------------------------------------------------------

(defn date? [x]
  (instance? Date x))

(defn date [x]
  (if (map? x)
    (Date/create ^IPersistentMap x)
    (Date/create ^blaze.fhir.spec.type.system.Date x)))

(def-extend-protocol-primitive Date)

(def-print-method-primitive date)

;; -- dateTime ----------------------------------------------------------------

(defn dateTime? [x]
  (instance? DateTime x))

(defn dateTime [x]
  (if (map? x)
    (DateTime/create ^IPersistentMap x)
    (DateTime/create ^blaze.fhir.spec.type.system.DateTime x)))

(def-extend-protocol-primitive DateTime)

(def-print-method-primitive dateTime)

;; ---- time ------------------------------------------------------------------

(defn time? [x]
  (instance? Time x))

(defn time [x]
  (if (map? x)
    (Time/create ^IPersistentMap x)
    (Time/create ^LocalTime x)))

(def-extend-protocol-primitive Time)

(def-print-method-primitive time)

;; ---- code ------------------------------------------------------------------

(defn code? [x]
  (instance? Code x))

(defn code [x]
  (if (clojure.core/string? x)
    (Code/create ^String x)
    (Code/create ^IPersistentMap x)))

(def-extend-protocol-primitive Code)

(def-print-method-primitive code)

;; ---- oid -------------------------------------------------------------------

(defn oid? [x]
  (instance? Oid x))

(defn oid [x]
  (if (map? x)
    (Oid/create ^IPersistentMap x)
    (Oid/create ^String x)))

(def-extend-protocol-primitive Oid)

(def-print-method-primitive oid)

;; ---- id --------------------------------------------------------------------

(defn id? [x]
  (instance? Id x))

(defn id [x]
  (if (map? x)
    (Id/create ^IPersistentMap x)
    (Id/create ^String x)))

(def-extend-protocol-primitive Id)

(def-print-method-primitive id)

;; ---- markdown --------------------------------------------------------------

(defn markdown? [x]
  (instance? Markdown x))

(defn markdown [x]
  (if (map? x)
    (Markdown/create ^IPersistentMap x)
    (Markdown/create ^String x)))

(def-extend-protocol-primitive Markdown)

(def-print-method-primitive markdown)

;; ---- unsignedInt -----------------------------------------------------------

(defn unsignedInt? [x]
  (instance? UnsignedInt x))

(defn unsignedInt [x]
  (cond
    (map? x) (ba/try-one IllegalArgumentException ::anom/incorrect
               (UnsignedInt/create ^IPersistentMap x))
    (nat-int? x) (UnsignedInt/create ^Number x)
    :else (ba/incorrect (format "Invalid unsignedInt value `%s`." x))))

(def-extend-protocol-primitive UnsignedInt)

(def-print-method-primitive unsignedInt)

;; ---- positiveInt -----------------------------------------------------------

(defn positiveInt? [x]
  (instance? PositiveInt x))

(defn positiveInt [x]
  (cond
    (map? x) (ba/try-one IllegalArgumentException ::anom/incorrect
               (PositiveInt/create ^IPersistentMap x))
    (pos-int? x) (PositiveInt/create ^Number x)
    :else (ba/incorrect (format "Invalid positiveInt value `%s`." x))))

(def-extend-protocol-primitive PositiveInt)

(def-print-method-primitive positiveInt)

;; ---- uuid ------------------------------------------------------------------

(defn uuid? [x]
  (instance? Uuid x))

(defn uuid [x]
  (if (map? x)
    (Uuid/create ^IPersistentMap x)
    (Uuid/create ^String x)))

(def-extend-protocol-primitive Uuid)

(def-print-method-primitive uuid)

;; ---- xhtml -----------------------------------------------------------------

(defn xhtml? [x]
  (instance? Xhtml x))

(defn xhtml [x]
  (if (map? x)
    (Xhtml/create ^IPersistentMap x)
    (Xhtml/create ^String x)))

(def-extend-protocol-primitive Xhtml)

(def-print-method-primitive xhtml)

(defmethod print-dup Xhtml [^Xhtml e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Xhtml. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

(def ^:const xml-preamble-length
  (count "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))

(defn xml->Xhtml
  "Creates a xhtml from XML `element`."
  [element]
  (Xhtml/create (subs (xml/emit-str element) xml-preamble-length)))

(defn- wrap-div [s]
  (str "<div xmlns=\"http://www.w3.org/1999/xhtml\">" s "</div>"))

(defn- parse-xhtml* [s]
  (let [xml (xml/parse-str s)]
    ;; simply emit the xml in order to parse eager and see all exceptions
    (xml/emit-str xml)
    xml))

(defn- parse-xhtml [s]
  (try
    (parse-xhtml* s)
    (catch Exception _
      (-> s
          (str/replace "<" "&lt;")
          (str/replace ">" "&gt;")
          (wrap-div)
          (parse-xhtml*)))))

(defn xhtml-to-xml [{:keys [value]}]
  (update (parse-xhtml value) :attrs assoc :xmlns "http://www.w3.org/1999/xhtml"))

;; ---- Complex Types --------------------------------------------------------

(extend-protocol p/FhirType
  List
  (-value [_])
  (-hash-into [xs sink]
    (.putByte ^PrimitiveSink sink (byte 36))
    (reduce #(p/-hash-into %2 sink) nil xs))
  (-references [xs]
    (reduce #(into %1 (p/-references %2)) [] xs))
  IPersistentMap
  (-assoc-id [m id] (assoc m :id id))
  (-assoc-extension [m extension] (assoc m :extension extension))
  (-value [_])
  (-assoc-value [m value] (assoc m :value value))
  (-hash-into [m sink]
    (.putByte ^PrimitiveSink sink (byte 37))
    (run!
     (fn [^Map$Entry e]
       (.putInt ^PrimitiveSink sink (.hasheq ^Keyword (.getKey e)))
       (let [value (.getValue e)]
         (cond
           ;; for compatibility reasons, we use the hash signature of a FHIR.String instead of a System.String
           (clojure.core/string? value) (blaze.fhir.spec.type.String/hashIntoValue sink value)
           (keyword? value) (.putInt ^PrimitiveSink sink (.hasheq ^Keyword value))
           :else (p/-hash-into value sink))))
     (sort
      (reify Comparator
        (compare [_ e1 e2]
          (.compareTo ^Keyword (.getKey ^Map$Entry e1) (.getKey ^Map$Entry e2))))
      m)))
  (-references [m]
   ;; Bundle entries have no references, because Bundles itself are stored "as-is"
    (when-not (identical? :fhir.Bundle/entry (:fhir/type m))
      (into [] (comp (remove clojure.core/string?) (remove keyword?) (mapcat p/-references)) (vals m)))))

(defmacro def-print-method-complex [name]
  `(defmethod print-method ~(symbol name)
     [~(with-meta 'v {:tag (symbol name)}) ~(with-meta 'w {:tag 'Writer})]
     (print-type ~name)))

;; ---- Address ---------------------------------------------------------------

(defn address [x]
  (Address/create x))

(extend-protocol p/FhirType
  Address
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Address")

;; ---- Annotation ------------------------------------------------------------

(defn annotation [x]
  (Annotation/create x))

(extend-protocol p/FhirType
  Annotation
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Annotation")

;; ---- Attachment ------------------------------------------------------------

(defn attachment [x]
  (Attachment/create x))

(extend-protocol p/FhirType
  Attachment
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Attachment")

;; ---- Expression --------------------------------------------------------

(defn expression [x]
  (Expression/create x))

(extend-protocol p/FhirType
  Expression
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Expression")

;; ---- Extension --------------------------------------------------------

(defn extension [x]
  (Extension/create x))

(extend-protocol p/FhirType
  Extension
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (-> (transient [])
        (into! (p/-references (.extension e)))
        (into! (p/-references (.value e)))
        (persistent!))))

(def-print-method-complex "Extension")

;; ---- Coding ----------------------------------------------------------------

(defn coding [x]
  (Coding/create x))

(extend-protocol p/FhirType
  Coding
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Coding")

;; ---- CodeableConcept -------------------------------------------------------

(defn codeable-concept [x]
  (CodeableConcept/create x))

(extend-protocol p/FhirType
  CodeableConcept
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "CodeableConcept")

;; ---- Quantity --------------------------------------------------------------

(defn quantity [x]
  (Quantity/create x))

(extend-protocol p/FhirType
  Quantity
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Quantity")

;; ---- Range -----------------------------------------------------------------

(defn range [x]
  (Range/create x))

(extend-protocol p/FhirType
  Range
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Range")

;; ---- Ratio -----------------------------------------------------------------

(defn ratio [x]
  (Ratio/create x))

(extend-protocol p/FhirType
  Ratio
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Ratio")

;; ---- Period ----------------------------------------------------------------

(defn period [x]
  (Period/create x))

(extend-protocol p/FhirType
  Period
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Period")

;; ---- Identifier ------------------------------------------------------------

(defn identifier [x]
  (Identifier/create x))

(extend-protocol p/FhirType
  Identifier
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Identifier")

;; ---- HumanName -------------------------------------------------------------

(defn human-name [x]
  (HumanName/create x))

(extend-protocol p/FhirType
  HumanName
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "HumanName")

;; ---- Reference --------------------------------------------------------

(defn- valid-ref? [[type id]]
  (and (.matches (re-matcher #"[A-Z]([A-Za-z0-9_]){0,254}" type))
       (some->> id (re-matcher #"[A-Za-z0-9\-\.]{1,64}") .matches)))

(defn- reference-reference [ref]
  (let [ref (str/split ref #"/" 2)]
    (when (valid-ref? ref)
      [ref])))

(defn reference [x]
  (Reference/create x))

(extend-protocol p/FhirType
  Reference
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [r]
    (-> (transient (or (some-> (.reference r) :value reference-reference) []))
        (into! (p/-references (.extension r)))
        (into! (p/-references (.type r)))
        (into! (p/-references (.identifier r)))
        (into! (p/-references (.display r)))
        (persistent!))))

(def-print-method-complex "Reference")

;; ---- Meta ------------------------------------------------------------------

(defn meta [x]
  (Meta/create x))

(extend-protocol p/FhirType
  Meta
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Meta")

;; ---- BundleEntrySearch -----------------------------------------------------

(defn bundle-entry-search [x]
  (BundleEntrySearch/create x))

(extend-protocol p/FhirType
  BundleEntrySearch
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "BundleEntrySearch")

;; ---- ContactDetail ------------------------------------------------------------

(defn contact-detail [x]
  (ContactDetail/create x))

(extend-protocol p/FhirType
  ContactDetail
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "ContactDetail")

;; ---- ContactPoint ------------------------------------------------------------

(defn contact-point [x]
  (ContactPoint/create x))

(extend-protocol p/FhirType
  ContactPoint
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "ContactPoint")

;; ---- RelatedArtifact ------------------------------------------------------------

(defn related-artifact [x]
  (RelatedArtifact/create x))

(extend-protocol p/FhirType
  RelatedArtifact
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "RelatedArtifact")
