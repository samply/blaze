(ns blaze.fhir.spec.generators
  (:refer-clojure :exclude [boolean meta str time])
  (:require
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system :as system]
   [blaze.util :refer [str]]
   [clojure.string :as str]
   [clojure.test.check.generators :as gen])
  (:import
   [com.google.common.base CaseFormat]
   [java.math RoundingMode]))

(set! *warn-on-reflection* true)

(defn nilable [gen]
  (gen/one-of [gen (gen/return nil)]))

(defn rare-nil [gen]
  (gen/frequency [[9 gen] [1 (gen/return nil)]]))

(defn often-nil [gen]
  (gen/frequency [[9 (gen/return nil)] [1 gen]]))

(def boolean-value
  gen/boolean)

(def integer-value
  gen/small-integer)

(def integer64-value
  gen/large-integer)

(def string-value
  (gen/such-that (partial re-matches #"[\r\n\t\u0020-\uFFFF]+") gen/string 1000))

(def decimal-value
  (gen/fmap
   #(if (< 17 (.scale ^BigDecimal %))
      (.setScale ^BigDecimal % 17 RoundingMode/HALF_UP)
      %)
   (gen/such-that
    #(<= (.precision ^BigDecimal %) 35)
    (gen/fmap #(BigDecimal/valueOf ^double %) (gen/double* {:infinite? false :NaN? false}))
    1000)))

(def uri-value
  (gen/such-that (partial re-matches #"[\u0021-\uFFFF]*") gen/string 1000))

(def url-value
  (gen/such-that (partial re-matches #"[\u0021-\uFFFF]*") gen/string 1000))

(def canonical-value
  (gen/such-that (partial re-matches #"[\u0021-\uFFFF]*") gen/string 1000))

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
  (gen/fmap (partial apply format "%04d-%02d-%02dT%02d:%02d:%02d%s")
            (gen/tuple year month day hour minute time-second zone)))

(def date-value
  (gen/one-of
   [(gen/fmap (partial format "%04d") year)
    (gen/fmap (partial apply format "%04d-%02d")
              (gen/tuple year month))
    (gen/fmap (partial apply format "%04d-%02d-%02d")
              (gen/tuple year month day))]))

(defn dateTime-value [& {:keys [year] :or {year year}}]
  (gen/one-of
   [(gen/fmap (partial format "%04d") year)
    (gen/fmap (partial apply format "%04d-%02d")
              (gen/tuple year month))
    (gen/fmap (partial apply format "%04d-%02d-%02d")
              (gen/tuple year month day))
    (gen/fmap (partial apply format "%04d-%02d-%02dT%02d:%02d:%02d%s")
              (gen/tuple year month day hour minute time-second zone))]))

(def time-value
  (gen/fmap (partial apply format "%02d:%02d:%02d")
            (gen/tuple hour minute time-second)))

(def code-value
  (gen/such-that (partial re-matches #"[\u0021-\uFFFF]+([ \t\n\r][\u0021-\uFFFF]+)*") gen/string 1000))

(def char-digit
  (gen/fmap char (gen/choose 48 57)))

(def oid-value
  (gen/such-that (partial re-matches #"urn:oid:[0-2](\.(0|[1-9][0-9]*))+")
                 (gen/fmap (partial str "urn:oid:0.")
                           (gen/fmap str/join (gen/vector char-digit)))))

(def id-value
  (gen/such-that (partial re-matches #"[A-Za-z0-9\-\.]{1,64}")
                 (gen/fmap str/join (gen/vector gen/char-alphanumeric 1 64))
                 1000))

(def markdown-value
  (gen/such-that (partial re-matches #"[\r\n\t\u0020-\uFFFF]+") gen/string 1000))

(def unsignedInt-value
  gen/nat)

(def positiveInt-value
  (gen/fmap inc gen/nat))

(def uuid-value
  (gen/fmap (partial str "urn:uuid:") gen/uuid))

(defn- keep-vals [m]
  (reduce-kv
   (fn [ret k v]
     (if (or (nil? v) (and (vector? v) (empty? v)))
       (dissoc ret k)
       ret))
   m
   m))

(defn- to-map [keys vals]
  (gen/such-that seq (gen/fmap #(keep-vals (zipmap keys %)) vals) 100))

(declare extension)

(defn extensions [& {:as gens}]
  (gen/scale #(/ % 10) (gen/vector (extension gens))))

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

(def integer64
  (primitive-gen type/integer64 integer64-value))

(def string
  (primitive-gen type/string string-value))

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

(defn attachment
  [& {:keys [id extension contentType language data url size hash title creation]
      :or {id (often-nil id-value)
           extension (gen/return nil)
           contentType (rare-nil (code))
           language (nilable (code))
           data (rare-nil (base64Binary))
           url (often-nil (url))
           size (often-nil (integer64))
           hash (often-nil (base64Binary))
           title (often-nil (string))
           creation (often-nil (dateTime))}}]
  (->> (gen/tuple id extension contentType language data url size hash title
                  creation)
       (to-map [:id :extension :contentType :language :data :url :size :hash
                :title :creation])
       (gen/fmap type/attachment)))

(defn extension
  [& {:keys [id extension value]
      :or {id (often-nil id-value)
           extension (gen/return nil)
           value (gen/return nil)}}]
  (->> (gen/tuple id extension uri-value value)
       (to-map [:id :extension :url :value])
       (gen/fmap type/extension)))

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

(defn codeable-concept
  [& {:keys [id extension coding text]
      :or {id (often-nil id-value)
           extension (extensions)
           coding (gen/vector (coding))
           text (often-nil (string))}}]
  (->> (gen/tuple id extension coding text)
       (to-map [:id :extension :coding :text])
       (gen/fmap type/codeable-concept)))

(declare reference)

(defn codeable-reference
  [& {:keys [id extension concept reference]
      :or {id (often-nil id-value)
           extension (extensions)
           concept (nilable (codeable-concept))
           reference (nilable (reference))}}]
  (->> (gen/tuple id extension concept reference)
       (to-map [:id :extension :concept :reference])
       (gen/fmap type/codeable-reference)))

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

;; TODO: Range

(defn ratio
  [& {:keys [id extension numerator denominator]
      :or {id (often-nil id-value)
           extension (extensions)
           numerator (nilable (quantity))
           denominator (nilable (quantity))}}]
  (->> (gen/tuple id extension numerator denominator)
       (to-map [:id :extension :numerator :denominator])
       (gen/fmap type/ratio)))

;; TODO: RatioRange

(defn period
  [& {:keys [id extension start end]
      :or {id (often-nil id-value)
           extension (extensions)
           start (nilable (dateTime))
           end (nilable (dateTime))}}]
  (as-> (gen/tuple id extension start end) x
    (to-map [:id :extension :start :end] x)
    (gen/such-that #(<= (system/date-time-lower-bound (type/value (:start %)))
                        (system/date-time-upper-bound (type/value (:end %))))
                   x
                   100)
    (gen/fmap type/period x)))

;; TODO: SampledData

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

(defn human-name
  [& {:keys [id extension use text family given prefix suffix period]
      :or {id (often-nil id-value)
           extension (extensions)
           use (rare-nil (code))
           text (often-nil (string))
           family (rare-nil (string))
           given (gen/vector (string))
           prefix (gen/scale #(/ % 5) (gen/vector (string)))
           suffix (gen/scale #(/ % 5) (gen/vector (string)))
           period (often-nil (period))}}]
  (->> (gen/tuple id extension use text family given prefix suffix period)
       (to-map [:id :extension :use :text :family :given :prefix :suffix :period])
       (gen/fmap type/human-name)))

(defn address
  [& {:keys [id extension use type text line city district state postalCode
             country period]
      :or {id (often-nil id-value)
           extension (extensions)
           use (rare-nil (code))
           type (rare-nil (code))
           text (often-nil (string))
           line (gen/vector (string))
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

;; TODO: ContactPoint

;; TODO: Timing

;; TODO: Signature

;; TODO: Annotation

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

(defn meta
  [& {:keys [id extension versionId lastUpdated source profile security tag]
      :or {id (often-nil id-value)
           extension (extensions)
           versionId (rare-nil (blaze.fhir.spec.generators/id))
           lastUpdated (rare-nil (instant))
           source (nilable (uri))
           profile (gen/vector (canonical))
           security (gen/vector (coding))
           tag (gen/vector (coding))}}]
  (->> (gen/tuple id extension versionId lastUpdated source profile security tag)
       (to-map [:id :extension :versionId :lastUpdated :source :profile
                :security :tag])
       (gen/fmap type/meta)))

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

(defn- kebab->pascal [s]
  (.to CaseFormat/LOWER_HYPHEN CaseFormat/UPPER_CAMEL s))

(defmacro def-resource-gen [type [& fields]]
  (let [fields (partition 2 fields)
        field-syms (map first fields)]
    `(defn ~type [& {:keys [~@field-syms]
                     :or ~(into {} (map vec) fields)}]
       (->> (gen/tuple ~@field-syms)
            (to-map [~@(map keyword field-syms)])
            (fhir-type ~(keyword "fhir" (kebab->pascal (str type))))))))

(def-resource-gen bundle
  [id id-value
   identifier (identifier)
   type (rare-nil (code))
   entry (gen/vector (bundle-entry))])

(def-resource-gen patient
  [id id-value
   gender (rare-nil (code))
   birthDate (rare-nil (date))
   multipleBirth (rare-nil (gen/one-of [(boolean) (integer)]))])

(defn- observation-value []
  (gen/one-of [(quantity) (codeable-concept) (string) (boolean) (integer)
               #_(range) #_(ratio) #_(sampled-data) (time) (dateTime) (period)]))

(def-resource-gen observation
  [id id-value
   meta (meta)
   identifier (gen/vector (identifier))
   status (rare-nil (code))
   category (gen/vector (codeable-concept))
   code (rare-nil (codeable-concept))
   subject (rare-nil (reference :reference (gen/return nil)))
   encounter (rare-nil (reference :reference (gen/return nil)))
   effective (rare-nil (gen/one-of [(dateTime) (period)]))
   performer (gen/vector (reference :reference (gen/return nil)))
   value (rare-nil (observation-value))])

(def-resource-gen encounter
  [id id-value
   meta (meta)
   identifier (gen/vector (identifier))
   status (rare-nil (code))
   priority (nilable (codeable-concept))
   type (gen/vector (codeable-concept))
   subject (rare-nil (reference :reference (gen/return nil)))
   actualPeriod (rare-nil (period))])

(def-resource-gen procedure
  [id id-value
   meta (meta)
   identifier (gen/vector (identifier))
   status (rare-nil (code))
   category (gen/vector (codeable-concept))
   code (rare-nil (codeable-concept))
   subject (rare-nil (reference :reference (gen/return nil)))
   encounter (rare-nil (reference :reference (gen/return nil)))])

(def-resource-gen allergy-intolerance
  [id id-value
   meta (meta)
   category (gen/vector (code))])

(def-resource-gen medication-administration
  [id id-value
   meta (meta)
   medication (rare-nil (codeable-reference :reference (reference :reference (gen/return nil))))
   subject (rare-nil (reference :reference (gen/return nil)))])

(def-resource-gen diagnostic-report
  [id id-value
   meta (meta)
   identifier (gen/vector (identifier))
   status (rare-nil (code))
   category (gen/vector (codeable-concept))
   code (rare-nil (codeable-concept))
   subject (rare-nil (reference :reference (gen/return nil)))
   encounter (rare-nil (reference :reference (gen/return nil)))
   effective (rare-nil (gen/one-of [(dateTime) (period)]))
   issued (nilable (instant))
   performer (gen/vector (reference :reference (gen/return nil)))])

(def-resource-gen library
  [id id-value
   meta (meta)
   url (uri)
   identifier (gen/vector (identifier))
   version (rare-nil (string))
   name (nilable (string))
   title (nilable (string))
   subtitle (nilable (string))
   status (rare-nil (code))
   experimental (nilable (boolean))
   type (codeable-concept)
   subject (rare-nil (gen/one-of [(codeable-concept) (reference :reference (gen/return nil))]))
   content (gen/vector (attachment))])

(defn- code-system-concept
  [& {:keys [code]
      :or {code (code)}}]
  (->> (gen/tuple code)
       (to-map [:code])
       (fhir-type :fhir.CodeSystem/concept)))

(def-resource-gen code-system
  [id id-value
   meta (meta)
   url (uri)
   identifier (gen/vector (identifier))
   version (rare-nil (string))
   name (nilable (string))
   title (nilable (string))
   status (rare-nil (code))
   experimental (nilable (boolean))
   concept (gen/vector (code-system-concept))])

(defn- value-set-compose
  [& {:keys [inactive]
      :or {inactive (often-nil (boolean))}}]
  (->> (gen/tuple inactive)
       (to-map [:inactive])
       (fhir-type :fhir.ValueSet/compose)))

(def-resource-gen value-set
  [id id-value
   meta (meta)
   url (uri)
   identifier (gen/vector (identifier))
   version (rare-nil (string))
   name (nilable (string))
   title (nilable (string))
   status (rare-nil (code))
   experimental (nilable (boolean))
   compose (value-set-compose)])

(defn- task-value []
  (gen/one-of [(quantity) (codeable-concept) (string) (boolean) (integer)
               #_(range) #_(ratio) #_(sampled-data) (time) (dateTime) (period)]))

(defn- task-input
  [& {:keys [type value]
      :or {type (codeable-concept)
           value (task-value)}}]
  (->> (gen/tuple type value)
       (to-map [:type :value])
       (fhir-type :fhir.Task/input)))

(def-resource-gen task
  [identifier (gen/vector (identifier))
   status (rare-nil (code))
   input (gen/vector (task-input))])

(defn consent-policy-basis
  [& {:keys [id extension reference uri]
      :or {id (often-nil id-value)
           extension (extensions)
           reference (nilable (reference))
           uri (nilable (uri))}}]
  (->> (gen/tuple id extension reference uri)
       (to-map [:id :extension :reference :uri])
       (fhir-type :fhir.Consent/policyBasis)))

(defn consent-verification
  [& {:keys [id extension verified verificationType verifiedWith verificationDate]
      :or {id (often-nil id-value)
           extension (extensions)
           verified (boolean)
           verificationType (nilable (codeable-concept))
           verifiedWith (nilable (reference))
           verificationDate (gen/vector (dateTime))}}]
  (->> (gen/tuple id extension verified verificationType verifiedWith verificationDate)
       (to-map [:id :extension :verified :verificationType :verifiedWith :verificationDate])
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
  [& {:keys [id extension period actor action securityLabel purpose documentType
             code dataPeriod data]
      :or {id (often-nil id-value)
           extension (extensions)
           period (nilable (period))
           actor (gen/vector (consent-provision-actor))
           action (gen/vector (codeable-concept))
           securityLabel (gen/vector (coding))
           purpose (gen/vector (coding))
           documentType (gen/vector (coding))
           code (gen/vector (codeable-concept))
           dataPeriod (nilable (blaze.fhir.spec.generators/period))
           data (gen/vector (consent-provision-data))}}]
  (->> (gen/tuple id extension period actor action securityLabel purpose
                  documentType code dataPeriod data)
       (to-map [:id :extension :period :actor :action :securityLabel
                :purpose :documentType :code :dataPeriod :data :provision])
       (fhir-type :fhir.Consent/provision)))

(def-resource-gen consent
  [identifier (gen/vector (identifier))
   status (code)
   policyBasis (nilable (consent-policy-basis))
   verification (gen/vector (consent-verification))
   provision (gen/vector (consent-provision {:provision (gen/vector (consent-provision))}))])
