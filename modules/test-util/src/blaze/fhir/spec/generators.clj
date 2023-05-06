(ns blaze.fhir.spec.generators
  (:refer-clojure :exclude [boolean meta time])
  (:require
    [blaze.fhir.spec.type :as type]
    [blaze.fhir.spec.type.system :as system]
    [clojure.string :as str]
    [clojure.test.check.generators :as gen]
    [cuerdas.core :refer [pascal]]))


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


(def long-value
  gen/large-integer)


(def string-value
  (gen/such-that (partial re-matches #"[ \r\n\t\S]+") gen/string 100))


(def decimal-value
  (gen/fmap #(BigDecimal/valueOf ^double %) (gen/double* {:infinite? false :NaN? false})))


(def uri-value
  (gen/such-that (partial re-matches #"\S*") gen/string 100))


(def url-value
  (gen/such-that (partial re-matches #"\S*") gen/string 100))


(def canonical-value
  (gen/such-that (partial re-matches #"\S*") gen/string 100))


(def base64Binary-value
  (->> (gen/vector gen/char-alphanumeric 4)
       (gen/fmap str/join)
       (gen/vector)
       (gen/fmap str/join)
       (gen/such-that (partial re-matches #"([0-9a-zA-Z\\+/=]{4})+"))))


(def year
  (gen/choose 1900 2100))


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


(def dateTime-value
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
  (gen/such-that (partial re-matches #"[^\s]+(\s[^\s]+)*") gen/string 100))


(def char-digit
  (gen/fmap char (gen/choose 48 57)))


(def oid-value
  (gen/such-that (partial re-matches #"urn:oid:[0-2](\.(0|[1-9][0-9]*))+")
                 (gen/fmap (partial str "urn:oid:0.")
                           (gen/fmap str/join (gen/vector char-digit)))))


(def id-value
  (gen/such-that (partial re-matches #"[A-Za-z0-9\-\.]{1,64}")
                 (gen/fmap str/join (gen/vector gen/char-alphanumeric 1 64))))


(def markdown-value
  (gen/such-that (partial re-matches #"[ \r\n\t\S]+") gen/string 100))


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
        :or {id (gen/return nil)
             extension (extensions)
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
  (primitive-gen type/dateTime dateTime-value))


(def time
  (primitive-gen type/time time-value))


(def code
  (primitive-gen type/code code-value))


(def id
  (primitive-gen type/id id-value))


(def unsignedInt
  (primitive-gen type/unsignedInt unsignedInt-value))


(defn attachment
  [& {:keys [id extension contentType language data url size hash title creation]
      :or {id (gen/return nil)
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


(defn extension
  [& {:keys [id extension value]
      :or {id (gen/return nil)
           extension (gen/return nil)
           value (gen/return nil)}}]
  (->> (gen/tuple id extension uri-value value)
       (to-map [:id :extension :url :value])
       (gen/fmap type/extension)))


(defn coding
  [& {:keys [id extension system version code display user-selected]
      :or {id (gen/return nil)
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
      :or {id (gen/return nil)
           extension (extensions)
           coding (gen/vector (coding))
           text (often-nil (string))}}]
  (->> (gen/tuple id extension coding text)
       (to-map [:id :extension :coding :text])
       (gen/fmap type/codeable-concept)))


(defn quantity
  [& {:keys [id extension value comparator unit system code]
      :or {id (gen/return nil)
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


;; TODO: Ratio


;; TODO: RatioRange


(defn period
  [& {:keys [id extension start end]
      :or {id (gen/return nil)
           extension (extensions)
           start (nilable (dateTime))
           end (nilable (dateTime))}}]
  (->> (gen/tuple id extension start end)
       (to-map [:id :extension :start :end])
       (gen/such-that #(<= (system/date-time-lower-bound (type/value (:start %)))
                           (system/date-time-upper-bound (type/value (:end %)))))
       (gen/fmap type/period)))


;; TODO: SampledData


(declare reference)


(defn identifier
  [& {:keys [id extension use type system value period assigner]
      :or {id (gen/return nil)
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
      :or {id (gen/return nil)
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
      :or {id (gen/return nil)
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
      :or {id (gen/return nil)
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
      :or {id (gen/return nil)
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
      :or {id (gen/return nil)
           extension (extensions)
           mode (rare-nil (code))
           score (often-nil (decimal))}}]
  (->> (gen/tuple id extension mode score)
       (to-map [:id :extension :mode :score])
       (gen/fmap type/bundle-entry-search)))


(defn- fhir-type [fhir-type gen]
  (gen/fmap #(assoc % :fhir/type fhir-type) gen))


(defn bundle-entry
  [& {:keys [id extension resource]
      :or {id (gen/return nil)
           extension (extensions)}}]
  (->> (gen/tuple id extension resource)
       (to-map [:id :extension :resource])
       (fhir-type :fhir.Bundle/entry)))


(defmacro def-resource-gen [type [& fields]]
  (let [fields (partition 2 fields)
        field-syms (map first fields)]
    `(defn ~type [& {:keys [~@field-syms]
                     :or ~(into {} (map vec) fields)}]
       (->> (gen/tuple ~@field-syms)
            (to-map [~@(map keyword field-syms)])
            (fhir-type ~(keyword "fhir" (pascal type)))))))


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
   value (rare-nil (observation-value))])


(def-resource-gen procedure
  [id id-value
   meta (meta)
   identifier (gen/vector (identifier))
   instantiatesCanonical (gen/vector (canonical))
   instantiatesUri (gen/vector (uri))
   status (rare-nil (code))
   category (codeable-concept)
   code (rare-nil (codeable-concept))
   subject (rare-nil (reference :reference (gen/return nil)))
   encounter (rare-nil (reference :reference (gen/return nil)))])


(def-resource-gen allergy-intolerance
  [id id-value
   meta (meta)
   category (gen/vector (code))])


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
