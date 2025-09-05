(ns blaze.fhir.spec.type
  "Functions for primitive and complex types."
  (:refer-clojure
   :exclude
   [boolean boolean? decimal? integer? long meta range str string? time type uri? uuid?])
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-string]
   [blaze.fhir.spec.impl.intern :as intern]
   [blaze.fhir.spec.type.json :as json]
   [blaze.fhir.spec.type.macros :refer [defextended]]
   [blaze.fhir.spec.type.protocols :as p]
   [blaze.fhir.spec.type.string-util :as su]
   [blaze.fhir.spec.type.system :as system]
   [blaze.util :refer [str]]
   [clojure.alpha.spec :as s2]
   [clojure.data.xml :as xml]
   [clojure.data.xml.name :as xml-name]
   [clojure.data.xml.node :as xml-node]
   [clojure.string :as str])
  (:import
   [blaze.fhir.spec.type
    Address Annotation Attachment Base64Binary BundleEntrySearch Canonical Code
    CodeableConcept Coding ContactDetail ContactPoint Date DateTime Decimal
    Expression Extension HumanName Id Identifier Instant Markdown Meta Oid
    Period PositiveInt Quantity Range Ratio Reference RelatedArtifact Time
    UnsignedInt Uri Url Uuid]
   [blaze.fhir.spec.type.system Strings]
   [clojure.lang IPersistentMap Keyword]
   [com.fasterxml.jackson.core JsonGenerator]
   [com.google.common.hash PrimitiveSink]
   [java.io Writer]
   [java.util Comparator List Map$Entry]))

(xml-name/alias-uri 'f "http://hl7.org/fhir")

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn type
  "Returns the FHIR type if `x` if it has some."
  [x]
  (p/-type x))

(defn value
  "Returns the possible value of the primitive value `x` as FHIRPath system
  type."
  [x]
  (p/-value x))

(defn assoc-id
  "Associates `id` to `x`."
  [x id]
  (p/-assoc-id x id))

(defn assoc-extension
  "Associates `extension` to `x`."
  [x extension]
  (p/-assoc-extension x extension))

(defn assoc-value
  "Associates `value` to `x`."
  [x value]
  (p/-assoc-value x value))

(defn to-xml [x]
  (p/-to-xml x))

(defn hash-into [x sink]
  (p/-hash-into x sink))

(defn references
  "Returns a collection of local references which are tuples of FHIR resource
  type name and FHIR resource id."
  [x]
  (p/-references x))

(defn- create-fn [intern create parse-fn]
  (fn [x]
    (if (map? x)
      (let [{:keys [id extension value]} x]
        (cond
          (and (nil? value) (p/-interned extension) (nil? id))
          (intern {:extension extension})

          (and (nil? extension) (nil? id))
          (parse-fn value)

          :else
          (create id extension (some-> value parse-fn))))
      (parse-fn x))))

(defn- into! [to from]
  (reduce conj! to from))

;; ---- nil -------------------------------------------------------------------

;; Be sure all methods can be called on nil.
(extend-protocol p/FhirType
  nil
  (-type [_])
  (-interned [_] true)
  (-assoc-id [_ _])
  (-assoc-extension [_ _])
  (-value [_])
  (-assoc-value [_ _])
  (-has-primary-content [_] false)
  (-serialize-json [_ _ _])
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ _ _])
  (-to-xml [_])
  (-hash-into [_ _])
  (-references [_] []))

;; ---- Object ----------------------------------------------------------------

;; Other instances have no type.
(extend-protocol p/FhirType
  Object
  (-type [_])
  (-interned [_] false)
  (-references [_]))

;; ---- Macros ----------------------------------------------------------------

