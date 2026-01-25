(ns blaze.fhir.spec.generators
  (:refer-clojure :exclude [boolean count meta range str time])
  (:require
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system :as system]
   [blaze.util :refer [str]]
   [clojure.string :as str]
   [clojure.test.check.generators :as gen])
  (:import
   [com.google.common.base CaseFormat]))

(set! *warn-on-reflection* true)

(declare narrative)

(defn nilable [gen]
  (gen/one-of [gen (gen/return nil)]))

(defn rare-nil [gen]
  (gen/frequency [[99 gen] [1 (gen/return nil)]]))

(defn often-nil [gen]
  (gen/frequency [[99 (gen/return nil)] [1 gen]]))

(def boolean-value
  gen/boolean)

(def integer-value
  gen/small-integer)

(def long-value
  gen/large-integer)

(def ^:private non-latin-alphabetic-code-point
  (gen/such-that #(Character/isAlphabetic (long %)) (gen/choose 256 65535) 1000))

(def ^:private char-printable-whitespace
  (gen/fmap char (gen/one-of [(gen/choose 9 10) (gen/return 13) (gen/choose 32 126) (gen/choose 160 255) non-latin-alphabetic-code-point])))

(def ^:private char-printable-non-blank
  (gen/fmap char (gen/one-of [(gen/choose 33 126) (gen/choose 161 255) non-latin-alphabetic-code-point])))

(def string-value
  (gen/fmap str/join (gen/vector char-printable-whitespace)))

(def large-string-value
  (gen/fmap str/join (gen/vector char-printable-whitespace 5 100)))

(def decimal-value
  (gen/fmap #(BigDecimal/valueOf ^double %) (gen/double* {:infinite? false :NaN? false})))

(def uri-value
  (gen/fmap str/join (gen/vector char-printable-non-blank)))

(def url-value
  (gen/fmap str/join (gen/vector char-printable-non-blank)))

(def canonical-value
  (gen/fmap str/join (gen/vector char-printable-non-blank)))

(def base64Binary-value
  (->> (gen/vector gen/char-alphanumeric 4)
       (gen/fmap str/join)
       (gen/vector)
       (gen/fmap str/join)
       (gen/such-that (partial re-matches #"([0-9a-zA-Z\\+/=]{4})+"))))

(def year
  (gen/choose 1 9999))

(def month
  (gen/choose 1 12))

(def day
  (gen/choose 1 28))

(def hour
  (gen/choose 0 23))

(def minute
  (gen/choose 0 59))

(def time-second
  (gen/choose 0 59))

(def zone-offset
  (gen/fmap (partial apply format "%s%02d:00")
            (gen/tuple (gen/elements ["+" "-"]) (gen/choose 1 14))))

(def zone
  (gen/one-of [(gen/return "Z") zone-offset]))

(def instant-value
  (gen/fmap
   system/parse-date-time
   (gen/fmap (partial apply format "%04d-%02d-%02dT%02d:%02d:%02d%s")
             (gen/tuple year month day hour minute time-second zone))))

(def date-value
  (gen/fmap
   system/parse-date
   (gen/one-of
    [(gen/fmap (partial format "%04d") year)
     (gen/fmap (partial apply format "%04d-%02d")
               (gen/tuple year month))
     (gen/fmap (partial apply format "%04d-%02d-%02d")
               (gen/tuple year month day))])))

(defn dateTime-value [& {:keys [year] :or {year year}}]
  (gen/fmap
   system/parse-date-time
   (gen/one-of
    [(gen/fmap (partial format "%04d") year)
     (gen/fmap (partial apply format "%04d-%02d")
               (gen/tuple year month))
     (gen/fmap (partial apply format "%04d-%02d-%02d")
               (gen/tuple year month day))
     (gen/fmap (partial apply format "%04d-%02d-%02dT%02d:%02d:%02d%s")
               (gen/tuple year month day hour minute time-second zone))])))

(def time-value
  (gen/fmap
   system/parse-time
   (gen/fmap (partial apply format "%02d:%02d:%02d")
             (gen/tuple hour minute time-second))))

(def code-value
  (gen/fmap str/join (gen/vector char-printable-whitespace)))

(def char-digit
  (gen/fmap char (gen/choose 48 57)))

(def oid-value
  (gen/such-that (partial re-matches #"urn:oid:[0-2](\.(0|[1-9][0-9]*))+")
                 (gen/fmap (partial str "urn:oid:0.")
                           (gen/fmap str/join (gen/vector char-digit)))))

(def id-value
  (gen/such-that (partial re-matches #"[A-Za-z0-9\-\ .]{1,64}")
                 (gen/fmap str/join (gen/vector gen/char-alphanumeric 1 64))
                 1000))

(def markdown-value
  (gen/fmap str/join (gen/vector char-printable-whitespace)))

(def unsignedInt-value
  gen/nat)

(def positiveInt-value
  (gen/fmap inc gen/nat))

(def uuid-value
  (gen/fmap (partial str "urn:uuid:") gen/uuid))

(def xhtml-value
  (->> (gen/such-that (comp pos? clojure.core/count) gen/string-alphanumeric)
       (gen/fmap #(str "<div xmlns=\"http://www.w3.org/1999/xhtml\">" % "</div>"))))

(defn- keep-vals [m]
  (reduce-kv
   (fn [ret k v]
     (if (or (nil? v) (and (vector? v) (empty? v)))
       (dissoc ret k)
       ret))
   m
   m))

(defn- to-map [keys vals]
  (gen/such-that seq (gen/fmap #(keep-vals (zipmap keys %)) vals) 1000))

(declare extension)

(defn extensions [& {:as gens}]
  (gen/vector (extension gens) 0 3))

(defn- primitive-gen [constructor value-gen]
  (fn
    [& {:keys [id extension value]
        :or {id (often-nil id-value)
             extension (often-nil (extensions))
             value (nilable value-gen)}}]
    (->> (gen/tuple id extension value)
         (to-map [:id :extension :value])
         (gen/fmap constructor))))

(def boolean
  (primitive-gen type/boolean boolean-value))

(def integer
  (primitive-gen type/integer integer-value))

(def string
  (primitive-gen type/string string-value))

(def large-string
  (primitive-gen type/string large-string-value))

(def decimal
  (primitive-gen type/decimal decimal-value))

(def uri
  (primitive-gen type/uri uri-value))

(def url
  (primitive-gen type/url url-value))

(def canonical
  (primitive-gen type/canonical canonical-value))

(def base64Binary
  (primitive-gen type/base64Binary base64Binary-value))

(def instant
  (primitive-gen type/instant instant-value))

(def date
  (primitive-gen type/date date-value))

(def dateTime
  (primitive-gen type/dateTime (dateTime-value)))

(def time
  (primitive-gen type/time time-value))

(def code
  (primitive-gen type/code code-value))

(def id
  (primitive-gen type/id id-value))

(def oid
  (primitive-gen type/oid oid-value))

(def markdown
  (primitive-gen type/markdown markdown-value))

(def unsignedInt
  (primitive-gen type/unsignedInt unsignedInt-value))

(def positiveInt
  (primitive-gen type/positiveInt positiveInt-value))

(def uuid
  (primitive-gen type/uuid uuid-value))

(def xhtml
  (primitive-gen type/xhtml xhtml-value))

(declare coding)
(declare contact-point)
(declare data-requirement)
(declare dosage)
(declare duration)
(declare human-name)
(declare identifier)
(declare meta)
(declare money)
(declare parameter-definition)
(declare period)
(declare quantity)
(declare range)
(declare ratio)
(declare reference)
(declare related-artifact)
(declare sampled-data)
(declare signature)
(declare timing)
(declare trigger-definition)
(declare usage-context)

(defn address
  [& {:keys [id extension use type text line city district state postalCode
             country period]
      :or {id (often-nil id-value)
           extension (extensions)
           use (rare-nil (code))
           type (rare-nil (code))
           text (often-nil (string))
           line (gen/vector (string) 0 5)
           city (rare-nil (string))
           district (rare-nil (string))
           state (rare-nil (string))
           postalCode (rare-nil (string))
           country (rare-nil (string))
           period (often-nil (period))}}]
  (->> (gen/tuple id extension use type text line city district state postalCode
                  country period)
       (to-map [:id :extension :use :type :text :line :city :district :state
                :postalCode :country :period])
       (gen/fmap type/address)))

(defn age
  [& {:keys [id extension value comparator unit system code]
      :or {id (often-nil id-value)
           extension (extensions)
           value (rare-nil (decimal))
           comparator (often-nil (code))
           unit (rare-nil (string))
           system (rare-nil (uri))
           code (rare-nil (code))}}]
  (->> (gen/tuple id extension value comparator unit system code)
       (to-map [:id :extension :value :comparator :unit :system :code])
       (gen/fmap type/age)))

(defn annotation
  [& {:keys [id extension author time text]
      :or {id (often-nil id-value)
           extension (gen/return nil)
           author (rare-nil (gen/one-of [(reference) (string)]))
           time (rare-nil (dateTime))
           text (rare-nil (markdown))}}]
  (->> (gen/tuple id extension author time text)
       (to-map [:id :extension :author :time :text])
       (gen/fmap type/annotation)))

(defn attachment
  [& {:keys [id extension contentType language data url size hash title creation]
      :or {id (often-nil id-value)
           extension (gen/return nil)
           contentType (rare-nil (code))
           language (nilable (code))
           data (rare-nil (base64Binary))
           url (often-nil (url))
           size (often-nil (unsignedInt))
           hash (often-nil (base64Binary))
           title (often-nil (string))
           creation (often-nil (dateTime))}}]
  (->> (gen/tuple id extension contentType language data url size hash title
                  creation)
       (to-map [:id :extension :contentType :language :data :url :size :hash
                :title :creation])
       (gen/fmap type/attachment)))

(defn codeable-concept
  [& {:keys [id extension coding text]
      :or {id (often-nil id-value)
           extension (extensions)
           coding (gen/vector (coding) 0 5)
           text (often-nil (string))}}]
  (->> (gen/tuple id extension coding text)
       (to-map [:id :extension :coding :text])
       (gen/fmap type/codeable-concept)))

(defn coding
  [& {:keys [id extension system version code display user-selected]
      :or {id (often-nil id-value)
           extension (extensions)
           system (rare-nil (uri))
           version (often-nil (string))
           code (rare-nil (blaze.fhir.spec.generators/code))
           display (often-nil (string))
           user-selected (often-nil (boolean))}}]
  (->> (gen/tuple id extension system version code display user-selected)
       (to-map [:id :extension :system :version :code :display :userSelected])
       (gen/fmap type/coding)))

(defn contact-detail
  [& {:keys [id extension name telecom]
      :or {id (often-nil id-value)
           extension (extensions)
           name (rare-nil (string))
           telecom (gen/vector (contact-point) 0 3)}}]
  (->> (gen/tuple id extension name telecom)
       (to-map [:id :extension :name :telecom])
       (gen/fmap type/contact-detail)))

(defn contact-point
  [& {:keys [id extension system value use rank period]
      :or {id (often-nil id-value)
           extension (extensions)
           system (rare-nil (code))
           value (rare-nil (string))
           use (nilable (code))
           rank (often-nil (positiveInt))
           period (rare-nil (period))}}]
  (->> (gen/tuple id extension system value use rank period)
       (to-map [:id :extension :system :value :use :rank :period])
       (gen/fmap type/contact-point)))

(defn contributor
  [& {:keys [id extension type name contact]
      :or {id (often-nil id-value)
           extension (extensions)
           type (rare-nil (code))
           name (rare-nil (string))
           contact (gen/vector (contact-detail) 0 3)}}]
  (->> (gen/tuple id extension type name contact)
       (to-map [:id :extension :type :name :contact])
       (gen/fmap type/contributor)))

(defn count
  [& {:keys [id extension value comparator unit system code]
      :or {id (often-nil id-value)
           extension (extensions)
           value (rare-nil (decimal))
           comparator (often-nil (code))
           unit (rare-nil (string))
           system (rare-nil (uri))
           code (rare-nil (code))}}]
  (->> (gen/tuple id extension value comparator unit system code)
       (to-map [:id :extension :value :comparator :unit :system :code])
       (gen/fmap type/count)))

(defn data-requirement-code-filter
  [& {:keys [id extension path searchParam valueSet code]
      :or {id (often-nil id-value)
           extension (extensions)
           path (nilable (string))
           searchParam (nilable (string))
           valueSet (nilable (canonical))
           code (gen/vector (coding) 0 5)}}]
  (->> (gen/tuple id extension path searchParam valueSet code)
       (to-map [:id :extension :path :searchParam :valueSet :code])
       (gen/fmap type/data-requirement-code-filter)))

(defn data-requirement-date-filter
  [& {:keys [id extension path searchParam value]
      :or {id (often-nil id-value)
           extension (extensions)
           path (nilable (string))
           searchParam (nilable (string))
           value (nilable (gen/one-of [(dateTime) (period) (duration)]))}}]
  (->> (gen/tuple id extension path searchParam value)
       (to-map [:id :extension :path :searchParam :value])
       (gen/fmap type/data-requirement-date-filter)))

(defn data-requirement-sort
  [& {:keys [id extension path direction]
      :or {id (often-nil id-value)
           extension (extensions)
           path (string)
           direction (code)}}]
  (->> (gen/tuple id extension path direction)
       (to-map [:id :extension :path :direction])
       (gen/fmap type/data-requirement-sort)))

(defn data-requirement
  [& {:keys [id extension type profile subject mustSupport codeFilter dateFilter
             limit sort]
      :or {id (often-nil id-value)
           extension (extensions)
           type (code)
           profile (gen/vector (canonical) 0 5)
           subject (nilable (gen/one-of [(codeable-concept) (reference)]))
           mustSupport (gen/vector (string) 0 5)
           codeFilter (gen/vector (data-requirement-code-filter) 0 5)
           dateFilter (gen/vector (data-requirement-date-filter) 0 5)
           limit (nilable (positiveInt))
           sort (gen/vector (data-requirement-sort) 0 5)}}]
  (->> (gen/tuple id extension type profile subject mustSupport codeFilter
                  dateFilter limit sort)
       (to-map [:id :extension :type :profile :subject :mustSupport :codeFilter
                :dateFilter :limit :sort])
       (gen/fmap type/data-requirement)))

(defn distance
  [& {:keys [id extension value comparator unit system code]
      :or {id (often-nil id-value)
           extension (extensions)
           value (rare-nil (decimal))
           comparator (often-nil (code))
           unit (rare-nil (string))
           system (rare-nil (uri))
           code (rare-nil (code))}}]
  (->> (gen/tuple id extension value comparator unit system code)
       (to-map [:id :extension :value :comparator :unit :system :code])
       (gen/fmap type/distance)))

(defn duration
  [& {:keys [id extension value comparator unit system code]
      :or {id (often-nil id-value)
           extension (extensions)
           value (rare-nil (decimal))
           comparator (often-nil (code))
           unit (rare-nil (string))
           system (rare-nil (uri))
           code (rare-nil (code))}}]
  (->> (gen/tuple id extension value comparator unit system code)
       (to-map [:id :extension :value :comparator :unit :system :code])
       (gen/fmap type/duration)))

(defn expression
  [& {:keys [id extension description name language expression reference]
      :or {id (often-nil id-value)
           extension (extensions)
           description (often-nil (string))
           name (often-nil (blaze.fhir.spec.generators/id))
           language (rare-nil (code))
           expression (rare-nil (string))
           reference (often-nil (uri))}}]
  (->> (gen/tuple id extension description name language expression reference)
       (to-map [:id :extension :description :name :language :expression :reference])
       (gen/fmap type/expression)))

(defn extension-value []
  (gen/one-of
   [(base64Binary)
    (boolean)
    (canonical)
    (code)
    (date)
    (dateTime)
    (decimal)
    (id)
    (instant)
    (integer)
    (markdown)
    (oid)
    (positiveInt)
    (string)
    (time)
    (unsignedInt)
    (uri)
    (url)
    (uuid)
    (address)
    (age)
    (annotation)
    (attachment)
    (codeable-concept)
    (coding)
    (contact-point)
    (count)
    (data-requirement)
    (distance)
    (duration)
    (human-name)
    (identifier)
    (money)
    (period)
    (quantity)
    (range)
    (ratio)
    (reference)
    (sampled-data)
    (signature)
    (timing)
    (contact-detail)
    (contributor)
    (expression)
    (parameter-definition)
    (related-artifact)
    (trigger-definition)
    (usage-context)
    (dosage)
    (meta)]))

(defn extension
  [& {:keys [id extension value]
      :or {id (often-nil id-value)
           extension (gen/return nil)
           value (gen/return nil)}}]
  (->> (gen/tuple id extension uri-value value)
       (to-map [:id :extension :url :value])
       (gen/fmap type/extension)))

(defn dosage-dose-and-rate
  [& {:keys [id extension type dose rate]
      :or {id (often-nil id-value)
           extension (extensions)
           type (nilable (codeable-concept))
           dose (nilable (gen/one-of [(range) (quantity)]))
           rate (nilable (gen/one-of [(ratio) (range) (quantity)]))}}]
  (->> (gen/tuple id extension type dose rate)
       (to-map [:id :extension :type :dose :rate])
       (gen/fmap type/dosage-dose-and-rate)))

(defn dosage
  [& {:keys [id extension modifierExtension sequence text additionalInstruction
             patientInstruction timing asNeeded site route method doseAndRate
             maxDosePerPeriod maxDosePerAdministration maxDosePerLifetime]
      :or {id (often-nil id-value)
           extension (extensions)
           modifierExtension (extensions)
           sequence (nilable (integer))
           text (nilable (string))
           additionalInstruction (gen/vector (codeable-concept) 0 5)
           patientInstruction (nilable (string))
           timing (nilable (timing))
           asNeeded (nilable (gen/one-of [(boolean) (codeable-concept)]))
           site (nilable (codeable-concept))
           route (nilable (codeable-concept))
           method (nilable (codeable-concept))
           doseAndRate (gen/vector (dosage-dose-and-rate) 0 5)
           maxDosePerPeriod (nilable (ratio))
           maxDosePerAdministration (nilable (quantity))
           maxDosePerLifetime (nilable (quantity))}}]
  (->> (gen/tuple id extension modifierExtension sequence text additionalInstruction
                  patientInstruction timing asNeeded site route method doseAndRate
                  maxDosePerPeriod maxDosePerAdministration maxDosePerLifetime)
       (to-map [:id :extension :modifierExtension :sequence :text :additionalInstruction
                :patientInstruction :timing :asNeeded :site :route :method :doseAndRate
                :maxDosePerPeriod :maxDosePerAdministration :maxDosePerLifetime])
       (gen/fmap type/dosage)))

(defn human-name
  [& {:keys [id extension use text family given prefix suffix period]
      :or {id (often-nil id-value)
           extension (extensions)
           use (rare-nil (code))
           text (often-nil (string))
           family (rare-nil (string))
           given (gen/vector (string) 0 3)
           prefix (gen/vector (string) 0 3)
           suffix (gen/vector (string) 0 3)
           period (often-nil (period))}}]
  (->> (gen/tuple id extension use text family given prefix suffix period)
       (to-map [:id :extension :use :text :family :given :prefix :suffix :period])
       (gen/fmap type/human-name)))

(defn identifier
  [& {:keys [id extension use type system value period assigner]
      :or {id (often-nil id-value)
           extension (extensions)
           use (rare-nil (code))
           type (nilable (codeable-concept))
           system (rare-nil (uri))
           value (rare-nil (string))
           period (often-nil (period))
           assigner (gen/return nil)}}]
  (->> (gen/tuple id extension use type system value period assigner)
       (to-map [:id :extension :use :type :system :value :period :assigner])
       (gen/fmap type/identifier)))

(defn meta
  [& {:keys [id extension versionId lastUpdated source profile security tag]
      :or {id (often-nil id-value)
           extension (extensions)
           versionId (rare-nil (blaze.fhir.spec.generators/id))
           lastUpdated (rare-nil (instant))
           source (nilable (uri))
           profile (gen/vector (canonical) 0 5)
           security (gen/vector (coding) 0 5)
           tag (gen/vector (coding) 0 5)}}]
  (->> (gen/tuple id extension versionId lastUpdated source profile security tag)
       (to-map [:id :extension :versionId :lastUpdated :source :profile
                :security :tag])
       (gen/fmap type/meta)))

(defn money
  [& {:keys [id extension value currency]
      :or {id (often-nil id-value)
           extension (extensions)
           value (rare-nil (decimal))
           currency (rare-nil (code))}}]
  (->> (gen/tuple id extension value currency)
       (to-map [:id :extension :value :currency])
       (gen/fmap type/money)))

(defn narrative
  [& {:keys [id extension status div]
      :or {id (often-nil id-value)
           extension (extensions)
           status (rare-nil (code))
           div (xhtml :value xhtml-value)}}]
  (->> (gen/tuple id extension status div)
       (to-map [:id :extension :status :div])
       (gen/fmap type/narrative)))

(defn parameter-definition
  [& {:keys [id extension name use min max documentation type profile]
      :or {id (often-nil id-value)
           extension (extensions)
           name (rare-nil (code))
           use (rare-nil (code))
           min (rare-nil (integer))
           max (rare-nil (string))
           documentation (rare-nil (string))
           type (rare-nil (code))
           profile (rare-nil (canonical))}}]
  (->> (gen/tuple id extension name use min max documentation type profile)
       (to-map [:id :extension :name :use :min :max :documentation :type :profile])
       (gen/fmap type/parameter-definition)))

(defn period
  [& {:keys [id extension start end]
      :or {id (often-nil id-value)
           extension (extensions)
           start (nilable (dateTime))
           end (nilable (dateTime))}}]
  (as-> (gen/tuple id extension start end) x
    (to-map [:id :extension :start :end] x)
    (gen/such-that #(<= (system/date-time-lower-bound (:value (:start %)))
                        (system/date-time-upper-bound (:value (:end %))))
                   x
                   1000)
    (gen/fmap type/period x)))

(defn quantity
  [& {:keys [id extension value comparator unit system code]
      :or {id (often-nil id-value)
           extension (extensions)
           value (rare-nil (decimal))
           comparator (often-nil (code))
           unit (rare-nil (string))
           system (rare-nil (uri))
           code (rare-nil (code))}}]
  (->> (gen/tuple id extension value comparator unit system code)
       (to-map [:id :extension :value :comparator :unit :system :code])
       (gen/fmap type/quantity)))

(defn range
  [& {:keys [id extension low high]
      :or {id (often-nil id-value)
           extension (extensions)
           low (nilable (quantity))
           high (nilable (quantity))}}]
  (->> (gen/tuple id extension low high)
       (to-map [:id :extension :low :high])
       (gen/fmap type/range)))

(defn ratio
  [& {:keys [id extension numerator denominator]
      :or {id (often-nil id-value)
           extension (extensions)
           numerator (nilable (quantity))
           denominator (nilable (quantity))}}]
  (->> (gen/tuple id extension numerator denominator)
       (to-map [:id :extension :numerator :denominator])
       (gen/fmap type/ratio)))

(defn reference
  [& {:keys [id extension reference type identifier display]
      :or {id (often-nil id-value)
           extension (extensions)
           reference (rare-nil (string))
           type (often-nil (uri))
           identifier (often-nil (identifier))
           display (often-nil (string))}}]
  (->> (gen/tuple id extension reference type identifier display)
       (to-map [:id :extension :reference :type :identifier :display])
       (gen/fmap type/reference)))

(defn related-artifact
  [& {:keys [id extension type label display citation url document resource]
      :or {id (often-nil id-value)
           extension (extensions)
           type (rare-nil (code))
           label (nilable (string))
           display (nilable (string))
           citation (often-nil (markdown))
           url (nilable (url))
           document (nilable (attachment))
           resource (nilable (canonical))}}]
  (->> (gen/tuple id extension type label display citation url document resource)
       (to-map [:id :extension :type :label :display :citation :url :document :resource])
       (gen/fmap type/related-artifact)))

(defn sampled-data
  [& {:keys [id extension origin period factor lowerLimit upperLimit dimensions data]
      :or {id (often-nil id-value)
           extension (extensions)
           origin (quantity :comparator (gen/return nil))
           period (decimal)
           factor (nilable (decimal))
           lowerLimit (nilable (decimal))
           upperLimit (nilable (decimal))
           dimensions (positiveInt)
           data (nilable (string))}}]
  (->> (gen/tuple id extension origin period factor lowerLimit upperLimit dimensions data)
       (to-map [:id :extension :origin :period :factor :lowerLimit :upperLimit :dimensions :data])
       (gen/fmap type/sampled-data)))

(defn signature
  [& {:keys [id extension type when who onBehalfOf targetFormat sigFormat data]
      :or {id (often-nil id-value)
           extension (extensions)
           type (gen/vector (coding) 0 5)
           when (rare-nil (instant))
           who (rare-nil (reference))
           onBehalfOf (nilable (reference))
           targetFormat (nilable (code))
           sigFormat (nilable (code))
           data (nilable (base64Binary))}}]
  (->> (gen/tuple id extension type when who onBehalfOf targetFormat sigFormat data)
       (to-map [:id :extension :type :when :who :onBehalfOf :targetFormat :sigFormat :data])
       (gen/fmap type/signature)))

(defn timing-repeat-bounds []
  (gen/one-of [(duration) (range) (period)]))

(defn timing-repeat
  [& {:keys [id extension bounds count countMax duration durationMax durationUnit
             frequency frequencyMax period periodMax periodUnit dayOfWeek timeOfDay
             when offset]
      :or {id (often-nil id-value)
           extension (extensions)
           bounds (nilable (timing-repeat-bounds))
           count (nilable (positiveInt))
           countMax (nilable (positiveInt))
           duration (nilable (decimal))
           durationMax (nilable (decimal))
           durationUnit (nilable (code))
           frequency (nilable (positiveInt))
           frequencyMax (nilable (positiveInt))
           period (nilable (decimal))
           periodMax (nilable (decimal))
           periodUnit (nilable (code))
           dayOfWeek (gen/vector (code) 0 5)
           timeOfDay (gen/vector (time) 0 5)
           when (gen/vector (code) 0 5)
           offset (nilable (unsignedInt))}}]
  (->> (gen/tuple id extension bounds count countMax duration durationMax
                  durationUnit frequency frequencyMax period periodMax periodUnit
                  dayOfWeek timeOfDay when offset)
       (to-map [:id :extension :bounds :count :countMax :duration :durationMax
                :durationUnit :frequency :frequencyMax :period :periodMax
                :periodUnit :dayOfWeek :timeOfDay :when :offset])
       (gen/fmap type/timing-repeat)))

(defn timing
  [& {:keys [id extension modifierExtension event repeat code]
      :or {id (often-nil id-value)
           extension (extensions)
           modifierExtension (extensions)
           event (gen/vector (dateTime) 0 5)
           repeat (nilable (timing-repeat))
           code (nilable (codeable-concept))}}]
  (->> (gen/tuple id extension modifierExtension event repeat code)
       (to-map [:id :extension :modifierExtension :event :repeat :code])
       (gen/fmap type/timing)))

(defn- trigger-definition-timing []
  (gen/one-of [(timing) (reference) (date) (dateTime)]))

(defn trigger-definition
  [& {:keys [id extension type name timing data condition]
      :or {id (often-nil id-value)
           extension (extensions)
           type (code)
           name (nilable (string))
           timing (nilable (trigger-definition-timing))
           data (gen/vector (data-requirement) 0 5)
           condition (nilable (expression))}}]
  (->> (gen/tuple id extension type name timing data condition)
       (to-map [:id :extension :type :name :timing :data :condition])
       (gen/fmap type/trigger-definition)))

(defn- usage-context-value []
  (gen/one-of [(codeable-concept) (quantity) (range) (reference)]))

(defn usage-context
  [& {:keys [id extension code value]
      :or {id (often-nil id-value)
           extension (extensions)
           code (coding)
           value (usage-context-value)}}]
  (->> (gen/tuple id extension code value)
       (to-map [:id :extension :code :value])
       (gen/fmap type/usage-context)))

(defn bundle-entry-search
  [& {:keys [id extension mode score]
      :or {id (often-nil id-value)
           extension (extensions)
           mode (rare-nil (code))
           score (often-nil (decimal))}}]
  (->> (gen/tuple id extension mode score)
       (to-map [:id :extension :mode :score])
       (gen/fmap type/bundle-entry-search)))

(defn- fhir-type [fhir-type gen]
  (gen/fmap #(assoc % :fhir/type fhir-type) gen))

(defn bundle-entry
  [& {:keys [id extension fullUrl resource]
      :or {id (often-nil id-value)
           extension (extensions)
           fullUrl (uri)
           resource (gen/return nil)}}]
  (->> (gen/tuple id extension fullUrl resource)
       (to-map [:id :extension :fullUrl :resource])
       (fhir-type :fhir.Bundle/entry)))

(defn kebab->pascal [s]
  (.to CaseFormat/LOWER_HYPHEN CaseFormat/UPPER_CAMEL s))

(defmacro def-resource-gen [type [& fields]]
  (let [fields (partition 2 fields)
        field-syms (map first fields)]
    `(defn ~type [& {:keys [~@field-syms]
                     :or ~(into {} (map vec) fields)}]
       (->> (gen/tuple ~@field-syms)
            (to-map [~@(map keyword field-syms)])
            (fhir-type ~(keyword "fhir" (kebab->pascal (str type))))))))

(def-resource-gen activity-definition
  [id id-value
   meta (rare-nil (meta))
   text (often-nil (narrative))
   identifier (gen/vector (identifier) 0 5)
   relatedArtifact (gen/vector (related-artifact) 0 5)
   timing (nilable (gen/one-of [(timing) (dateTime) (age) (period) (range) (duration)]))
   dosage (gen/vector (dosage) 0 5)])

(def-resource-gen allergy-intolerance
  [id id-value
   meta (rare-nil (meta))
   text (often-nil (narrative))
   category (gen/vector (code) 0 5)])

(def-resource-gen bundle
  [id id-value
   meta (rare-nil (meta))
   identifier (identifier)
   type (rare-nil (code))
   entry (gen/vector (bundle-entry) 0 5)])

(def-resource-gen capability-statement
  [id id-value
   meta (rare-nil (meta))
   text (often-nil (narrative))
   status (rare-nil (code))
   date (nilable (dateTime))
   kind (rare-nil (code))
   fhirVersion (rare-nil (code))
   format (gen/vector (code) 0 5)])

(def-resource-gen claim
  [id id-value
   meta (rare-nil (meta))
   text (often-nil (narrative))
   identifier (gen/vector (identifier) 0 5)
   status (rare-nil (code))
   total (nilable (money))])

(defn- code-system-concept
  [& {:keys [code]
      :or {code (code)}}]
  (->> (gen/tuple code)
       (to-map [:code])
       (fhir-type :fhir.CodeSystem/concept)))

(def-resource-gen code-system
  [id id-value
   meta (rare-nil (meta))
   text (often-nil (narrative))
   url (uri)
   identifier (gen/vector (identifier) 0 5)
   version (rare-nil (string))
   name (nilable (string))
   title (nilable (string))
   status (rare-nil (code))
   experimental (nilable (boolean))
   concept (gen/vector (code-system-concept) 0 5)])

(defn condition-onset []
  (gen/one-of [(dateTime) (age) (period) (range) (string)]))

(def-resource-gen condition
  [id id-value
   meta (rare-nil (meta))
   text (often-nil (narrative))
   identifier (gen/vector (identifier) 0 5)
   onset (nilable (condition-onset))])

(defn consent-policy
  [& {:keys [id extension authority uri]
      :or {id (often-nil id-value)
           extension (extensions)
           authority (nilable (uri))
           uri (nilable (uri))}}]
  (->> (gen/tuple id extension authority uri)
       (to-map [:id :extension :authority :uri])
       (fhir-type :fhir.Consent/policy)))

(defn consent-verification
  [& {:keys [id extension verified verifiedWith verificationDate]
      :or {id (often-nil id-value)
           extension (extensions)
           verified (boolean)
           verifiedWith (nilable (reference))
           verificationDate (nilable (dateTime))}}]
  (->> (gen/tuple id extension verified verifiedWith verificationDate)
       (to-map [:id :extension :verified :verifiedWith :verificationDate])
       (fhir-type :fhir.Consent/verification)))

(defn consent-provision-actor
  [& {:keys [id extension role reference]
      :or {id (often-nil id-value)
           extension (extensions)
           role (codeable-concept)
           reference (reference)}}]
  (->> (gen/tuple id extension role reference)
       (to-map [:id :extension :role :reference])
       (fhir-type :fhir.Consent.provision/actor)))

(defn consent-provision-data
  [& {:keys [id extension meaning reference]
      :or {id (often-nil id-value)
           extension (extensions)
           meaning (code)
           reference (reference)}}]
  (->> (gen/tuple id extension meaning reference)
       (to-map [:id :extension :meaning :reference])
       (fhir-type :fhir.Consent.provision/data)))

(defn consent-provision
  [& {:keys [id extension type period actor action securityLabel purpose class
             code dataPeriod data]
      :or {id (often-nil id-value)
           extension (extensions)
           type (nilable (code))
           period (nilable (period))
           actor (gen/vector (consent-provision-actor) 0 5)
           action (gen/vector (codeable-concept) 0 5)
           securityLabel (gen/vector (coding) 0 5)
           purpose (gen/vector (coding) 0 5)
           class (gen/vector (coding) 0 5)
           code (gen/vector (codeable-concept) 0 5)
           dataPeriod (nilable (blaze.fhir.spec.generators/period))
           data (gen/vector (consent-provision-data) 0 5)}}]
  (->> (gen/tuple id extension type period actor action securityLabel purpose
                  class code dataPeriod data)
       (to-map [:id :extension :type :period :actor :action :securityLabel
                :purpose :class :code :dataPeriod :data :provision])
       (fhir-type :fhir.Consent/provision)))

(def-resource-gen consent
  [id id-value
   meta (rare-nil (meta))
   text (often-nil (narrative))
   identifier (gen/vector (identifier) 0 5)
   status (code)
   policy (gen/vector (consent-policy) 0 5)
   policyRule (nilable (codeable-concept))
   verification (gen/vector (consent-verification) 0 5)
   provision (nilable (consent-provision {:provision (gen/vector (consent-provision) 0 5)}))])

(def-resource-gen diagnostic-report
  [id id-value
   meta (rare-nil (meta))
   text (often-nil (narrative))
   identifier (gen/vector (identifier) 0 5)
   status (rare-nil (code))
   category (gen/vector (codeable-concept) 0 5)
   code (rare-nil (codeable-concept))
   subject (rare-nil (reference :reference (gen/return nil)))
   encounter (rare-nil (reference :reference (gen/return nil)))
   effective (rare-nil (gen/one-of [(dateTime) (period)]))
   issued (nilable (instant))
   performer (gen/vector (reference :reference (gen/return nil)) 0 5)])

(def-resource-gen encounter
  [id id-value
   meta (rare-nil (meta))
   text (often-nil (narrative))
   identifier (gen/vector (identifier) 0 5)
   status (rare-nil (code))
   type (gen/vector (codeable-concept) 0 5)
   priority (rare-nil (codeable-concept))
   subject (rare-nil (reference :reference (gen/return nil)))
   period (rare-nil (period))])

(defn- imaging-study-series-instance
  [& {:keys [id extension modifierExtension uid sopClass number title]
      :or {id (often-nil id-value)
           extension (extensions)
           modifierExtension (extensions)
           uid (rare-nil (blaze.fhir.spec.generators/id))
           sopClass (rare-nil (coding))
           number (nilable (unsignedInt))
           title (nilable (string))}}]
  (->> (gen/tuple id extension modifierExtension uid sopClass number title)
       (to-map [:id :extension :modifierExtension :uid :sopClass :number :title])
       (fhir-type :fhir.ImagingStudy.series/instance)))

(defn- imaging-study-series-performer
  [& {:keys [id extension modifierExtension function actor]
      :or {id (often-nil id-value)
           extension (extensions)
           modifierExtension (extensions)
           function (nilable (codeable-concept))
           actor (reference)}}]
  (->> (gen/tuple id extension modifierExtension function actor)
       (to-map [:id :extension :modifierExtension :function :actor])
       (fhir-type :fhir.ImagingStudy.series/performer)))

(defn- imaging-study-series
  [& {:keys [id extension modifierExtension uid number modality description
             numberOfInstances endpoint bodySite laterality specimen started
             performer instance]
      :or {id (often-nil id-value)
           extension (extensions)
           modifierExtension (extensions)
           uid (rare-nil (blaze.fhir.spec.generators/id))
           number (nilable (unsignedInt))
           modality (coding)
           description (nilable (string))
           numberOfInstances (nilable (unsignedInt))
           endpoint (gen/vector (reference) 0 5)
           bodySite (nilable (coding))
           laterality (nilable (coding))
           specimen (gen/vector (reference) 0 5)
           started (nilable (dateTime))
           performer (gen/vector (imaging-study-series-performer) 0 5)
           instance (gen/vector (imaging-study-series-instance) 0 5)}}]
  (->> (gen/tuple id extension modifierExtension uid number modality description
                  numberOfInstances endpoint bodySite laterality specimen started
                  performer instance)
       (to-map [:id :extension :modifierExtension :uid :number :modality :description
                :numberOfInstances :endpoint :bodySite :laterality :specimen :started
                :performer :instance])
       (fhir-type :fhir.ImagingStudy/series)))

(def-resource-gen imaging-study
  [id id-value
   meta (rare-nil (meta))
   text (often-nil (narrative))
   identifier (gen/vector (identifier) 0 5)
   status (rare-nil (code))
   modality (gen/vector (coding) 0 5)
   subject (rare-nil (reference :reference (gen/return nil)))
   encounter (nilable (reference :reference (gen/return nil)))
   started (nilable (dateTime))
   basedOn (gen/vector (reference :reference (gen/return nil)) 0 5)
   referrer (nilable (reference :reference (gen/return nil)))
   interpreter (gen/vector (reference :reference (gen/return nil)) 0 5)
   endpoint (gen/vector (reference :reference (gen/return nil)) 0 5)
   numberOfSeries (nilable (unsignedInt))
   numberOfInstances (nilable (unsignedInt))
   procedureReference (nilable (reference :reference (gen/return nil)))
   procedureCode (gen/vector (codeable-concept) 0 5)
   location (nilable (reference :reference (gen/return nil)))
   reasonCode (gen/vector (codeable-concept) 0 5)
   reasonReference (gen/vector (reference :reference (gen/return nil)) 0 5)
   note (gen/vector (annotation) 0 5)
   description (nilable (string))
   series (gen/vector (imaging-study-series) 0 5)])

(def-resource-gen library
  [id id-value
   meta (rare-nil (meta))
   text (often-nil (narrative))
   url (uri)
   identifier (gen/vector (identifier) 0 5)
   version (nilable (string))
   name (nilable (string))
   title (nilable (string))
   subtitle (nilable (string))
   status (rare-nil (code))
   experimental (nilable (boolean))
   type (codeable-concept)
   subject (nilable (gen/one-of [(codeable-concept) (reference :reference (gen/return nil))]))
   date (nilable (dateTime))
   publisher (nilable (string))
   contact (gen/vector (contact-detail) 0 5)
   description (nilable (markdown))
   useContext (gen/vector (usage-context) 0 5)
   jurisdiction (gen/vector (codeable-concept) 0 5)
   purpose (nilable (markdown))
   usage (nilable (string))
   copyright (nilable (markdown))
   approvalDate (nilable (blaze.fhir.spec.generators/date))
   lastReviewDate (nilable (blaze.fhir.spec.generators/date))
   effectivePeriod (nilable (period))
   topic (gen/vector (codeable-concept) 0 5)
   author (gen/vector (contact-detail) 0 5)
   editor (gen/vector (contact-detail) 0 5)
   reviewer (gen/vector (contact-detail) 0 5)
   endorser (gen/vector (contact-detail) 0 5)
   relatedArtifact (gen/vector (related-artifact) 0 5)
   parameter (gen/vector (parameter-definition) 0 5)
   dataRequirement (gen/vector (data-requirement) 0 5)
   content (gen/vector (attachment) 0 5)])

(def-resource-gen medication-administration
  [id id-value
   meta (rare-nil (meta))
   text (often-nil (narrative))
   medication (rare-nil (reference :reference (gen/return nil)))
   subject (rare-nil (reference :reference (gen/return nil)))])

(defn- observation-value []
  (gen/one-of [(quantity) (codeable-concept) (string) (boolean) (integer)
               (range) (ratio) (sampled-data) (time) (dateTime) (period)]))

(defn- observation-reference-range
  [& {:keys [id extension modifierExtension low high type appliesTo age text]
      :or {id (often-nil id-value)
           extension (extensions)
           modifierExtension (extensions)
           low (nilable (quantity))
           high (nilable (quantity))
           type (nilable (codeable-concept))
           appliesTo (gen/vector (codeable-concept) 0 5)
           age (nilable (range))
           text (nilable (string))}}]
  (->> (gen/tuple id extension modifierExtension low high type appliesTo age text)
       (to-map [:id :extension :modifierExtension :low :high :type :appliesTo :age :text])
       (fhir-type :fhir.Observation/referenceRange)))

(defn- observation-component
  [& {:keys [id extension modifierExtension code value dataAbsentReason interpretation referenceRange]
      :or {id (often-nil id-value)
           extension (extensions)
           modifierExtension (extensions)
           code (codeable-concept)
           value (nilable (observation-value))
           dataAbsentReason (nilable (codeable-concept))
           interpretation (gen/vector (codeable-concept) 0 5)
           referenceRange (gen/vector (observation-reference-range) 0 5)}}]
  (->> (gen/tuple id extension modifierExtension code value dataAbsentReason interpretation referenceRange)
       (to-map [:id :extension :modifierExtension :code :value :dataAbsentReason :interpretation :referenceRange])
       (fhir-type :fhir.Observation/component)))

(def-resource-gen observation
  [id id-value
   meta (rare-nil (meta))
   text (often-nil (narrative))
   identifier (gen/vector (identifier) 0 5)
   basedOn (gen/vector (reference :reference (gen/return nil)) 0 5)
   partOf (gen/vector (reference :reference (gen/return nil)) 0 5)
   status (rare-nil (code))
   category (gen/vector (codeable-concept) 0 5)
   code (rare-nil (codeable-concept))
   subject (rare-nil (reference :reference (gen/return nil)))
   focus (gen/vector (reference :reference (gen/return nil)) 0 5)
   encounter (nilable (reference :reference (gen/return nil)))
   effective (nilable (gen/one-of [(dateTime) (period) (timing) (instant)]))
   issued (nilable (instant))
   performer (gen/vector (reference :reference (gen/return nil)) 0 5)
   value (rare-nil (observation-value))
   dataAbsentReason (often-nil (codeable-concept))
   interpretation (gen/vector (codeable-concept) 0 5)
   note (gen/vector (annotation) 0 5)
   bodySite (nilable (codeable-concept))
   method (nilable (codeable-concept))
   specimen (nilable (reference :reference (gen/return nil)))
   device (nilable (reference :reference (gen/return nil)))
   referenceRange (gen/vector (observation-reference-range) 0 5)
   hasMember (gen/vector (reference :reference (gen/return nil)) 0 5)
   derivedFrom (gen/vector (reference :reference (gen/return nil)) 0 5)
   component (gen/vector (observation-component) 0 5)])

(def-resource-gen patient
  [id id-value
   meta (rare-nil (meta))
   text (often-nil (narrative))
   gender (rare-nil (code))
   birthDate (rare-nil (date))
   multipleBirth (rare-nil (gen/one-of [(boolean) (integer)]))])

(def-resource-gen procedure
  [id id-value
   meta (rare-nil (meta))
   text (often-nil (narrative))
   identifier (gen/vector (identifier) 0 5)
   instantiatesCanonical (gen/vector (canonical) 0 5)
   instantiatesUri (gen/vector (uri) 0 5)
   status (rare-nil (code))
   category (codeable-concept)
   code (rare-nil (codeable-concept))
   subject (rare-nil (reference :reference (gen/return nil)))
   encounter (rare-nil (reference :reference (gen/return nil)))])

(defn- parameter-value []
  (gen/one-of [(boolean) (integer) (string) (decimal) (uri) (url) (canonical)
               (base64Binary) (instant) (date) (dateTime) (time) (code) (oid)
               (id) (markdown) (unsignedInt) (positiveInt) (uuid)
               (coding) (codeable-concept) (quantity) (period) (ratio)
               (human-name) (address) (attachment) (reference) (meta)]))

(defn parameters-parameter
  [& {:keys [name value resource part]
      :or {name (string)
           value (rare-nil (parameter-value))
           resource (gen/return nil)
           part (gen/return nil)}}]
  (->> (gen/tuple name value resource part)
       (to-map [:name :value :resource :part])
       (fhir-type :fhir.Parameters/parameter)))

(def-resource-gen parameters
  [parameter (gen/vector (parameters-parameter))])

(defn- task-value []
  (gen/one-of [(quantity) (codeable-concept) (string) (boolean) (integer)
               (range) (ratio) (sampled-data) (time) (dateTime) (period)]))

(defn- task-input
  [& {:keys [type value]
      :or {type (codeable-concept)
           value (task-value)}}]
  (->> (gen/tuple type value)
       (to-map [:type :value])
       (fhir-type :fhir.Task/input)))

(def-resource-gen task
  [id id-value
   meta (rare-nil (meta))
   text (often-nil (narrative))
   identifier (gen/vector (identifier) 0 5)
   status (rare-nil (code))
   input (gen/vector (task-input) 0 5)])

(defn- value-set-compose
  [& {:keys [inactive]
      :or {inactive (often-nil (boolean))}}]
  (->> (gen/tuple inactive)
       (to-map [:inactive])
       (fhir-type :fhir.ValueSet/compose)))

(def-resource-gen value-set
  [id id-value
   meta (rare-nil (meta))
   text (often-nil (narrative))
   url (uri)
   identifier (gen/vector (identifier) 0 5)
   version (rare-nil (string))
   name (nilable (string))
   title (nilable (string))
   status (rare-nil (code))
   experimental (nilable (boolean))
   compose (value-set-compose)])
