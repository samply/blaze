(ns blaze.db.impl.search-param
  (:require
    [blaze.anomaly :refer [throw-anom when-ok]]
    [blaze.coll.core :as coll]
    [blaze.db.impl.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource :as resource]
    [blaze.db.impl.index.resource-as-of :as resource-as-of]
    [blaze.db.impl.iterators :as i]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.list]
    [blaze.db.kv :as kv]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir.spec]
    [clj-fuzzy.phonetics :as phonetics]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [taoensso.timbre :as log])
  (:import
    [java.time ZoneId]
    [java.nio ByteBuffer])
  (:refer-clojure :exclude [keys]))


(set! *warn-on-reflection* true)


(defn compile-values [search-param values]
  (p/-compile-values search-param values))


(defn resources
  "Returns a reducible collection of resources."
  [search-param node snapshot svri rsvi raoi tid modifier compiled-values t]
  (coll/eduction
    (mapcat #(p/-resources search-param node snapshot svri rsvi raoi tid
                           modifier % t))
    compiled-values))


(def ^:private by-id-grouper
  "Transducer which groups `[id hash-prefix]` tuples by `id` and concatenates
  all hash-prefixes within each group, outputting `[id hash-prefixes]` tuples."
  (comp
    (partition-by (fn [[_ id]] (ByteBuffer/wrap id)))
    (map
      (fn group-hash-prefixes [[[_ id hash-prefix] & more]]
        [id (cons hash-prefix (map #(nth % 2) more))]))))


(defn- non-deleted-resource [node raoi tid id t]
  (when-let [resource (resource-as-of/resource node raoi tid id t)]
    (when-not (resource/deleted? resource)
      resource)))


(defn- resource-mapper [node raoi tid t]
  (mapcat
    (fn [[id hash-prefixes]]
      (when-let [resource (non-deleted-resource node raoi tid id t)]
        [[resource hash-prefixes]]))))


(def ^:private matches-hash-prefixes-filter
  (mapcat
    (fn [[resource hash-prefixes]]
      (when (some #(bytes/starts-with? (resource/hash resource) %) hash-prefixes)
        [resource]))))


(defn compartment-keys
  "Returns a reducible collection of `[prefix id hash-prefix]` triples."
  [search-param csvri compartment tid compiled-values]
  (coll/eduction
    (mapcat #(p/-compartment-keys search-param csvri compartment tid %))
    compiled-values))


(defn compartment-resources
  [node search-param csvri raoi compartment tid compiled-values t]
  (coll/eduction
    (comp
      by-id-grouper
      (resource-mapper node raoi tid t)
      matches-hash-prefixes-filter)
    (compartment-keys search-param csvri compartment tid compiled-values)))


(defn matches? [search-param snapshot tid id hash modifier compiled-values]
  (p/-matches? search-param snapshot tid id hash modifier compiled-values))


(def stub-resolver
  "A resolver which only returns a resource stub with type and id from the local
  reference itself."
  (reify
    fhir-path/Resolver
    (-resolve [_ uri]
      (let [res (s/conform :blaze.fhir/local-ref uri)]
        (when-not (s/invalid? res)
          (let [[type id] res]
            {:resourceType type
             :id id}))))))


(defn compartment-ids
  "Returns all compartments `resource` is part-of according to `search-param`."
  [search-param resource]
  (p/-compartment-ids search-param stub-resolver resource))


(defn index-entries
  "Returns search index entries of `resource` with `hash` or an anomaly in case
  of errors."
  [search-param hash resource linked-compartments]
  (p/-index-entries search-param stub-resolver hash resource linked-compartments))


(defn- separate-op
  "Ordered search parameters of type number, date and quantity allow prefixes in
  search values. This function separates the possible prefix (operator) and
  returns a tuple of operator and value. The default operator :eq is returned if
  no prefix was given."
  [value]
  (if (re-matches #"^(eq|ne|gt|lt|ge|le|sa|eb|ap).*" value)
    [(keyword (subs value 0 2)) (subs value 2)]
    [:eq value]))


(defn- format-skip-indexing-msg [value url type]
  (format "Skip indexing value `%s` of type `%s` for search parameter `%s` with type `%s` because the rule is missing."
          (pr-str value) (clojure.core/type value) url type))


(defn- get-value [snapshot tid id hash c-hash]
  (kv/snapshot-get
    snapshot :resource-value-index
    (codec/resource-value-key tid id hash c-hash)))


(defn- prefix-seek [iter key]
  (kv/seek! iter key)
  (when (kv/valid? iter)
    (let [k (kv/key iter)]
      (when (bytes/starts-with? k key)
        k))))


(defn- seek-value [snapshot tid id hash c-hash value]
  (with-open [iter (kv/new-iterator snapshot :resource-value-index)]
    (prefix-seek iter (codec/resource-value-key tid id hash c-hash value))))


(defn- prefix-keys [iter start-key]
  (coll/eduction
    (take-while (fn [[prefix]] (bytes/starts-with? prefix start-key)))
    (i/keys iter codec/decode-search-param-value-key start-key)))



;; ---- Date ------------------------------------------------------------------

(def ^:private default-zone-id (ZoneId/systemDefault))


(defn- date-lb [date-time]
  (codec/date-lb default-zone-id date-time))


(defn- date-ub [date-time]
  (codec/date-ub default-zone-id date-time))


(defmulti date-index-entries (fn [_ _ value] (type value)))


(defmethod date-index-entries :fhir/date
  [_ entries-fn date]
  (entries-fn (date-lb @date) (date-ub @date)))


(defmethod date-index-entries :fhir/dateTime
  [_ entries-fn date-time]
  (entries-fn (date-lb @date-time) (date-ub @date-time)))


(defmethod date-index-entries :fhir/instant
  [_ entries-fn date-time]
  (entries-fn (date-lb @date-time) (date-ub @date-time)))


(defmethod date-index-entries :fhir/Period
  [_ entries-fn {:keys [start end]}]
  (entries-fn
    (if start (date-lb start) codec/date-min-bound)
    (if end (date-ub end) codec/date-max-bound)))


(defmethod date-index-entries :default
  [url _ value]
  (log/warn (format-skip-indexing-msg value url "date")))


(def ^:private ^:const ^long date-key-offset
  (+ codec/c-hash-size codec/tid-size))


(defn- date-key-lb? [[prefix]]
  (codec/date-lb? prefix date-key-offset))


(defn- date-key-ub? [[prefix]]
  (codec/date-ub? prefix date-key-offset))


(defn- date-eq-key-valid? [c-hash snapshot tid ub [prefix id hash-prefix]]
  (and (date-key-lb? [prefix])
       (when-let [v (get-value snapshot tid id hash-prefix c-hash)]
         (bytes/<= (codec/date-lb-ub->ub v) ub))))


(defrecord SearchParamDate [name url type base code c-hash expression]
  p/SearchParam
  (-compile-values [_ values]
    (vec values))

  (-resources [_ node snapshot svri _ raoi tid _ compiled-value t]
    (let [[op value] (separate-op compiled-value)]
      (case op
        :eq
        (let [start-key (codec/search-param-value-key c-hash tid (date-lb value))
              ub (date-ub value)
              date-eq-key-valid? #(date-eq-key-valid? c-hash snapshot tid ub %)]
          (coll/eduction
            (comp
              (take-while date-eq-key-valid?)
              by-id-grouper
              (resource-mapper node raoi tid t)
              matches-hash-prefixes-filter)
            (i/keys svri codec/decode-search-param-value-key start-key)))

        :ge
        (let [start-key (codec/search-param-value-key c-hash tid (date-lb value))]
          (coll/eduction
            (comp
              (take-while date-key-lb?)
              by-id-grouper
              (resource-mapper node raoi tid t)
              matches-hash-prefixes-filter)
            (i/keys svri codec/decode-search-param-value-key start-key)))

        :le
        (let [^bytes start-key (codec/search-param-value-key-for-prev c-hash tid (date-ub value))]
          (coll/eduction
            (comp
              (take-while date-key-ub?)
              by-id-grouper
              (resource-mapper node raoi tid t)
              matches-hash-prefixes-filter)
            (i/keys-prev svri codec/decode-search-param-value-key start-key)))

        (throw-anom ::anom/unsupported (format "Unsupported prefix `%s` in search parameter of type date." (clojure.core/name op))))))

  (-matches? [_ snapshot tid id hash _ [value & _]]
    ;; TODO: handle other values
    (when-let [v (get-value snapshot tid id hash c-hash)]
      (let [[op value] (separate-op value)]
        (case op
          :eq (and (bytes/<= (date-lb value) (codec/date-lb-ub->lb v))
                   (bytes/<= (codec/date-lb-ub->ub v) (date-ub value)))))))

  (-index-entries [_ resolver hash resource _]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (let [{type :resourceType id :id} resource
            tid (codec/tid type)
            id-bytes (codec/id-bytes id)]
        (into
          []
          (mapcat
            (partial
              date-index-entries
              url
              (fn search-param-date-entry [lb ub]
                (log/trace "search-param-value-entry" "date" code type id (codec/hex hash))
                [[:search-param-value-index
                  (codec/search-param-value-key
                    c-hash
                    tid
                    lb
                    id-bytes
                    hash)
                  bytes/empty]
                 [:search-param-value-index
                  (codec/search-param-value-key
                    c-hash
                    tid
                    ub
                    id-bytes
                    hash)
                  bytes/empty]
                 [:resource-value-index
                  (codec/resource-value-key
                    tid
                    id-bytes
                    hash
                    c-hash)
                  (codec/date-lb-ub lb ub)]])))
          values)))))



;; ---- String ----------------------------------------------------------------

(defmulti string-index-entries (fn [_ _ value] (type value)))


(defn- normalize-string [s]
  (-> s
      (str/trim)
      (str/replace #"[\p{Punct}]" " ")
      (str/replace #"\s+" " ")
      (str/lower-case)))


(defmethod string-index-entries :fhir/string
  [_ entries-fn s]
  (entries-fn (normalize-string @s)))


(defmethod string-index-entries :fhir/markdown
  [_ entries-fn s]
  (entries-fn (normalize-string @s)))


(defn- string-entries [entries-fn values]
  (into
    []
    (comp
      (remove nil?)
      (map normalize-string)
      (mapcat entries-fn))
    values))


(defmethod string-index-entries :fhir/Address
  [_ entries-fn {:keys [line city district state postalCode country]}]
  (string-entries entries-fn (conj line city district state postalCode country)))


(defmethod string-index-entries :fhir/HumanName
  [url entries-fn {:keys [family given]}]
  (if (str/ends-with? url "phonetic")
    (some-> family phonetics/soundex entries-fn)
    (string-entries entries-fn (conj given family))))


(defmethod string-index-entries :default
  [url _ value]
  (log/warn (format-skip-indexing-msg value url "string")))


(defrecord SearchParamString [name url type base code c-hash expression]
  p/SearchParam
  (-compile-values [_ values]
    (mapv (comp codec/string normalize-string) values))

  (-resources [_ node _ svri _ raoi tid _ compiled-value t]
    (coll/eduction
      (comp
        by-id-grouper
        (resource-mapper node raoi tid t)
        matches-hash-prefixes-filter)
      (prefix-keys svri (codec/search-param-value-key c-hash tid compiled-value))))

  (-compartment-keys [_ csvri compartment tid compiled-value]
    (let [{co-c-hash :c-hash co-res-id :res-id} compartment
          start-key (codec/compartment-search-param-value-key
                      co-c-hash co-res-id c-hash tid compiled-value)]
      (prefix-keys csvri start-key)))

  (-matches? [_ snapshot tid id hash _ [compiled-value & _]]
    ;; TODO: handle other values
    (seek-value snapshot tid id hash c-hash compiled-value))

  (-index-entries [_ resolver hash resource _]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (let [{type :resourceType id :id} resource
            tid (codec/tid type)
            id-bytes (codec/id-bytes id)]
        (into
          []
          (mapcat
            (partial
              string-index-entries
              url
              (fn search-param-string-entry [value]
                (log/trace "search-param-value-entry" "string" code value type id (codec/hex hash))
                (let [value-bytes (codec/string value)]
                  [[:search-param-value-index
                    (codec/search-param-value-key
                      c-hash
                      tid
                      value-bytes
                      id-bytes
                      hash)
                    bytes/empty]
                   [:resource-value-index
                    (codec/resource-value-key
                      tid
                      id-bytes
                      hash
                      c-hash
                      value-bytes)
                    bytes/empty]]))))
          values)))))



;; ---- Token -----------------------------------------------------------------

(defmulti token-index-entries
  "Returns index entries for `value` from a resource.

  The supplied function `entries-fn` takes a value and is used to create the
  actual index entries. Multiple such `entries-fn` results can be combined to
  one coll of entries."
  {:arglists '([url entries-fn value])}
  (fn [_ _ value] (type value)))


(defmethod token-index-entries String
  [_ entries-fn s]
  (entries-fn nil (codec/v-hash s)))


(defmethod token-index-entries :fhir/id
  [_ entries-fn id]
  (entries-fn nil (codec/v-hash @id)))


(defmethod token-index-entries :fhir/string
  [_ entries-fn s]
  (entries-fn nil (codec/v-hash @s)))


(defmethod token-index-entries :fhir/uri
  [_ entries-fn uri]
  (entries-fn nil (codec/v-hash @uri)))


(defmethod token-index-entries :fhir/boolean
  [_ entries-fn boolean]
  (entries-fn nil (codec/v-hash (str @boolean))))


(defmethod token-index-entries :fhir/canonical
  [_ entries-fn uri]
  (entries-fn nil (codec/v-hash @uri)))


(defmethod token-index-entries :fhir/code
  [_ entries-fn code]
  ;; TODO: system
  (entries-fn nil (codec/v-hash @code)))


(defmethod token-index-entries :fhir/Coding
  [_ entries-fn {:keys [code system]}]
  (cond-> []
    code
    (into (entries-fn nil (codec/v-hash code)))
    system
    (into (entries-fn nil (codec/v-hash (str system "|"))))
    (and code system)
    (into (entries-fn nil (codec/v-hash (str system "|" code))))
    (and code (nil? system))
    (into (entries-fn nil (codec/v-hash (str "|" code))))))


(defmethod token-index-entries :fhir/CodeableConcept
  [_ entries-fn {:keys [coding]}]
  (into
    []
    (mapcat
      (fn [{:keys [code system]}]
        (cond-> []
          code
          (into (entries-fn nil (codec/v-hash code)))
          system
          (into (entries-fn nil (codec/v-hash (str system "|"))))
          (and code system)
          (into (entries-fn nil (codec/v-hash (str system "|" code))))
          (and code (nil? system))
          (into (entries-fn nil (codec/v-hash (str "|" code)))))))
    coding))


(defn- token-identifier-entries [entries-fn modifier {:keys [value system]}]
  (cond-> []
    value
    (into (entries-fn modifier (codec/v-hash value)))
    system
    (into (entries-fn modifier (codec/v-hash (str system "|"))))
    (and value system)
    (into (entries-fn modifier (codec/v-hash (str system "|" value))))
    (and value (nil? system))
    (into (entries-fn modifier (codec/v-hash (str "|" value))))))


(defmethod token-index-entries :fhir/Identifier
  [_ entries-fn identifier]
  (token-identifier-entries entries-fn nil identifier))


(defn- token-literal-reference-entries [entries-fn reference]
  (let [res (s/conform :blaze.fhir/local-ref reference)]
    (if (s/invalid? res)
      (entries-fn nil (codec/v-hash reference))
      (let [[type id] res]
        (-> (entries-fn nil (codec/v-hash id))
            (into (entries-fn nil (codec/v-hash (str type "/" id))))
            (into (entries-fn nil (codec/tid-id type id))))))))


(defmethod token-index-entries :fhir/Reference
  [_ entries-fn {:keys [reference identifier]}]
  (cond-> []
    reference
    (into (token-literal-reference-entries entries-fn reference))
    identifier
    (into (token-identifier-entries entries-fn "identifier" identifier))))


(defmethod token-index-entries :fhir/ContactPoint
  [_ entries-fn {:keys [value]}]
  (when value
    (entries-fn nil (codec/v-hash value))))


(defmethod token-index-entries :default
  [url _ value]
  (log/warn (format-skip-indexing-msg value url "token")))


(defn c-hash-w-modifier [c-hash code modifier]
  (if modifier
    (codec/c-hash (str code ":" modifier))
    c-hash))


(defn- index-token-entries
  [url code c-hash hash resource linked-compartments values]
  (let [{type :resourceType id :id} resource
        tid (codec/tid type)
        id-bytes (codec/id-bytes id)]
    (into
      []
      (mapcat
        (partial
          token-index-entries
          url
          (fn search-param-token-entry [modifier value]
            (log/trace "search-param-value-entry" "token" code type id
                       (codec/hex hash) (codec/hex value))
            (let [c-hash (c-hash-w-modifier c-hash code modifier)]
              (into
                [[:search-param-value-index
                  (codec/search-param-value-key
                    c-hash
                    tid
                    value
                    id-bytes
                    hash)
                  bytes/empty]
                 [:resource-value-index
                  (codec/resource-value-key
                    tid
                    id-bytes
                    hash
                    c-hash
                    value)
                  bytes/empty]]
                (map
                  (fn [[code id]]
                    [:compartment-search-param-value-index
                     (codec/compartment-search-param-value-key
                       (codec/c-hash code)
                       (codec/id-bytes id)
                       c-hash
                       tid
                       value
                       id-bytes
                       hash)
                     bytes/empty]))
                linked-compartments)))))
      values)))


(defrecord SearchParamToken [name url type base code c-hash expression]
  p/SearchParam
  (-compile-values [_ values]
    (mapv codec/v-hash values))

  (-resources [_ node _ svri _ raoi tid modifier compiled-value t]
    (coll/eduction
      (comp
        by-id-grouper
        (resource-mapper node raoi tid t)
        matches-hash-prefixes-filter)
      (prefix-keys
        svri
        (codec/search-param-value-key
          (c-hash-w-modifier c-hash code modifier)
          tid
          compiled-value))))

  (-compartment-keys [_ csvri compartment tid compiled-value]
    (let [{co-c-hash :c-hash co-res-id :res-id} compartment
          start-key (codec/compartment-search-param-value-key
                      co-c-hash co-res-id c-hash tid compiled-value)]
      (prefix-keys csvri start-key)))

  (-matches? [_ snapshot tid id hash modifier [compiled-value & _]]
    ;; TODO: handle other values
    (seek-value snapshot tid id hash (c-hash-w-modifier c-hash code modifier)
                compiled-value))

  (-compartment-ids [_ resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (into
        []
        (mapcat
          (fn [value]
            (case (clojure.core/type value)
              :fhir/Reference
              (let [{:keys [reference]} value]
                (when reference
                  (let [res (s/conform :blaze.fhir/local-ref reference)]
                    (when-not (s/invalid? res)
                      (rest res))))))))
        values)))

  (-index-entries [_ resolver hash resource linked-compartments]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (index-token-entries url code c-hash hash resource linked-compartments
                           values))))



;; ---- Quantity --------------------------------------------------------------

(defmulti quantity-index-entries
  "Returns index entries for `value` from a resource.

  The supplied function `entries-fn` takes a unit and a value and is used to
  create the actual index entries. Multiple such `entries-fn` results can be
  combined to one coll of entries."
  {:arglists '([url entries-fn value])}
  (fn [_ _ value] (type value)))


(defmethod quantity-index-entries :fhir/Quantity
  [_ entries-fn {:keys [value system code unit]}]
  (cond-> (entries-fn "" value)
    code
    (into (entries-fn code value))
    (and unit (not= unit code))
    (into (entries-fn unit value))
    (and system code)
    (into (entries-fn (str system "|" code) value))))


(defmethod quantity-index-entries :default
  [url _ value]
  (log/warn (format-skip-indexing-msg value url "quantity")))


(defrecord SearchParamQuantity [name url type base code c-hash expression]
  p/SearchParam
  (-compile-values [_ values]
    (mapv
      (fn [value]
        (let [[value unit] (str/split value #"\|" 2)]
          (codec/quantity (BigDecimal. ^String value) unit)))
      values))

  (-resources [_ node _ svri _ raoi tid _ compiled-value t]
    (coll/eduction
      (comp
        by-id-grouper
        (resource-mapper node raoi tid t)
        matches-hash-prefixes-filter)
      (prefix-keys svri (codec/search-param-value-key c-hash tid compiled-value))))

  (-compartment-keys [_ csvri compartment tid compiled-value]
    (let [{co-c-hash :c-hash co-res-id :res-id} compartment
          start-key (codec/compartment-search-param-value-key
                      co-c-hash co-res-id c-hash tid compiled-value)]
      (prefix-keys csvri start-key)))

  (-matches? [_ snapshot tid id hash _ [compiled-value & _]]
    ;; TODO: handle other values
    (seek-value snapshot tid id hash c-hash compiled-value))

  (-index-entries [_ resolver hash resource _]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (let [{type :resourceType id :id} resource
            tid (codec/tid type)
            id-bytes (codec/id-bytes id)]
        (into
          []
          (mapcat
            (partial
              quantity-index-entries
              url
              (fn search-param-quantity-entry [unit value]
                (log/trace "search-param-value-entry" "quantity" code unit value type id (codec/hex hash))
                [[:search-param-value-index
                  (codec/search-param-value-key
                    c-hash
                    tid
                    (codec/quantity value unit)
                    id-bytes
                    hash)
                  bytes/empty]
                 [:resource-value-index
                  (codec/resource-value-key
                    tid
                    id-bytes
                    hash
                    c-hash
                    (codec/quantity value unit))
                  bytes/empty]])))
          values)))))


(defmethod sr/search-param "date"
  [{:keys [name url type base code expression]}]
  (when expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamDate name url type base code (codec/c-hash code) expression))))


(defmethod sr/search-param "string"
  [{:keys [name url type base code expression]}]
  (when expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamString name url type base code (codec/c-hash code) expression))))


(defmethod sr/search-param "token"
  [{:keys [name url type base code expression]}]
  (when expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamToken name url type base code (codec/c-hash code) expression))))


(defmethod sr/search-param "reference"
  [{:keys [name url type base code expression]}]
  (when expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamToken name url type base code (codec/c-hash code) expression))))


;; TODO: do we need to index composites?
(defmethod sr/search-param "composite" [_])


(defmethod sr/search-param "quantity"
  [{:keys [name url type base code expression]}]
  (when-ok [expression (fhir-path/compile expression)]
    (->SearchParamQuantity name url type base code (codec/c-hash code) expression)))


(defmethod sr/search-param "uri"
  [{:keys [name url type base code expression]}]
  (when expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamToken name url type base code (codec/c-hash code) expression))))
