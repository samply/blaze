(ns blaze.db.impl.search-param
  (:require
    [blaze.anomaly :refer [throw-anom when-ok]]
    [blaze.db.impl.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.kv :as kv]
    [blaze.fhir-path :as fhir-path]
    [clj-fuzzy.phonetics :as phonetics]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [taoensso.timbre :as log])
  (:import
    [java.io Closeable]
    [java.time ZoneId])
  (:refer-clojure :exclude [first next]))


(set! *warn-on-reflection* true)


(defprotocol Iterator
  (-first [_])
  (-next [_ current-id]))


(defn first [iterator]
  (-first iterator))


(defn next [iterator current-id]
  (-next iterator current-id))


(defprotocol SearchParam
  (-new-iterator [_ snapshot tid value])
  (-new-compartment-iterator [_ snapshot compartment tid value])
  (-matches? [_ snapshot tid id hash value])
  (-compartment-matches? [_ snapshot compartment tid id hash value])
  (-compartment-ids [_ resolver resource])
  (-index-entries [_ resolver hash resource linked-compartments]))


(defn new-iterator ^Closeable [search-param snapshot tid value]
  (let [[value & values] (str/split value #",")]
    (if (empty? values)
      (-new-iterator search-param snapshot tid value)
      (let [state (volatile! {:iter (-new-iterator search-param snapshot tid value)
                              :values values})]
        (reify
          Iterator
          (-first [_]
            (-first (:iter @state)))
          (-next [_ current-id]
            (if-let [k (-next (:iter @state) current-id)]
              k
              (let [[value & values] (:values @state)]
                (when value
                  (.close ^Closeable (:iter @state))
                  (vreset! state {:iter (-new-iterator search-param snapshot tid value)
                                  :values values})
                  (-first (:iter @state))))))
          Closeable
          (close [_]
            (.close ^Closeable (:iter @state))))))))


(defn new-compartment-iterator ^Closeable [search-param snapshot compartment tid value]
  (let [[value & values] (str/split value #",")]
    (if (empty? values)
      (-new-compartment-iterator search-param snapshot compartment tid value)
      (let [state (volatile! {:iter (-new-compartment-iterator search-param snapshot compartment tid value)
                              :values values})]
        (reify
          Iterator
          (-first [_]
            (-first (:iter @state)))
          (-next [_ current-id]
            (if-let [k (-next (:iter @state) current-id)]
              k
              (let [[value & values] (:values @state)]
                (when value
                  (.close ^Closeable (:iter @state))
                  (vreset! state {:iter (-new-compartment-iterator search-param snapshot compartment tid value)
                                  :values values})
                  (-first (:iter @state))))))
          Closeable
          (close [_]
            (.close ^Closeable (:iter @state))))))))


(defn matches? [search-param snapshot tid id hash value]
  (-matches? search-param snapshot tid id hash value))


(defn compartment-matches? [search-param snapshot compartment tid id hash value]
  (-compartment-matches? search-param snapshot compartment tid id hash value))


(s/def :blaze.db/local-ref
  (s/and string?
         (s/conformer #(str/split % #"/" 2))
         (s/tuple :blaze.resource/resourceType :blaze.resource/id)))


(def stub-resolver
  "A resolver which only returns a resource stub with type and id from the local
  reference itself."
  (reify
    fhir-path/Resolver
    (-resolve [_ uri]
      (let [res (s/conform :blaze.db/local-ref uri)]
        (when-not (s/invalid? res)
          (let [[type id] res]
            {:resourceType type
             :id id}))))))


(defn compartment-ids
  "Returns all compartments `resource` is part-of according to `search-param`."
  [search-param resource]
  (-compartment-ids search-param stub-resolver resource))


(defn index-entries
  "Returns search index entries of `resource` with `hash` or an anomaly in case
  of errors."
  [search-param hash resource linked-compartments]
  (-index-entries search-param stub-resolver hash resource linked-compartments))


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


(defn- key-valid? [^bytes start-key ^bytes key]
  (and (<= (alength start-key) (alength key))
       (bytes/prefix= start-key key (alength start-key))))


(defn- prefix-seek [iter key]
  (when-let [k (kv/seek iter key)]
    (when (key-valid? key k)
      k)))


(defn- seek-value [snapshot tid id hash c-hash value]
  (with-open [iter (kv/new-iterator snapshot :resource-value-index)]
    (prefix-seek iter (codec/resource-value-key tid id hash c-hash value))))


(defn- get-value' [snapshot tid search-param-value-key c-hash]
  (get-value
    snapshot tid (codec/search-param-value-key->id search-param-value-key)
    (codec/search-param-value-key->hash-prefix search-param-value-key) c-hash))


(defn- get-compartment-value
  [snapshot {co-c-hash :c-hash co-res-id :res-id} tid id hash sp-c-hash]
  (kv/snapshot-get
    snapshot :compartment-resource-value-index
    (codec/compartment-resource-value-key co-c-hash co-res-id tid id hash sp-c-hash)))



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


(defn- date-key-lb? [k]
  (codec/date-lb? k date-key-offset))


(defn- date-key-ub? [k]
  (codec/date-ub? k date-key-offset))


(defn- date-eq-key-valid? [c-hash snapshot tid ub k]
  (and (date-key-lb? k)
       (when-let [v (get-value' snapshot tid k c-hash)]
         (bytes/<= (codec/date-lb-ub->ub v) ub))))


(defrecord SearchParamDate [name url type code c-hash expression]
  SearchParam
  (-new-iterator [_ snapshot tid value]
    (let [[op value] (separate-op value)
          iter (kv/new-iterator snapshot :search-param-value-index)]
      (case op
        :eq
        (let [start-key (codec/search-param-value-key c-hash tid (date-lb value))
              ub (date-ub value)
              date-eq-key-valid? #(date-eq-key-valid? c-hash snapshot tid ub %)]
          (reify
            Iterator
            (-first [_]
              (when-let [k (kv/seek iter start-key)]
                (when (date-eq-key-valid? k)
                  k)))
            (-next [_ current-id]
              (loop [k (kv/next iter)]
                (when (some-> k date-eq-key-valid?)
                  (if (bytes/= current-id (codec/search-param-value-key->id k))
                    ;; recur, because we are still on the same resource
                    (recur (kv/next iter))
                    k))))
            Closeable
            (close [_]
              (.close iter))))

        :ge
        (let [start-key (codec/search-param-value-key c-hash tid (date-lb value))]
          (reify
            Iterator
            (-first [_]
              (when-let [k (kv/seek iter start-key)]
                (when (date-key-lb? k)
                  k)))
            (-next [_ current-id]
              (loop [k (kv/next iter)]
                (when (some-> k date-key-lb?)
                  (if (bytes/= current-id (codec/search-param-value-key->id k))
                    ;; recur, because we are still on the same resource
                    (recur (kv/next iter))
                    k))))
            Closeable
            (close [_]
              (.close iter))))

        :le
        (let [^bytes start-key (codec/search-param-value-key-for-prev c-hash tid (date-ub value))]
          (reify
            Iterator
            (-first [_]
              (when-let [^bytes k (kv/seek-for-prev iter start-key)]
                (when (date-key-ub? k)
                  k)))
            (-next [_ current-id]
              (loop [k (kv/prev iter)]
                (when (some-> k date-key-ub?)
                  (if (bytes/= current-id (codec/search-param-value-key->id k))
                    ;; recur, because we are still on the same resource
                    (recur (kv/prev iter))
                    k))))
            Closeable
            (close [_]
              (.close iter))))

        (throw-anom ::anom/unsupported (format "Unsupported prefix `%s` in search parameter of type date." (clojure.core/name op))))))

  (-matches? [_ snapshot tid id hash value]
    (when-let [v (get-value snapshot tid id hash c-hash)]
      (let [[op value] (separate-op value)]
        (case op
          :eq (and (bytes/<= (date-lb value) (codec/date-lb-ub->lb v))
                   (bytes/<= (codec/date-lb-ub->ub v) (date-ub value)))))))

  (-index-entries [_ resolver hash {type :resourceType id :id :as resource} linked-compartments]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (let [tid (codec/tid type)
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


(defrecord SearchParamString [name url type code c-hash expression]
  SearchParam
  (-new-iterator [_ snapshot tid value]
    (let [value (normalize-string value)
          iter (kv/new-iterator snapshot :search-param-value-index)
          start-key (codec/search-param-value-key c-hash tid (codec/string value))]
      (reify
        Iterator
        (-first [_]
          (prefix-seek iter start-key))
        (-next [_ current-id]
          (loop [k (kv/next iter)]
            (when (some->> k (key-valid? start-key))
              (if (bytes/= current-id (codec/search-param-value-key->id k))
                ;; recur, because we are still on the same resource
                (recur (kv/next iter))
                k))))
        Closeable
        (close [_]
          (.close iter)))))

  (-matches? [_ snapshot tid id hash value]
    (seek-value snapshot tid id hash c-hash (codec/string (normalize-string value))))

  (-index-entries [_ resolver hash {type :resourceType id :id :as resource} linked-compartments]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (let [tid (codec/tid type)
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

(defmulti token-index-entries (fn [_ _ value] (type value)))


(defmethod token-index-entries `string?
  [_ entries-fn s]
  (entries-fn [(codec/v-hash @s)]))


(defmethod token-index-entries :fhir/id
  [_ entries-fn id]
  (entries-fn [(codec/v-hash @id)]))


(defmethod token-index-entries :fhir/string
  [_ entries-fn s]
  (entries-fn [(codec/v-hash @s)]))


(defmethod token-index-entries :fhir/uri
  [_ entries-fn uri]
  (entries-fn [(codec/v-hash @uri)]))


(defmethod token-index-entries :fhir/boolean
  [_ entries-fn boolean]
  (entries-fn [(codec/v-hash (str @boolean))]))


(defmethod token-index-entries :fhir/canonical
  [_ entries-fn uri]
  (entries-fn [(codec/v-hash @uri)]))


(defmethod token-index-entries :fhir/code
  [_ entries-fn code]
  ;; TODO: system
  (entries-fn [(codec/v-hash @code)]))


(defmethod token-index-entries :fhir/Coding
  [_ entries-fn {:keys [code system]}]
  (entries-fn
    (cond-> []
      code
      (conj (codec/v-hash code))
      system
      (conj (codec/v-hash (str system "|")))
      (and code system)
      (conj (codec/v-hash (str system "|" code)))
      (and code (nil? system))
      (conj (codec/v-hash (str "|" code))))))


(defmethod token-index-entries :fhir/CodeableConcept
  [_ entries-fn {:keys [coding]}]
  (entries-fn
    (mapcat
      (fn [{:keys [code system]}]
        (cond-> []
          code
          (conj (codec/v-hash code))
          system
          (conj (codec/v-hash (str system "|")))
          (and code system)
          (conj (codec/v-hash (str system "|" code)))
          (and code (nil? system))
          (conj (codec/v-hash (str "|" code)))))
      coding)))


(defmethod token-index-entries :fhir/Identifier
  [_ entries-fn {:keys [value system]}]
  (entries-fn
    (cond-> []
      value
      (conj (codec/v-hash value))
      system
      (conj (codec/v-hash (str system "|")))
      (and value system)
      (conj (codec/v-hash (str system "|" value)))
      (and value (nil? system))
      (conj (codec/v-hash (str "|" value))))))


(defmethod token-index-entries :fhir/Reference
  [_ entries-fn {:keys [reference]}]
  (when reference
    (let [res (s/conform :blaze.db/local-ref reference)]
      (if (s/invalid? res)
        (entries-fn
          [(codec/v-hash reference)])
        (let [[type id] res]
          (entries-fn
            [(codec/v-hash id)
             (codec/v-hash (str type "/" id))]))))))


(defmethod token-index-entries :fhir/ContactPoint
  [_ entries-fn {:keys [value]}]
  (when value
    (entries-fn [(codec/v-hash value)])))


(defmethod token-index-entries :default
  [url _ value]
  (log/warn (format-skip-indexing-msg value url "token")))


(defrecord SearchParamToken [name url type code c-hash expression]
  SearchParam
  (-new-iterator [_ snapshot tid value]
    (let [value (codec/v-hash value)
          iter (kv/new-iterator snapshot :search-param-value-index)
          start-key (codec/search-param-value-key c-hash tid value)]
      (reify
        Iterator
        (-first [_]
          (prefix-seek iter start-key))
        (-next [_ current-id]
          (loop [k (kv/next iter)]
            (when (some->> k (key-valid? start-key))
              (if (bytes/= current-id (codec/search-param-value-key->id k))
                ;; recur, because we are still on the same resource
                (recur (kv/next iter))
                k))))
        Closeable
        (close [_]
          (.close iter)))))

  (-new-compartment-iterator [_ snapshot compartment tid value]
    (let [value (codec/v-hash value)
          iter (kv/new-iterator snapshot :compartment-search-param-value-index)
          {co-c-hash :c-hash co-res-id :res-id} compartment
          start-key (codec/compartment-search-param-value-key
                      co-c-hash co-res-id c-hash tid value)]
      (reify
        Iterator
        (-first [_]
          (prefix-seek iter start-key))
        (-next [_ current-id]
          (loop [k (kv/next iter)]
            (when (some->> k (key-valid? start-key))
              (if (= current-id (codec/compartment-search-param-value-key->id k))
                ;; recur, because we are still on the same resource
                (recur (kv/next iter))
                k))))
        Closeable
        (close [_]
          (.close iter)))))

  (-matches? [_ snapshot tid id hash value]
    (when-let [v (get-value snapshot tid id hash c-hash)]
      (codec/contains-v-hash? v (codec/v-hash value))))

  (-compartment-matches? [_ snapshot compartment tid id hash value]
    (when-let [v (get-compartment-value snapshot compartment tid (codec/id-bytes id) hash c-hash)]
      (codec/contains-v-hash? v (codec/v-hash value))))

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
                  (let [res (s/conform :blaze.db/local-ref reference)]
                    (when-not (s/invalid? res)
                      (rest res))))))))
        values)))

  (-index-entries [_ resolver hash {type :resourceType id :id :as resource} linked-compartments]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (let [tid (codec/tid type)
            id-bytes (codec/id-bytes id)]
        (into
          []
          (mapcat
            (partial
              token-index-entries
              url
              (fn search-param-token-entry [values]
                (log/trace "search-param-value-entry" "token" code type id (codec/hex hash))
                (into
                  (into
                    [[:resource-value-index
                      (codec/resource-value-key
                        tid
                        id-bytes
                        hash
                        c-hash)
                      (bytes/concat values)]]
                    (map
                      (fn [{co-c-hash :c-hash co-res-id :res-id}]
                        [:compartment-resource-value-index
                         (codec/compartment-resource-value-key
                           co-c-hash
                           co-res-id
                           tid
                           id-bytes
                           hash
                           c-hash)
                         (bytes/concat values)]))
                    linked-compartments)
                  (mapcat
                    (fn [value]
                      (cons
                        [:search-param-value-index
                         (codec/search-param-value-key
                           c-hash
                           tid
                           value
                           id-bytes
                           hash)
                         bytes/empty]
                        (map
                          (fn [{co-c-hash :c-hash co-res-id :res-id}]
                            [:compartment-search-param-value-index
                             (codec/compartment-search-param-value-key
                               co-c-hash
                               co-res-id
                               c-hash
                               tid
                               value
                               id-bytes
                               hash)
                             bytes/empty])
                          linked-compartments))))
                  values))))
          values)))))



;; ---- Quantity --------------------------------------------------------------

(defmulti quantity-index-entries (fn [_ _ value] (type value)))


(defmethod quantity-index-entries :fhir/Quantity
  [_ entry-fn {:keys [value system code]}]
  (into (entry-fn "" value) (entry-fn (str system "|" code) value)))


(defmethod quantity-index-entries :default
  [url _ value]
  (log/warn (format-skip-indexing-msg value url "quantity")))


(defrecord SearchParamQuantity [name url type code c-hash expression]
  SearchParam
  (-new-iterator [_ snapshot tid value]
    (let [[value unit] (str/split value #"\|" 2)
          iter (kv/new-iterator snapshot :search-param-value-index)
          start-key (codec/search-param-value-key c-hash tid (codec/quantity (BigDecimal. ^String value) unit))]
      (reify
        Iterator
        (-first [_]
          (prefix-seek iter start-key))
        (-next [_ current-id]
          (loop [k (kv/next iter)]
            (when (some->> k (key-valid? start-key))
              (if (bytes/= current-id (codec/search-param-value-key->id k))
                ;; recur, because we are still on the same resource
                (recur (kv/next iter))
                k))))
        Closeable
        (close [_]
          (.close iter)))))

  (-matches? [_ snapshot tid id hash value]
    (let [[value unit] (str/split value #"\|" 2)]
      (when-let [v (get-value snapshot tid id hash c-hash)]
        (bytes/= (codec/quantity (BigDecimal. ^String value) unit) v))))

  (-index-entries [_ resolver hash {type :resourceType id :id :as resource} linked-compartments]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (let [tid (codec/tid type)
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
                    c-hash)
                  (codec/quantity value unit)]])))
          values)))))


(defmulti search-param (fn [{:keys [type]}] type))


(defmethod search-param "date"
  [{:keys [name url type code expression]}]
  (when expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamDate name url type code (codec/c-hash code) expression))))


(defmethod search-param "string"
  [{:keys [name url type code expression]}]
  (when expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamString name url type code (codec/c-hash code) expression))))


(defmethod search-param "token"
  [{:keys [name url type code expression]}]
  (when expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamToken name url type code (codec/c-hash code) expression))))


(defmethod search-param "reference"
  [{:keys [name url type code expression]}]
  (when expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamToken name url type code (codec/c-hash code) expression))))


;; TODO: do we need to index composites?
(defmethod search-param "composite" [_])


(defmethod search-param "quantity"
  [{:keys [name url type code expression]}]
  (when-ok [expression (fhir-path/compile expression)]
    (->SearchParamQuantity name url type code (codec/c-hash code) expression)))


(defmethod search-param "uri"
  [{:keys [name url type code expression]}]
  (when expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamToken name url type code (codec/c-hash code) expression))))


(defmethod search-param :default
  [{:keys [url type]}]
  (log/debug (format "Skip creating search parameter `%s` of type `%s` because the rule is missing." url type)))
