(ns blaze.fhir.spec.type
  "Functions for primitive and complex types."
  (:refer-clojure
   :exclude
   [boolean boolean? count decimal? integer? long meta range str string? time
    type uri? uuid?])
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-string]
   [blaze.fhir.spec.type.string-util :as su]
   [blaze.fhir.spec.xml :as spec-xml]
   [blaze.util :refer [str]]
   [clojure.data.xml :as xml]
   [clojure.data.xml.name :as xml-name]
   [clojure.data.xml.node :as xml-node]
   [clojure.string :as str]
   [cognitect.anomalies :as anom])
  (:import
   [blaze.fhir.spec.type
    Address Age Annotation Attachment Base Base64Binary BundleEntrySearch
    Canonical Code CodeableConcept Coding ContactDetail ContactPoint Contributor Count
    DataRequirement DataRequirement$CodeFilter DataRequirement$DateFilter DataRequirement$Sort
    Date DateTime Decimal Distance Dosage Dosage$DoseAndRate Duration Expression Extension HumanName Id
    Identifier Instant Markdown Meta Money Narrative Oid ParameterDefinition Period PositiveInt Primitive Quantity
    Range Ratio Reference RelatedArtifact SampledData Signature String$Interned
    String$Normal Time Timing Timing$Repeat TriggerDefinition UnsignedInt Uri Uri$Interned
    Uri$Normal Url UsageContext Uuid Xhtml]
   [clojure.lang IPersistentMap PersistentVector]
   [java.io Writer]
   [java.time LocalTime OffsetDateTime]))

(xml-name/alias-uri 'f "http://hl7.org/fhir")

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn to-xml [^Primitive x]
  (xml-node/element*
   nil
   (let [value (.valueAsString x)]
     (cond-> {}
       (some? (.id x))
       (assoc :id (.id x))
       (some? value)
       (assoc :value (spec-xml/replace-invalid-chars value))))
   (.extension x)))

(defn references
  "Returns a collection of local references which are tuples of FHIR resource
  type name and FHIR resource id."
  [x]
  (PersistentVector/create (.toList (Base/references x))))

;; ---- Macros ----------------------------------------------------------------

(defn print-data-element [separate? ^Writer writer entry]
  (when separate?
    (.write writer " "))
  (.write writer (str ":" (name (key entry)) " "))
  (print-method (val entry) writer)
  true)