(defmacro def-extend-protocol-primitive [name & {:as opts}]
  (let [constructor (symbol (str name) "new")]
    `(extend-protocol p/FhirType
       ~name
       (~'-type [~'v]
         (.fhirType ~'v))
       (~'-interned [~'v]
         (and (nil? (.id ~'v)) (p/-interned (.extension ~'v))
              ~@(when-not (:value-internable opts) [`(nil? (.value ~'v))])))
       (~'-value [~'v]
         (.value ~'v))
       (~'-assoc-id [~'v ~'id]
         (~constructor ~'id (.extension ~'v) (.value ~'v)))
       (~'-assoc-extension [~'v ~'extension]
         (~constructor (.id ~'v) ~'extension (.value ~'v)))
       (~'-assoc-value [~'v ~'value]
         (~constructor (.id ~'v) (.extension ~'v) ~'value))
       (~'-has-primary-content [~'v]
         (.hasValue ~'v))
       (~'-serialize-json [~'v ~'generator]
         (.serializeJsonPrimitiveValue ~'v ~'generator))
       (~'-has-secondary-content [~'v]
         (.isExtended ~'v))
       (~'-serialize-json-secondary [~'v ~'generator]
         (.serializeJsonPrimitiveExtension ~'v ~'generator))
       (~'-to-xml [~'v]
         (xml-node/element*
          nil
          (cond-> {}
            (some? (.id ~'v))
            (assoc :id (.id ~'v))
            (some? (.value ~'v))
            (assoc :value (system/-to-string (.value ~'v))))
          (.extension ~'v)))
       (~'-hash-into [~'v ~'sink]
         (.hashInto ~'v ~'sink))
       (~'-references [~'v]
         (p/-references (.extension ~'v))))))

(defmacro print-data-element
  {:arglists '([prev-names* name])}
  [& args]
  (let [prev-names (butlast args)
        name (last args)
        accessor (fn [name] `(~(symbol (str "." (if (= "size" name) "sizeValue" name))) ~'v))]
    `(when ~(accessor name)
       ~@(when (seq prev-names)
           [`(when (or ~@(map accessor prev-names))
               (.write ~'w " "))])
       (.write ~'w ~(str ":" name " "))
       (print-method ~(accessor name) ~'w))))

(defmacro print-type [name & data-element-names]
  `(do (.write ~'w ~(format "#fhir/%s{" name))
       ~@(map
          (fn [names] `(print-data-element ~@names))
          (map #(take % data-element-names) (clojure.core/range 1 (inc (count data-element-names)))))
       (.write ~'w "}")))

(defmacro def-print-method-primitive [name & data-element-names]
  (let [class-sym (symbol (str "blaze.fhir.spec.type." (su/capital (str name))))]
    `(defmethod print-method ~class-sym
       [~(with-meta 'v {:tag class-sym}) ~(with-meta 'w {:tag 'Writer})]
       (if (or (.id ~'v) (.extension ~'v))
         (print-type ~name ~@data-element-names)
         (do (.write ~'w ~(format "#fhir/%s " name))
             (print-method (.value ~'v) ~'w))))))

;; ---- boolean ---------------------------------------------------------------

(defn boolean? [x]
  (instance? blaze.fhir.spec.type.Boolean x))

(defn- map->Boolean [x]
  (blaze.fhir.spec.type.Boolean/create x))

(def ^{:arglists '([x])} boolean
  (let [intern-extended (intern/intern-value map->Boolean)]
    (fn [x]
      (cond
        (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension))
            (intern-extended x)
            (blaze.fhir.spec.type.Boolean. id extension value)))

        (true? x) blaze.fhir.spec.type.Boolean/TRUE
        (false? x) blaze.fhir.spec.type.Boolean/FALSE

        :else (ba/incorrect (format "Invalid boolean value `%s`." x))))))

(def-extend-protocol-primitive blaze.fhir.spec.type.Boolean
  :value-internable true)

(def-print-method-primitive boolean "id" "extension" "value")

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

(defn- map->Integer [x]
  (blaze.fhir.spec.type.Integer/create x))

(def ^{:arglists '([x])} integer
  (let [intern-extended (intern/intern-value map->Integer)]
    (fn [x]
      (cond
        (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (blaze.fhir.spec.type.Integer. id extension (some-> value int))))

        (int? x) (blaze.fhir.spec.type.Integer. nil nil (int x))

        :else (ba/incorrect (format "Invalid integer value `%s`." x))))))

(def-extend-protocol-primitive blaze.fhir.spec.type.Integer)

(def-print-method-primitive integer "id" "extension" "value")

(defmethod print-dup blaze.fhir.spec.type.Integer [^blaze.fhir.spec.type.Integer e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Integer. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- long ---------------------------------------------------------------

(declare long)

(extend-protocol p/FhirType
  Long
  (-type [_] :fhir/long)
  (-interned [_] false)
  (-assoc-id [l id] (long {:id id :value l}))
  (-assoc-extension [l extension] (long {:extension extension :value l}))
  (-value [l] l)
  (-assoc-value [_ value] (long value))
  (-has-primary-content [_] true)
  (-serialize-json [l generator]
    (.writeNumber ^JsonGenerator generator (unchecked-long l)))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [l]
    (xml-node/element nil {:value (str l)}))
  (-hash-into [l sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 2))                                   ; :fhir/long
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into l sink))
  (-references [_]))

(defextended ExtendedLong [id extension ^Long value]
  :fhir-type :fhir/long :hash-num 2)

(def ^{:arglists '([x])} long
  (create-fn (intern/intern-value map->ExtendedLong) ->ExtendedLong
             #(if (clojure.core/int? %) (clojure.core/long %) ::s2/invalid)))

(defn long? [x]
  (identical? :fhir/long (type x)))

;; ---- string ----------------------------------------------------------------

(defn string? [x]
  (instance? blaze.fhir.spec.type.String x))

(defn- map->String [x]
  (blaze.fhir.spec.type.String/create x))

(def string
  (let [intern-extended (intern/intern-value map->String)]
    (fn [data]
      (if (clojure.core/string? data)
        (blaze.fhir.spec.type.String. nil nil data)
        (let [{:keys [id extension value]} data]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended data)
            (blaze.fhir.spec.type.String. id extension value)))))))

(def-extend-protocol-primitive blaze.fhir.spec.type.String)

(def-print-method-primitive string "id" "extension" "value")

(defmethod print-dup blaze.fhir.spec.type.String [^blaze.fhir.spec.type.String s ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.String. ")
  (print-dup (.id s) w)
  (.write w " ")
  (print-dup (.extension s) w)
  (.write w " ")
  (print-dup (.value s) w)
  (.write w ")"))

;; ---- decimal ---------------------------------------------------------------

(defn decimal? [x]
  (instance? Decimal x))

(defn- map->Decimal [x]
  (Decimal/create x))

(def ^{:arglists '([x])} decimal
  (let [intern-extended (intern/intern-value map->Decimal)]
    (fn [x]
      (if (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (Decimal. id extension value)))
        (Decimal. nil nil x)))))

(def-extend-protocol-primitive Decimal)

(def-print-method-primitive decimal "id" "extension" "value")

(defmethod print-dup Decimal [^Decimal e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Decimal. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- uri -------------------------------------------------------------------

(defn uri? [x]
  (instance? Uri x))

(defn- map->Uri [x]
  (Uri/create x))

(def ^{:arglists '([x])} uri
  (let [intern (intern/intern-value #(Uri. nil nil %))
        intern-extended (intern/intern-value map->Uri)]
    (fn [x]
      (if (clojure.core/string? x)
        (intern x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension))
            (intern-extended x)
            (Uri. id extension value)))))))

(def-extend-protocol-primitive Uri
  :value-internable true)

(def-print-method-primitive uri "id" "extension" "value")

(defmethod print-dup Uri [^Uri e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Uri. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- url -------------------------------------------------------------------

(defn url? [x]
  (instance? Url x))

(defn- map->Url [x]
  (Url/create x))

(def ^{:arglists '([x])} url
  (let [intern-extended (intern/intern-value map->Url)]
    (fn [x]
      (if (clojure.core/string? x)
        (Url. nil nil x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (Url. id extension value)))))))

(def-extend-protocol-primitive Url)

(def-print-method-primitive url "id" "extension" "value")

(defmethod print-dup Url [^Url e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Url. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- canonical -------------------------------------------------------------

(defn canonical? [x]
  (instance? Canonical x))

(defn- map->Canonical [x]
  (Canonical/create x))

(def ^{:arglists '([x])} canonical
  (let [intern (intern/intern-value #(Canonical. nil nil %))
        intern-extended (intern/intern-value map->Canonical)]
    (fn [x]
      (if (clojure.core/string? x)
        (intern x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension))
            (intern-extended x)
            (Canonical. id extension value)))))))

(def-extend-protocol-primitive Canonical
  :value-internable true)

(def-print-method-primitive canonical "id" "extension" "value")

(defmethod print-dup Canonical [^Canonical e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Canonical. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- base64Binary ----------------------------------------------------------

(defn base64Binary? [x]
  (instance? Base64Binary x))

(defn- map->Base64Binary [x]
  (Base64Binary/create x))

(def ^{:arglists '([x])} base64Binary
  (let [intern-extended (intern/intern-value map->Base64Binary)]
    (fn [x]
      (if (clojure.core/string? x)
        (Base64Binary. nil nil x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (Base64Binary. id extension value)))))))

(def-extend-protocol-primitive Base64Binary)

(def-print-method-primitive base64Binary "id" "extension" "value")

(defmethod print-dup Base64Binary [^Base64Binary e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Base64Binary. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- instant ---------------------------------------------------------------

(defn instant? [x]
  (instance? Instant x))

(defn- map->Instant [x]
  (Instant/create x))

(def ^{:arglists '([x])} instant
  (let [intern-extended (intern/intern-value map->Instant)]
    (fn [x]
      (if (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (Instant. id extension value)))
        (Instant. nil nil x)))))

(def-extend-protocol-primitive Instant)

(def-print-method-primitive instant "id" "extension" "value")

(defmethod print-dup Instant [^Instant e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Instant. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; -- date --------------------------------------------------------------------

(defn date? [x]
  (instance? Date x))

(defn- map->Date [x]
  (Date/create x))

(def ^{:arglists '([x])} date
  (let [intern-extended (intern/intern-value map->Date)]
    (fn [x]
      (if (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (Date. id extension value)))
        (Date. nil nil x)))))

(def-extend-protocol-primitive Date)

(def-print-method-primitive date "id" "extension" "value")

(defmethod print-dup Date [^Date e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Date. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; -- dateTime ----------------------------------------------------------------

(defn dateTime? [x]
  (instance? DateTime x))

(defn- map->DateTime [x]
  (DateTime/create x))

(def ^{:arglists '([x])} dateTime
  (let [intern-extended (intern/intern-value map->DateTime)]
    (fn [x]
      (if (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (DateTime. id extension value)))
        (DateTime. nil nil x)))))

(def-extend-protocol-primitive DateTime)

(def-print-method-primitive dateTime "id" "extension" "value")

(defmethod print-dup DateTime [^DateTime e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.DateTime. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- time ------------------------------------------------------------------

(defn time? [x]
  (instance? Time x))

(defn- map->Time [x]
  (Time/create x))

(def ^{:arglists '([x])} time
  (let [intern-extended (intern/intern-value map->Time)]
    (fn [x]
      (if (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (Time. id extension value)))
        (Time. nil nil x)))))

(def-extend-protocol-primitive Time)

(def-print-method-primitive time "id" "extension" "value")

(defmethod print-dup Time [^Time e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Time. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- code ------------------------------------------------------------------

(defn code? [x]
  (instance? Code x))

(defn- map->Code [x]
  (Code/create x))

(def ^{:arglists '([x])} code
  (let [intern (intern/intern-value #(Code. nil nil %))
        intern-extended (intern/intern-value map->Code)]
    (fn [x]
      (if (clojure.core/string? x)
        (intern x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension))
            (intern-extended x)
            (Code. id extension value)))))))

(def-extend-protocol-primitive Code
  :value-internable true)

(def-print-method-primitive code "id" "extension" "value")

(defmethod print-dup Code [^Code e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Code. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- oid -------------------------------------------------------------------

(defn oid? [x]
  (instance? Oid x))

(defn- map->Oid [x]
  (Oid/create x))

(def ^{:arglists '([x])} oid
  (let [intern-extended (intern/intern-value map->Oid)]
    (fn [x]
      (if (clojure.core/string? x)
        (Oid. nil nil x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (Oid. id extension value)))))))

(def-extend-protocol-primitive Oid)

(def-print-method-primitive oid "id" "extension" "value")

(defmethod print-dup Oid [^Oid e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Oid. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- id --------------------------------------------------------------------

(defn id? [x]
  (instance? Id x))

(defn- map->Id [x]
  (Id/create x))

(def ^{:arglists '([x])} id
  (let [intern-extended (intern/intern-value map->Id)]
    (fn [x]
      (if (clojure.core/string? x)
        (Id. nil nil x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (Id. id extension value)))))))

(def-extend-protocol-primitive Id)

(def-print-method-primitive id "id" "extension" "value")

(defmethod print-dup Id [^Id e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Id. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- markdown --------------------------------------------------------------

(defn markdown? [x]
  (instance? Markdown x))

(defn- map->Markdown [x]
  (Markdown/create x))

(def ^{:arglists '([x])} markdown
  (let [intern-extended (intern/intern-value map->Markdown)]
    (fn [x]
      (if (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (Markdown. id extension value)))
        (Markdown. nil nil x)))))

(def-extend-protocol-primitive Markdown)

(def-print-method-primitive markdown "id" "extension" "value")

(defmethod print-dup Markdown [^Markdown e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Markdown. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- unsignedInt -----------------------------------------------------------

(defn unsignedInt? [x]
  (instance? UnsignedInt x))

(defn- map->UnsignedInt [x]
  (UnsignedInt/create x))

(def ^{:arglists '([x])} unsignedInt
  (let [intern-extended (intern/intern-value map->UnsignedInt)]
    (fn [x]
      (cond
        (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (UnsignedInt. id extension (some-> value int))))

        (int? x) (UnsignedInt. nil nil (int x))

        :else (ba/incorrect (format "Invalid unsignedInt value `%s`." x))))))

(def-extend-protocol-primitive UnsignedInt)

(def-print-method-primitive unsignedInt "id" "extension" "value")

(defmethod print-dup UnsignedInt [^UnsignedInt e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.UnsignedInt. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- positiveInt -----------------------------------------------------------

(defn positiveInt? [x]
  (instance? PositiveInt x))

(defn- map->PositiveInt [x]
  (PositiveInt/create x))

(def ^{:arglists '([x])} positiveInt
  (let [intern-extended (intern/intern-value map->PositiveInt)]
    (fn [x]
      (cond
        (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (PositiveInt. id extension (some-> value int))))

        (int? x) (PositiveInt. nil nil (int x))

        :else (ba/incorrect (format "Invalid positiveInt value `%s`." x))))))

(def-extend-protocol-primitive PositiveInt)

(def-print-method-primitive positiveInt "id" "extension" "value")

(defmethod print-dup PositiveInt [^PositiveInt e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.PositiveInt. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- uuid ------------------------------------------------------------------

(defn uuid? [x]
  (instance? Uuid x))

(defn- map->Uuid [x]
  (Uuid/create x))

(def ^{:arglists '([x])} uuid
  (let [intern-extended (intern/intern-value map->Uuid)]
    (fn [x]
      (if (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (Uuid. id extension value)))
        (Uuid. nil nil x)))))

(def-extend-protocol-primitive Uuid)

(def-print-method-primitive uuid "id" "extension" "value")

(defmethod print-dup Uuid [^Uuid e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Uuid. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- xhtml -----------------------------------------------------------------

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

(declare ->Xhtml)

(deftype Xhtml [value]
  p/FhirType
  (-type [_] :fhir/xhtml)
  (-interned [_] false)
  (-value [_] value)
  (-assoc-value [_ val] (->Xhtml val))
  (-has-primary-content [_] true)
  (-serialize-json [_ generator]
    (.writeString ^JsonGenerator generator ^String value))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [_] (update (parse-xhtml value) :attrs assoc :xmlns "http://www.w3.org/1999/xhtml"))
  (-hash-into [_ sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 20))                                  ; :fhir/xhtml
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into value sink))
  (-references [_])
  Object
  (equals [_ x]
    (and (instance? Xhtml x) (= value (.value ^Xhtml x))))
  (hashCode [_]
    (.hashCode value))
  (toString [_]
    (str value)))

(defmethod print-method Xhtml [xhtml ^Writer w]
  (.write w "#fhir/xhtml\"")
  (.write w ^String (value xhtml))
  (.write w "\""))

(def ^:const xml-preamble-length
  (count "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))

(defn xml->Xhtml
  "Creates a xhtml from XML `element`."
  [element]
  (->Xhtml (subs (xml/emit-str element) xml-preamble-length)))

(defn xhtml? [x]
  (instance? Xhtml x))

;; ---- Complex Types --------------------------------------------------------

(extend-protocol p/FhirType
  List
  (-type [_])
  (-interned [xs]
    (reduce #(if (p/-interned %2) %1 (reduced false)) true xs))
  (-value [_])
  (-has-primary-content [xs]
    (reduce #(when (p/-has-primary-content %2) (reduced true)) nil xs))
  (-serialize-json [xs generator]
    (.writeStartArray ^JsonGenerator generator)
    (run! #(p/-serialize-json % generator) xs)
    (.writeEndArray ^JsonGenerator generator))
  (-has-secondary-content [xs]
    (reduce #(when (p/-has-secondary-content %2) (reduced true)) nil xs))
  (-serialize-json-secondary [xs generator]
    (.writeStartArray ^JsonGenerator generator)
    (reduce #(p/-serialize-json-secondary %2 generator) nil xs)
    (.writeEndArray ^JsonGenerator generator))
  (-hash-into [xs sink]
    (.putByte ^PrimitiveSink sink (byte 36))
    (reduce #(p/-hash-into %2 sink) nil xs))
  (-references [xs]
    (reduce #(into %1 (p/-references %2)) [] xs))
  Keyword
  (-type [_])
  (-value [_])
  (-hash-into [k sink]
    (.putInt ^PrimitiveSink sink (.hasheq k)))
  (-references [_])
  IPersistentMap
  (-type [m]
    (.valAt m :fhir/type))
  (-interned [_] false)
  (-assoc-id [m id] (assoc m :id id))
  (-assoc-extension [m extension] (assoc m :extension extension))
  (-value [_])
  (-assoc-value [m value] (assoc m :value value))
  (-has-primary-content [_] true)
  (-serialize-json [m generator]
    (.writeStartObject ^JsonGenerator generator)
    (run!
     (fn [^Map$Entry e]
       (let [^Keyword key (.getKey e)]
         (when-not (identical? :fhir/type key)
           (when-some [v (.getValue e)]
             (json/write-field generator (json/field-name (.getName key)) v)))))
     m)
    (.writeEndObject ^JsonGenerator generator))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [m _]
    (throw (ex-info "A complex type/resource has no secondary content." m)))
  (-hash-into [m sink]
    (.putByte ^PrimitiveSink sink (byte 37))
    (when-let [id (:id m)]
      (.putByte ^PrimitiveSink sink (byte 0))
      (Strings/hashInto id sink))
    (run!
     (fn [^Map$Entry e]
       (p/-hash-into (.getKey e) sink)
       (p/-hash-into (.getValue e) sink))
     (sort
      (reify Comparator
        (compare [_ e1 e2]
          (.compareTo ^Keyword (.getKey ^Map$Entry e1) (.getKey ^Map$Entry e2))))
      (dissoc m :id))))
  (-references [m]
   ;; Bundle entries have no references, because Bundles itself are stored "as-is"
    (when-not (identical? :fhir.Bundle/entry (p/-type m))
      (transduce (mapcat p/-references) conj [] (vals m)))))

(defmacro def-print-method-complex [name & data-element-names]
  `(defmethod print-method ~(symbol name)
     [~(with-meta 'v {:tag (symbol name)}) ~(with-meta 'w {:tag 'Writer})]
     (print-type ~name ~@data-element-names)))

;; ---- Attachment ------------------------------------------------------------

(defn- map->Attachment [x]
  (Attachment/create x))

(def ^{:arglists '([x])} attachment
  (let [intern (intern/intern-value map->Attachment)]
    (fn [{:keys [id extension contentType language data url size hash title creation] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned contentType)
               (p/-interned language) (p/-interned data) (p/-interned url)
               (p/-interned size) (p/-interned hash) (p/-interned title)
               (p/-interned creation))
        (intern x)
        (Attachment. id extension contentType language data url size hash title creation)))))

(extend-protocol p/FhirType
  Attachment
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Attachment" "id" "extension" "contentType" "language"
  "data" "url" "size" "hash" "title" "creation")

(defmethod print-dup Attachment [^Attachment e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Attachment. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.contentType e) w)
  (.write w " ")
  (print-dup (.language e) w)
  (.write w " ")
  (print-dup (.data e) w)
  (.write w " ")
  (print-dup (.url e) w)
  (.write w " ")
  (print-dup (.sizeValue e) w)
  (.write w " ")
  (print-dup (.hash e) w)
  (.write w " ")
  (print-dup (.title e) w)
  (.write w " ")
  (print-dup (.creation e) w)
  (.write w ")"))

;; ---- Expression --------------------------------------------------------

(defn- map->Expression [x]
  (Expression/create x))

(def ^{:arglists '([x])} expression
  (let [intern (intern/intern-value map->Expression)]
    (fn [{:keys [id extension description name language expression reference] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned description)
               (p/-interned name) (p/-interned language) (p/-interned expression)
               (p/-interned reference))
        (intern x)
        (Expression. id extension description name language expression reference)))))

(extend-protocol p/FhirType
  Expression
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e))
         (p/-interned (.description e)) (p/-interned (.name e))
         (p/-interned (.language e)) (p/-interned (.expression e))
         (p/-interned (.reference e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Expression" "id" "extension" "description" "name"
  "language" "expression" "reference")

(defmethod print-dup Expression [^Expression e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Expression. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.description e) w)
  (.write w " ")
  (print-dup (.name e) w)
  (.write w " ")
  (print-dup (.language e) w)
  (.write w " ")
  (print-dup (.expression e) w)
  (.write w " ")
  (print-dup (.reference e) w)
  (.write w ")"))

;; ---- Extension --------------------------------------------------------

(defn- map->Extension [x]
  (Extension/create x))

(def ^{:arglists '([x])} extension
  (let [intern (intern/intern-value map->Extension)]
    (fn [{:keys [id extension url value] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned value))
        (intern x)
        (Extension. id extension url value)))))

(extend-protocol p/FhirType
  Extension
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.value e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (-> (transient [])
        (into! (p/-references (.extension e)))
        (into! (p/-references (.value e)))
        (persistent!))))

(def-print-method-complex "Extension" "id" "extension" "url" "value")

(defmethod print-dup Extension [^Extension e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Extension. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.url e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- Coding ----------------------------------------------------------------

(defn- map->Coding [x]
  (Coding/create x))

(def coding
  (let [intern (intern/intern-value map->Coding)]
    (fn [{:keys [id extension system version code display userSelected] :as x}]
      (if (and (nil? id) (p/-interned extension))
        (intern x)
        (Coding. id extension system version code display userSelected)))))

(extend-protocol p/FhirType
  Coding
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Coding" "id" "extension" "system" "version" "code"
  "display" "userSelected")

(defmethod print-dup Coding [^Coding e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Coding. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.system e) w)
  (.write w " ")
  (print-dup (.version e) w)
  (.write w " ")
  (print-dup (.code e) w)
  (.write w " ")
  (print-dup (.display e) w)
  (.write w " ")
  (print-dup (.userSelected e) w)
  (.write w ")"))

;; ---- CodeableConcept -------------------------------------------------------

(defn- map->CodeableConcept [x]
  (CodeableConcept/create x))

(def ^{:arglists '([x])} codeable-concept
  (let [intern (intern/intern-value map->CodeableConcept)]
    (fn [{:keys [id extension coding text] :as x}]
      (if (and (nil? id) (p/-interned extension))
        (intern x)
        (CodeableConcept. id extension coding text)))))

(extend-protocol p/FhirType
  CodeableConcept
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "CodeableConcept" "id" "extension" "coding" "text")

(defmethod print-dup CodeableConcept [^CodeableConcept e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.CodeableConcept. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.coding e) w)
  (.write w " ")
  (print-dup (.text e) w)
  (.write w ")"))

;; ---- Quantity --------------------------------------------------------------

(defn- map->Quantity [x]
  (Quantity/create x))

(def ^{:arglists '([x])} quantity
  (let [intern (intern/intern-value map->Quantity)]
    (fn [{:keys [id extension value comparator unit system code] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned value)
               (p/-interned comparator) (p/-interned system) (p/-interned code))
        (intern x)
        (Quantity. id extension value comparator unit system code)))))

(extend-protocol p/FhirType
  Quantity
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.value e))
         (p/-interned (.comparator e)) (p/-interned (.system e))
         (p/-interned (.code e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Quantity" "id" "extension" "value" "comparator"
  "unit" "system" "code")

(defmethod print-dup Quantity [^Quantity e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Quantity. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w " ")
  (print-dup (.comparator e) w)
  (.write w ")")
  (print-dup (.unit e) w)
  (.write w ")")
  (print-dup (.system e) w)
  (.write w ")")
  (print-dup (.comparator e) w)
  (.write w ")"))

;; ---- Range -----------------------------------------------------------------

(defn- map->Range [x]
  (Range/create x))

(def ^{:arglists '([x])} range
  (let [intern (intern/intern-value map->Range)]
    (fn [{:keys [id extension low high] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned low)
               (p/-interned high))
        (intern x)
        (Range. id extension low high)))))

(extend-protocol p/FhirType
  Range
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.low e))
         (p/-interned (.high e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Range" "id" "extension" "low" "high")

(defmethod print-dup Range [^Range e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Range. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.low e) w)
  (.write w " ")
  (print-dup (.high e) w)
  (.write w ")"))

;; ---- Ratio -----------------------------------------------------------------

(defn- map->Ratio [x]
  (Ratio/create x))

(def ^{:arglists '([x])} ratio
  (let [intern (intern/intern-value map->Ratio)]
    (fn [{:keys [id extension numerator denominator] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned numerator)
               (p/-interned denominator))
        (intern x)
        (Ratio. id extension numerator denominator)))))

(extend-protocol p/FhirType
  Ratio
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.numerator e))
         (p/-interned (.denominator e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Ratio" "id" "extension" "numerator" "denominator")

(defmethod print-dup Ratio [^Ratio e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Ratio. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.numerator e) w)
  (.write w " ")
  (print-dup (.denominator e) w)
  (.write w ")"))

;; ---- Period ----------------------------------------------------------------

(defn- map->Period [x]
  (Period/create x))

(def ^{:arglists '([x])} period
  (let [intern (intern/intern-value map->Period)]
    (fn [{:keys [id extension start end] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned start)
               (p/-interned end))
        (intern x)
        (Period. id extension start end)))))

(extend-protocol p/FhirType
  Period
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.start e))
         (p/-interned (.end e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Period" "id" "extension" "start" "end")

(defmethod print-dup Period [^Period e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Period. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.start e) w)
  (.write w " ")
  (print-dup (.end e) w)
  (.write w ")"))

;; ---- Identifier ------------------------------------------------------------

(defn- map->Identifier [x]
  (Identifier/create x))

(def ^{:arglists '([x])} identifier
  (let [intern (intern/intern-value map->Identifier)]
    (fn [{:keys [id extension use type system value period assigner] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned use)
               (p/-interned type) (p/-interned value) (p/-interned period)
               (p/-interned assigner))
        (intern x)
        (Identifier. id extension use type system value period assigner)))))

(extend-protocol p/FhirType
  Identifier
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.use e))
         (p/-interned (.type e)) (p/-interned (.value e)) (p/-interned (.period e))
         (p/-interned (.assigner e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Identifier" "id" "extension" "use" "type" "system"
  "value" "period" "assigner")

(defmethod print-dup Identifier [^Identifier e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Identifier. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.use e) w)
  (.write w " ")
  (print-dup (.type e) w)
  (.write w " ")
  (print-dup (.system e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w " ")
  (print-dup (.period e) w)
  (.write w " ")
  (print-dup (.assigner e) w)
  (.write w ")"))

;; ---- HumanName -------------------------------------------------------------

(defn- map->HumanName [x]
  (HumanName/create x))

(def ^{:arglists '([x])} human-name
  (let [intern (intern/intern-value map->HumanName)]
    (fn [{:keys [id extension use text family given prefix suffix period] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned use)
               (p/-interned text) (p/-interned family) (p/-interned given)
               (p/-interned prefix) (p/-interned suffix) (p/-interned period))
        (intern x)
        (HumanName. id extension use text family given prefix suffix period)))))

(extend-protocol p/FhirType
  HumanName
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.use e))
         (p/-interned (.text e)) (p/-interned (.family e))
         (p/-interned (.given e)) (p/-interned (.prefix e))
         (p/-interned (.suffix e)) (p/-interned (.period e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "HumanName" "id" "extension" "use" "text" "family"
  "given" "prefix" "suffix" "period")

(defmethod print-dup HumanName [^HumanName e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.HumanName. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.use e) w)
  (.write w " ")
  (print-dup (.text e) w)
  (.write w " ")
  (print-dup (.family e) w)
  (.write w " ")
  (print-dup (.given e) w)
  (.write w " ")
  (print-dup (.prefix e) w)
  (.write w " ")
  (print-dup (.suffix e) w)
  (.write w " ")
  (print-dup (.period e) w)
  (.write w ")"))

;; ---- Address ---------------------------------------------------------------

(defn- map->Address [x]
  (Address/create x))

(def ^{:arglists '([x])} address
  (let [intern (intern/intern-value map->Address)]
    (fn [{:keys [id extension use type text line city district state postalCode
                 country period] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned use)
               (p/-interned type) (p/-interned text) (p/-interned line)
               (p/-interned city) (p/-interned district) (p/-interned state)
               (p/-interned postalCode) (p/-interned country) (p/-interned period))
        (intern x)
        (Address. id extension use type text line city district state postalCode
                  country period)))))

(extend-protocol p/FhirType
  Address
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.use e))
         (p/-interned (.type e)) (p/-interned (.text e)) (p/-interned (.line e))
         (p/-interned (.city e)) (p/-interned (.district e))
         (p/-interned (.state e)) (p/-interned (.postalCode e))
         (p/-interned (.country e)) (p/-interned (.period e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Address" "id" "extension" "use" "type" "text" "line"
  "city" "district" "state" "postalCode" "country" "period")

(defmethod print-dup Address [^Address e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Address. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.use e) w)
  (.write w " ")
  (print-dup (.type e) w)
  (.write w " ")
  (print-dup (.text e) w)
  (.write w " ")
  (print-dup (.line e) w)
  (.write w " ")
  (print-dup (.city e) w)
  (.write w " ")
  (print-dup (.district e) w)
  (.write w " ")
  (print-dup (.state e) w)
  (.write w " ")
  (print-dup (.postalCode e) w)
  (.write w " ")
  (print-dup (.country e) w)
  (.write w " ")
  (print-dup (.period e) w)
  (.write w ")"))

;; ---- Reference --------------------------------------------------------

(defn- valid-ref? [[type id]]
  (and (.matches (re-matcher #"[A-Z]([A-Za-z0-9_]){0,254}" type))
       (some->> id (re-matcher #"[A-Za-z0-9\-\.]{1,64}") .matches)))

(defn- reference-reference [ref]
  (let [ref (str/split ref #"/" 2)]
    (when (valid-ref? ref)
      [ref])))

(defn- map->Reference [x]
  (Reference/create x))

(def ^{:arglists '([x])} reference
  (let [intern (intern/intern-value map->Reference)]
    (fn [{:keys [id extension reference type identifier display] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned reference)
               (p/-interned type) (p/-interned identifier) (p/-interned display))
        (intern x)
        (Reference. id extension reference type identifier display)))))

(extend-protocol p/FhirType
  Reference
  (-type [r]
    (.fhirType r))
  (-interned [r]
    (and (nil? (.id r)) (p/-interned (.extension r))
         (p/-interned (.reference r)) (p/-interned (.type r))
         (p/-interned (.identifier r)) (p/-interned (.display r))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [r]
    (-> (transient (or (some-> (.reference r) :value reference-reference) []))
        (into! (p/-references (.extension r)))
        (into! (p/-references (.type r)))
        (into! (p/-references (.identifier r)))
        (into! (p/-references (.display r)))
        (persistent!))))

(def-print-method-complex "Reference" "id" "extension" "reference" "type"
  "identifier" "display")

(defmethod print-dup Reference [^Reference e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Reference. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.reference e) w)
  (.write w " ")
  (print-dup (.type e) w)
  (.write w " ")
  (print-dup (.identifier e) w)
  (.write w " ")
  (print-dup (.display e) w)
  (.write w ")"))

;; ---- Meta ------------------------------------------------------------------

(defn- map->Meta [x]
  (Meta/create x))

(def ^{:arglists '([x])} meta
  (let [intern (intern/intern-value map->Meta)]
    (fn [{:keys [id extension versionId lastUpdated source profile security tag] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned versionId)
               (p/-interned lastUpdated) (p/-interned source)
               (p/-interned profile) (p/-interned security) (p/-interned tag))
        (intern x)
        (Meta. id extension versionId lastUpdated source profile security tag)))))

(extend-protocol p/FhirType
  Meta
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.versionId e))
         (p/-interned (.lastUpdated e)) (p/-interned (.source e))
         (p/-interned (.profile e)) (p/-interned (.security e)) (p/-interned (.tag e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Meta" "id" "extension" "versionId" "lastUpdated" "source" "profile" "security" "tag")

(defmethod print-dup Meta [^Meta e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Meta. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.versionId e) w)
  (.write w " ")
  (print-dup (.lastUpdated e) w)
  (.write w " ")
  (print-dup (.source e) w)
  (.write w " ")
  (print-dup (.profile e) w)
  (.write w " ")
  (print-dup (.security e) w)
  (.write w " ")
  (print-dup (.tag e) w)
  (.write w ")"))

;; ---- BundleEntrySearch -----------------------------------------------------

(defn- map->BundleEntrySearch [x]
  (BundleEntrySearch/create x))

(re-matches #"[ \r\n\t\S]+" "𝗔𝗗𝗗𝗜𝗧𝗜𝗢𝗡𝗔𝗟")

(def ^{:arglists '([x])} bundle-entry-search
  (let [intern (intern/intern-value map->BundleEntrySearch)]
    (fn [{:keys [id extension mode score] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned mode)
               (p/-interned score))
        (intern x)
        (BundleEntrySearch. id extension mode score)))))

(extend-protocol p/FhirType
  BundleEntrySearch
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.mode e))
         (p/-interned (.score e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "BundleEntrySearch" "id" "extension" "mode" "score")

(defmethod print-dup BundleEntrySearch [^BundleEntrySearch e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.BundleEntrySearch. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.mode e) w)
  (.write w " ")
  (print-dup (.score e) w)
  (.write w ")"))

;; ---- Annotation ------------------------------------------------------------

(defn- map->Annotation [x]
  (Annotation/create x))

(def ^{:arglists '([x])} annotation
  (let [intern (intern/intern-value map->Annotation)]
    (fn [{:keys [id extension author time text] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned author)
               (p/-interned time) (p/-interned text))
        (intern x)
        (Annotation. id extension author time text)))))

(extend-protocol p/FhirType
  Annotation
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.author e))
         (p/-interned (.time e)) (p/-interned (.text e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Annotation" "id" "extension" "author" "time" "text")

(defmethod print-dup Annotation [^Annotation e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Annotation. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.author e) w)
  (.write w " ")
  (print-dup (.time e) w)
  (.write w " ")
  (print-dup (.text e) w)
  (.write w ")"))

;; ---- ContactDetail ------------------------------------------------------------

(defn- map->ContactDetail [x]
  (ContactDetail/create x))

(def ^{:arglists '([x])} contact-detail
  (let [intern (intern/intern-value map->ContactDetail)]
    (fn [{:keys [id extension name telecom] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned name)
               (p/-interned telecom))
        (intern x)
        (ContactDetail. id extension name telecom)))))

(extend-protocol p/FhirType
  ContactDetail
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.name e))
         (p/-interned (.telecom e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "ContactDetail" "id" "extension" "name" "telecom")

(defmethod print-dup ContactDetail [^ContactDetail e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.ContactDetail. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.name e) w)
  (.write w " ")
  (print-dup (.telecom e) w)
  (.write w ")"))

;; ---- ContactPoint ------------------------------------------------------------

(defn- map->ContactPoint [x]
  (ContactPoint/create x))

(def ^{:arglists '([x])} contact-point
  (let [intern (intern/intern-value map->ContactPoint)]
    (fn [{:keys [id extension system value use rank period] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned system)
               (p/-interned value) (p/-interned use) (p/-interned rank)
               (p/-interned period))
        (intern x)
        (ContactPoint. id extension system value use rank period)))))

(extend-protocol p/FhirType
  ContactPoint
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.system e))
         (p/-interned (.value e)) (p/-interned (.use e)) (p/-interned (.rank e))
         (p/-interned (.period e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "ContactPoint" "id" "extension" "system" "value" "use" "rank" "period")

(defmethod print-dup ContactPoint [^ContactPoint e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.ContactPoint. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.system e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w " ")
  (print-dup (.use e) w)
  (.write w " ")
  (print-dup (.rank e) w)
  (.write w " ")
  (print-dup (.period e) w)
  (.write w ")"))

;; ---- RelatedArtifact ------------------------------------------------------------

(defn- map->RelatedArtifact [x]
  (RelatedArtifact/create x))

(def ^{:arglists '([x])} related-artifact
  (let [intern (intern/intern-value map->RelatedArtifact)]
    (fn [{:keys [id extension type label display citation url document resource]
          :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned type)
               (p/-interned label) (p/-interned display) (p/-interned citation)
               (p/-interned url) (p/-interned document) (p/-interned resource))
        (intern x)
        (RelatedArtifact. id extension type label display citation url document
                          resource)))))

(extend-protocol p/FhirType
  RelatedArtifact
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.type e))
         (p/-interned (.label e)) (p/-interned (.display e))
         (p/-interned (.citation e)) (p/-interned (.url e))
         (p/-interned (.document e)) (p/-interned (.resource e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "RelatedArtifact" "id" "extension" "type" "label"
  "display" "citation" "url" "document" "resource")

(defmethod print-dup RelatedArtifact [^RelatedArtifact e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.RelatedArtifact. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.type e) w)
  (.write w " ")
  (print-dup (.label e) w)
  (.write w " ")
  (print-dup (.display e) w)
  (.write w " ")
  (print-dup (.citation e) w)
  (.write w " ")
  (print-dup (.url e) w)
  (.write w " ")
  (print-dup (.document e) w)
  (.write w " ")
  (print-dup (.resource e) w)
  (.write w ")"))