(defmacro print-type [name & [tag-name]]
  `(do (.write ~'w ~(str "#" (or tag-name (format "fhir/%s" name)) "{"))
       (reduce #(print-data-element %1 ~'w %2) false ~'v)
       (.write ~'w "}")))

(defmacro def-print-method-primitive [name & [tag-name]]
  (let [class-sym (symbol (str "blaze.fhir.spec.type." (su/capital (str name))))]
    `(defmethod print-method ~class-sym
       [~(with-meta 'v {:tag class-sym}) ~(with-meta 'w {:tag 'Writer})]
       (if (or (.id ~'v) (seq (.extension ~'v)))
         (print-type ~name ~tag-name)
         (do (.write ~'w ~(str "#" (or tag-name (format "fhir/%s" name)) " "))
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

(def-print-method-primitive boolean)

;; ---- integer ---------------------------------------------------------------

(defn integer? [x]
  (instance? blaze.fhir.spec.type.Integer x))

(defn integer [x]
  (cond
    (map? x) (ba/try-one IllegalArgumentException ::anom/incorrect
               (blaze.fhir.spec.type.Integer/create ^IPersistentMap x))
    (int? x) (ba/try-one IllegalArgumentException ::anom/incorrect
               (blaze.fhir.spec.type.Integer/create ^Number x))
    :else (ba/incorrect (format "Invalid integer value `%s`." x))))

(def-print-method-primitive integer)

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

(def-print-method-primitive String$Normal "fhir/string")
(def-print-method-primitive String$Interned "fhir/string-interned")

;; ---- decimal ---------------------------------------------------------------

(defn decimal? [x]
  (instance? Decimal x))

(defn decimal [x]
  (if (clojure.core/decimal? x)
    (Decimal/create ^BigDecimal x)
    (Decimal/create ^IPersistentMap x)))

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

(def-print-method-primitive Uri$Normal "fhir/uri")
(def-print-method-primitive Uri$Interned "fhir/uri-interned")

;; ---- url -------------------------------------------------------------------

(defn url? [x]
  (instance? Url x))

(defn url [x]
  (if (clojure.core/string? x)
    (Url/create ^String x)
    (Url/create ^IPersistentMap x)))

(def-print-method-primitive url)

;; ---- canonical -------------------------------------------------------------

(defn canonical? [x]
  (instance? Canonical x))

(defn canonical [x]
  (if (clojure.core/string? x)
    (Canonical/create ^String x)
    (Canonical/create ^IPersistentMap x)))

(def-print-method-primitive canonical)

;; ---- base64Binary ----------------------------------------------------------

(defn base64Binary? [x]
  (instance? Base64Binary x))

(defn base64Binary [x]
  (if (clojure.core/string? x)
    (Base64Binary/create ^String x)
    (Base64Binary/create ^IPersistentMap x)))

(def-print-method-primitive base64Binary)

;; ---- instant ---------------------------------------------------------------

(defn instant? [x]
  (instance? Instant x))

(defn instant [x]
  (if (map? x)
    (Instant/create ^IPersistentMap x)
    (Instant/create ^OffsetDateTime x)))

(def-print-method-primitive instant)

;; -- date --------------------------------------------------------------------

(defn date? [x]
  (instance? Date x))

(defn date [x]
  (if (map? x)
    (Date/create ^IPersistentMap x)
    (Date/create ^blaze.fhir.spec.type.system.Date x)))

(def-print-method-primitive date)

;; -- dateTime ----------------------------------------------------------------

(defn dateTime? [x]
  (instance? DateTime x))

(defn dateTime [x]
  (if (map? x)
    (DateTime/create ^IPersistentMap x)
    (DateTime/create ^blaze.fhir.spec.type.system.DateTime x)))

(def-print-method-primitive dateTime)

;; ---- time ------------------------------------------------------------------

(defn time? [x]
  (instance? Time x))

(defn time [x]
  (if (map? x)
    (Time/create ^IPersistentMap x)
    (Time/create ^LocalTime x)))

(def-print-method-primitive time)

;; ---- code ------------------------------------------------------------------

(defn code? [x]
  (instance? Code x))

(defn code [x]
  (if (clojure.core/string? x)
    (Code/create ^String x)
    (Code/create ^IPersistentMap x)))

(def-print-method-primitive code)

;; ---- oid -------------------------------------------------------------------

(defn oid? [x]
  (instance? Oid x))

(defn oid [x]
  (if (map? x)
    (Oid/create ^IPersistentMap x)
    (Oid/create ^String x)))

(def-print-method-primitive oid)

;; ---- id --------------------------------------------------------------------

(defn id? [x]
  (instance? Id x))

(defn id [x]
  (if (map? x)
    (Id/create ^IPersistentMap x)
    (Id/create ^String x)))

(def-print-method-primitive id)

;; ---- markdown --------------------------------------------------------------

(defn markdown? [x]
  (instance? Markdown x))

(defn markdown [x]
  (if (map? x)
    (Markdown/create ^IPersistentMap x)
    (Markdown/create ^String x)))

(def-print-method-primitive markdown)

;; ---- unsignedInt -----------------------------------------------------------

(defn unsignedInt? [x]
  (instance? UnsignedInt x))

(defn unsignedInt [x]
  (cond
    (map? x) (ba/try-one IllegalArgumentException ::anom/incorrect
               (UnsignedInt/create ^IPersistentMap x))
    (nat-int? x) (ba/try-one IllegalArgumentException ::anom/incorrect
                   (UnsignedInt/create ^Number x))
    :else (ba/incorrect (format "Invalid unsignedInt value `%s`." x))))

(def-print-method-primitive unsignedInt)

;; ---- positiveInt -----------------------------------------------------------

(defn positiveInt? [x]
  (instance? PositiveInt x))

(defn positiveInt [x]
  (cond
    (map? x) (ba/try-one IllegalArgumentException ::anom/incorrect
               (PositiveInt/create ^IPersistentMap x))
    (pos-int? x) (ba/try-one IllegalArgumentException ::anom/incorrect
                   (PositiveInt/create ^Number x))
    :else (ba/incorrect (format "Invalid positiveInt value `%s`." x))))

(def-print-method-primitive positiveInt)

;; ---- uuid ------------------------------------------------------------------

(defn uuid? [x]
  (instance? Uuid x))

(defn uuid [x]
  (if (map? x)
    (Uuid/create ^IPersistentMap x)
    (Uuid/create ^String x)))

(def-print-method-primitive uuid)

;; ---- xhtml -----------------------------------------------------------------

(defn xhtml? [x]
  (instance? Xhtml x))

(defn xhtml [x]
  (if (map? x)
    (Xhtml/create ^IPersistentMap x)
    (Xhtml/create ^String x)))

(def-print-method-primitive xhtml)

(def ^:const xml-preamble-length
  (clojure.core/count "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))

(defn xml->Xhtml
  "Creates a xhtml from XML `element`."
  [{{:keys [id extension]} :attrs :as element}]
  (let [value (subs (xml/emit-str (update element :attrs dissoc :id :extension)) xml-preamble-length)]
    (if (or id extension)
      (Xhtml/create {:id id :extension extension :value value})
      (Xhtml/create value))))

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
      (-> (or s "")
          (str/replace "<" "&lt;")
          (str/replace ">" "&gt;")
          (wrap-div)
          (parse-xhtml*)))))

(defn xhtml-to-xml [{:keys [id extension value]}]
  (cond-> (update (parse-xhtml value) :attrs assoc :xmlns "http://www.w3.org/1999/xhtml")
    id (update :attrs assoc :id id)
    (seq extension) (update :attrs assoc :extension extension)))

;; ---- Complex Types --------------------------------------------------------

(defmacro def-print-method-complex [name & [tag-name]]
  `(defmethod print-method ~(symbol name)
     [~(with-meta 'v {:tag (symbol name)}) ~(with-meta 'w {:tag 'Writer})]
     (print-type ~name ~tag-name)))

;; ---- Address ---------------------------------------------------------------

(defn address [x]
  (Address/create x))

(def-print-method-complex "Address")

;; ---- Age ---------------------------------------------------------------

(defn age [x]
  (Age/create x))

(def-print-method-complex "Age")

;; ---- Annotation ------------------------------------------------------------

(defn annotation [x]
  (Annotation/create x))

(def-print-method-complex "Annotation")

;; ---- Attachment ------------------------------------------------------------

(defn attachment [x]
  (Attachment/create x))

(def-print-method-complex "Attachment")

;; ---- BundleEntrySearch -----------------------------------------------------

(defn bundle-entry-search [x]
  (BundleEntrySearch/create x))

(def-print-method-complex "BundleEntrySearch" "fhir.Bundle.entry/search")

;; ---- CodeableConcept -------------------------------------------------------

(defn codeable-concept [x]
  (CodeableConcept/create x))

(def-print-method-complex "CodeableConcept")

;; ---- Coding ----------------------------------------------------------------

(defn coding [x]
  (Coding/create x))

(def-print-method-complex "Coding")

;; ---- ContactDetail ------------------------------------------------------------

(defn contact-detail [x]
  (ContactDetail/create x))

(def-print-method-complex "ContactDetail")

;; ---- ContactPoint ------------------------------------------------------------

(defn contact-point [x]
  (ContactPoint/create x))

(def-print-method-complex "ContactPoint")

;; ---- Contributor --------------------------------------------------------

(defn contributor [x]
  (Contributor/create x))

(def-print-method-complex "Contributor")

;; ---- Count --------------------------------------------------------

(defn count [x]
  (Count/create x))

(def-print-method-complex "Count")

;; ---- DataRequirement -------------------------------------------------------

(defn data-requirement [x]
  (DataRequirement/create x))

(def-print-method-complex "DataRequirement")

(defn data-requirement-code-filter [x]
  (DataRequirement$CodeFilter/create x))

(def-print-method-complex "DataRequirement$CodeFilter" "fhir.DataRequirement/codeFilter")

(defn data-requirement-date-filter [x]
  (DataRequirement$DateFilter/create x))

(def-print-method-complex "DataRequirement$DateFilter" "fhir.DataRequirement/dateFilter")

(defn data-requirement-sort [x]
  (DataRequirement$Sort/create x))

(def-print-method-complex "DataRequirement$Sort" "fhir.DataRequirement/sort")

;; ---- Distance --------------------------------------------------------

(defn distance [x]
  (Distance/create x))

(def-print-method-complex "Distance")

;; ---- Dosage ----------------------------------------------------------------

(defn dosage [x]
  (Dosage/create x))

(def-print-method-complex "Dosage")

(defn dosage-dose-and-rate [x]
  (Dosage$DoseAndRate/create x))

(def-print-method-complex "Dosage$DoseAndRate" "fhir.Dosage/doseAndRate")

;; ---- Duration --------------------------------------------------------

(defn duration [x]
  (Duration/create x))

(def-print-method-complex "Duration")

;; ---- Expression --------------------------------------------------------

(defn expression [x]
  (Expression/create x))

(def-print-method-complex "Expression")

;; ---- Extension --------------------------------------------------------

(defn extension [x]
  (Extension/create x))

(def-print-method-complex "Extension")

;; ---- HumanName -------------------------------------------------------------

(defn human-name [x]
  (HumanName/create x))

(def-print-method-complex "HumanName")

;; ---- Identifier ------------------------------------------------------------

(defn identifier [x]
  (Identifier/create x))

(def-print-method-complex "Identifier")

;; ---- Meta ------------------------------------------------------------------

(defn meta [x]
  (Meta/create x))

(def-print-method-complex "Meta")

;; ---- Money ------------------------------------------------------------------

(defn money [x]
  (Money/create x))

(def-print-method-complex "Money")

;; ---- Narrative -------------------------------------------------------------

(defn narrative [x]
  (Narrative/create x))

(def-print-method-complex "Narrative")

;; ---- ParameterDefinition --------------------------------------------------------

(defn parameter-definition [x]
  (ParameterDefinition/create x))

(def-print-method-complex "ParameterDefinition")

;; ---- Period ----------------------------------------------------------------

(defn period [x]
  (Period/create x))

(def-print-method-complex "Period")

;; ---- Quantity --------------------------------------------------------------

(defn quantity [x]
  (Quantity/create x))

(def-print-method-complex "Quantity")

;; ---- Range -----------------------------------------------------------------

(defn range [x]
  (Range/create x))

(def-print-method-complex "Range")

;; ---- Ratio -----------------------------------------------------------------

(defn ratio [x]
  (Ratio/create x))

(def-print-method-complex "Ratio")

;; ---- Reference --------------------------------------------------------

(defn reference [x]
  (Reference/create x))

(def-print-method-complex "Reference")

;; ---- RelatedArtifact ------------------------------------------------------------

(defn related-artifact [x]
  (RelatedArtifact/create x))

(def-print-method-complex "RelatedArtifact")

;; ---- SampledData ------------------------------------------------------------

(defn sampled-data [x]
  (SampledData/create x))

(def-print-method-complex "SampledData")

;; ---- Signature ------------------------------------------------------------

(defn signature [x]
  (Signature/create x))

(def-print-method-complex "Signature")

;; ---- Timing ------------------------------------------------------------

(defn timing [x]
  (Timing/create x))

(def-print-method-complex "Timing")

(defn timing-repeat [x]
  (Timing$Repeat/create x))

(def-print-method-complex "Timing$Repeat" "fhir.Timing/repeat")

;; ---- TriggerDefinition ------------------------------------------------------------

(defn trigger-definition [x]
  (TriggerDefinition/create x))

(def-print-method-complex "TriggerDefinition")

;; ---- UsageContext ------------------------------------------------------------

(defn usage-context [x]
  (UsageContext/create x))

(def-print-method-complex "UsageContext")
