(ns blaze.db.impl.search-param.token
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :as ac]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.compartment.search-param-value-resource :as c-sp-vr]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.core :as sc]
   [blaze.db.impl.search-param.util :as u]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.spec.references :as fsr]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system :as system]
   [blaze.terminology-service :as ts]
   [blaze.util :refer [str]]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [java-time.api :as time]
   [taoensso.timbre :as log])
  (:import
   [com.github.benmanes.caffeine.cache AsyncCacheLoader AsyncLoadingCache Caffeine]))

(set! *warn-on-reflection* true)

(defmulti index-entries
  "Returns index entries for `value` from a resource.

  Index entries are `[modifier value include-in-compartments?]` triples."
  {:arglists '([url value])}
  (fn [_ value] (or (:fhir/type value) (system/type value))))

(defmethod index-entries :fhir/id
  [_ id]
  (when-let [value (:value id)]
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :fhir/string
  [_ s]
  (when-let [value (:value s)]
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :fhir/uri
  [_ uri]
  (when-let [value (:value uri)]
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :fhir/url
  [_ url]
  (when-let [value (:value url)]
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :fhir/boolean
  [_ boolean]
  (when-some [value (:value boolean)]
    [[nil (codec/v-hash (str value))]]))

(defmethod index-entries :system/boolean
  [_ value]
  [[nil (codec/v-hash (str value))]])

(defmethod index-entries :fhir/canonical
  [_ canonical]
  (when-let [value (:value canonical)]
    (let [[url version-parts] (u/canonical-parts value)]
      (into
       [[nil (codec/v-hash value)]
        ["below" (codec/v-hash url)]]
       (map
        (fn [version-part]
          ["below" (codec/v-hash (str url "|" version-part))]))
       version-parts))))

(defmethod index-entries :fhir/code
  [_ code]
  ;; TODO: system
  (when-let [value (:value code)]
    [[nil (codec/v-hash value) true]]))

(defn token-coding-entries [{{code :value} :code {system :value} :system}]
  (cond-> []
    code
    (conj [nil (codec/v-hash code)])
    system
    (conj [nil (codec/v-hash (str system "|"))])
    (and code system)
    (conj [nil (codec/v-hash (str system "|" code)) true])
    (and code (nil? system))
    (conj [nil (codec/v-hash (str "|" code))])))

(defmethod index-entries :fhir/Coding
  [_ coding]
  (token-coding-entries coding))

(defmethod index-entries :fhir/CodeableConcept
  [_ {:keys [coding]}]
  (coll/eduction (mapcat token-coding-entries) coding))

(defn- identifier-entries
  [modifier {{:keys [value]} :value {system :value} :system}]
  (cond-> []
    value
    (conj [modifier (codec/v-hash value)])
    system
    (conj [modifier (codec/v-hash (str system "|"))])
    (and value system)
    (conj [modifier (codec/v-hash (str system "|" value))])
    (and value (nil? system))
    (conj [modifier (codec/v-hash (str "|" value))])))

(defmethod index-entries :fhir/Identifier
  [_ identifier]
  (identifier-entries nil identifier))

(defn- literal-reference-entries [reference]
  (when-let [value (:value reference)]
    (if-let [[type id] (fsr/split-literal-ref value)]
      [[nil (codec/v-hash id)]
       [nil (codec/v-hash (str type "/" id))]
       [nil (codec/tid-id (codec/tid type)
                          (codec/id-byte-string id))]]
      [[nil (codec/v-hash value)]])))

(defmethod index-entries :fhir/Reference
  [_ {:keys [reference identifier]}]
  (coll/eduction
   cat
   (cond-> []
     reference
     (conj (literal-reference-entries reference))
     identifier
     (conj (identifier-entries "identifier" identifier)))))

(defmethod index-entries :fhir/ContactPoint
  [_ {:keys [value]}]
  (when-let [value (:value value)]
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :default
  [url value]
  (log/warn (u/format-skip-indexing-msg value url "token")))

(defn- c-hash-w-modifier [c-hash code modifier]
  (if modifier
    (codec/c-hash (str code ":" modifier))
    c-hash))

(defn- canonical-url-modifier
  "Maps the `below` modifier to `nil` for `:canonical-url` search params
  (e.g. `ValueSet.url`), since those below-queries probe the plain unversioned
  index and never had dedicated below index entries. Leaves the modifier
  unchanged for any other `expression-type`, in particular `:canonical`
  (combined `url|version` string params like `depends-on`), whose below index
  entries do exist and still use the `below` c-hash."
  [expression-type modifier]
  (if (and (= :canonical-url expression-type) (= "below" modifier))
    nil
    modifier))

(defn index-handles
  "Returns a reducible collection of index handles that have `value` starting at
  `start-id` (optional)."
  ([{:keys [snapshot]} c-hash tid value]
   (sp-vr/index-handles-full-value snapshot c-hash tid value))
  ([{:keys [snapshot]} c-hash tid value start-id]
   (sp-vr/index-handles-full-value snapshot c-hash tid value start-id)))

(defn- has-system? [value]
  (let [idx (str/index-of value "|")]
    (and idx (< 0 idx (count value)))))

(def ^:private fhir-uri-modifier
  #{"above" "below" "missing"})

(def ^:private fhir-token-modifier
  #{"above" "below" "code-text" "in" "missing" "not" "not-in" "text" "text-advanced"})

(def ^:private fhir-reference-modifier
  #{"above" "below" "code-text" "contains" "identifier" "missing" "not-in" "text" "text-advanced"})

(defn- url-param [url]
  {:fhir/type :fhir.Parameters/parameter
   :name #fhir/string "url"
   :value (type/uri url)})

(defn- parameters [url]
  {:fhir/type :fhir/Parameters
   :parameter [(url-param url)]})

(defn- compile-concept [{:keys [system code]}]
  (codec/v-hash (str (:value system) "|" (:value code))))

(defn- compile-concepts [url value-set]
  (let [hashes (mapv compile-concept (:contains (:expansion value-set)))]
    (log/trace "Compiled" (count hashes) "hashes from the expansion of value set with URL:" url)
    hashes))

(defn- compile-value-set* [terminology-service url]
  (-> (ts/expand-value-set terminology-service (parameters url))
      (ac/then-apply (partial compile-concepts url))))

(defn- compile-value-set-cache [terminology-service]
  (-> (Caffeine/newBuilder)
      (.maximumSize 1000)
      (.refreshAfterWrite (time/hours 1))
      (^[AsyncCacheLoader] Caffeine/.buildAsync
       (fn [url _]
         (compile-value-set* terminology-service url)))))

(defn- expand-value-set-msg [url cause-msg]
  (format "Error while expanding the ValueSet `%s`. Cause: %s" url cause-msg))

(defn- version-prefix-match?
  "Returns true if the `wanted` version matches the `stored` version either
  exactly or as a dotted version prefix.

  Implements the FHIR R4 `:below` version-prefix rule (§3.1.1.4.13): a search
  version like `1` or `1.2` matches stored versions `1.0.0`, `1.2.3` etc., while
  `1.2.3` matches only `1.2.3`. The trailing `.` guard prevents `1` from
  matching `10.0`."
  [wanted stored]
  (or (= wanted stored) (str/starts-with? stored (str wanted "."))))

(def ^:private noop-resolver
  (reify fhir-path/Resolver (-resolve [_ _])))

(defn- parse-canonical-value
  "Splits a raw canonical search `value` into an `[url version]` pair, with
  `version` nil when `value` carries no `|version` suffix."
  [value]
  (if-let [idx (str/index-of value "|")]
    [(subs value 0 idx) (subs value (inc idx))]
    [value nil]))

(defn- version-matches?
  "Tests a `stored` version against a `wanted` version, using exact equality
  for the `nil` (default) modifier and dotted version-prefix matching (FHIR
  R4 §3.1.1.4.13) for the `below` modifier."
  [modifier wanted stored]
  (if (= "below" modifier)
    (version-prefix-match? wanted stored)
    (= wanted stored)))

(defn- matches-canonical-value?
  "Returns true if the resource behind `resource-handle` has a stored
  `.url`/`.version` pair (per `expression`/`version-expression`) matching one
  of the `wanted` `[url version]` pairs, honoring `modifier`.

  A `nil` version in a wanted pair matches any stored version. A non-nil
  wanted version only matches if the resource has a stored version, tested
  via `version-matches?`.

  Pulls the resource via `batch-db`. Used to post-filter the candidates of a
  versioned canonical url search, since versions are not held in the index."
  [batch-db expression version-expression modifier wanted resource-handle]
  (let [resource @(d/pull batch-db resource-handle)
        urls (keep :value (fhir-path/eval noop-resolver expression resource))
        stored-versions (delay (keep :value (fhir-path/eval noop-resolver version-expression resource)))]
    (some
     (fn [[wanted-url wanted-version]]
       (and (some #(= wanted-url %) urls)
            (or (nil? wanted-version)
                (some #(version-matches? modifier wanted-version %) @stored-versions))))
     wanted)))

(defrecord SearchParamToken [compile-value-set-cache name url type base code
                             target c-hash expression expression-type version-expression]
  p/SearchParam
  (-validate-modifier [_ modifier]
    (condp = type
      "uri"
      (when-not (and (#{:canonical :canonical-url} expression-type) (#{"below"} modifier))
        (some->> modifier (u/modifier-anom fhir-uri-modifier code)))
      "token"
      (when-not (#{"in"} modifier)
        (some->> modifier (u/modifier-anom fhir-token-modifier code)))
      ; else / "reference"
      (when-not ((into #{"identifier"} target) modifier)
        (some->> modifier (u/modifier-anom (into fhir-reference-modifier target) code)))))

  (-compile-value [_ modifier value]
    (cond
      (= "reference" type)
      (ac/completed-future
       (if-let [[type id] (fsr/split-literal-ref value)]
         (codec/tid-id (codec/tid type) (codec/id-byte-string id))
         (if (and (= 1 (count target)) (.matches (re-matcher #"[A-Za-z0-9\-\.]{1,64}" value)))
           (codec/tid-id (codec/tid (first target)) (codec/id-byte-string value))
           (codec/v-hash value))))
      (= "in" modifier)
      (-> (.get ^AsyncLoadingCache compile-value-set-cache value)
          (ac/exceptionally
           (fn [anom]
             (ba/fault (expand-value-set-msg value (::anom/message anom))))))
      (= :canonical-url expression-type)
      (ac/completed-future
       (codec/v-hash (if-let [idx (str/index-of value "|")] (subs value 0 idx) value)))
      :else
      (ac/completed-future (codec/v-hash value))))

  (-estimated-scan-size [_ batch-db tid modifier compiled-value]
    (let [c-hash (c-hash-w-modifier c-hash code (canonical-url-modifier expression-type modifier))]
      (sp-vr/estimated-scan-size (:kv-store batch-db) c-hash tid compiled-value)))

  (-supports-ordered-index-handles [_ _ _ _ _]
    true)

  (-ordered-index-handles
    [search-param batch-db tid modifier compiled-values]
    (if (= "in" modifier)
      (let [all-compiled-values (flatten compiled-values)]
        (if (empty? all-compiled-values)
          []
          (p/-ordered-index-handles search-param batch-db tid nil all-compiled-values)))
      (if (= 1 (count compiled-values))
        (p/-index-handles search-param batch-db tid modifier (first compiled-values))
        (let [index-handles #(p/-index-handles search-param batch-db tid modifier %)]
          (u/union-index-handles (map index-handles compiled-values))))))

  (-ordered-index-handles
    [search-param batch-db tid modifier compiled-values start-id]
    (if (= "in" modifier)
      (p/-ordered-index-handles search-param batch-db tid nil (flatten compiled-values) start-id)
      (if (= 1 (count compiled-values))
        (p/-index-handles search-param batch-db tid modifier (first compiled-values) start-id)
        (let [index-handles #(p/-index-handles search-param batch-db tid modifier % start-id)]
          (u/union-index-handles (map index-handles compiled-values))))))

  (-index-handles [_ batch-db tid modifier compiled-value]
    (index-handles batch-db (c-hash-w-modifier c-hash code (canonical-url-modifier expression-type modifier)) tid
                   compiled-value))

  (-index-handles [_ batch-db tid modifier compiled-value start-id]
    (index-handles batch-db (c-hash-w-modifier c-hash code (canonical-url-modifier expression-type modifier)) tid
                   compiled-value start-id))

  (-supports-ordered-compartment-index-handles [_ modifier values]
   ;; the CompartmentSearchParamValueResource index only contains values with
   ;; or values of type :fhir/code
   ;; with in-modifier, a ValueSet expansion contains always systems
    (or (= :code expression-type) (every? has-system? values) (= "in" modifier)))

  (-ordered-compartment-index-handles [search-param batch-db compartment tid modifier compiled-values]
    (if (= "in" modifier)
      (let [all-compiled-values (flatten compiled-values)]
        (if (empty? all-compiled-values)
          []
          (p/-ordered-compartment-index-handles search-param batch-db compartment tid nil all-compiled-values)))
      (if (= 1 (count compiled-values))
        (c-sp-vr/index-handles (:snapshot batch-db) compartment c-hash tid (first compiled-values))
        (let [index-handles #(c-sp-vr/index-handles (:snapshot batch-db) compartment c-hash tid %)]
          (u/union-index-handles (map index-handles compiled-values))))))

  (-ordered-compartment-index-handles [search-param batch-db compartment tid modifier compiled-values start-id]
    (if (= "in" modifier)
      (p/-ordered-compartment-index-handles search-param batch-db compartment tid nil (flatten compiled-values) start-id)
      (if (= 1 (count compiled-values))
        (c-sp-vr/index-handles (:snapshot batch-db) compartment c-hash tid (first compiled-values) start-id)
        (let [index-handles #(c-sp-vr/index-handles (:snapshot batch-db) compartment c-hash tid % start-id)]
          (u/union-index-handles (map index-handles compiled-values))))))

  (-matcher [_ batch-db modifier compiled-values]
    (r-sp-v/value-prefix-filter
     (:snapshot batch-db) (c-hash-w-modifier c-hash code (canonical-url-modifier expression-type modifier))
     compiled-values))

  (-single-version-id-matcher [_ batch-db tid modifier compiled-values]
    (r-sp-v/single-version-id-value-prefix-filter
     (:snapshot batch-db) tid (c-hash-w-modifier c-hash code (canonical-url-modifier expression-type modifier))
     compiled-values))

  (-postprocess-matches [_ batch-db modifier values _]
    (when (and (= :canonical-url expression-type) (some #(str/includes? % "|") values))
      (let [wanted (into #{} (map parse-canonical-value) values)]
        (filter (partial matches-canonical-value? batch-db expression
                         version-expression modifier wanted)))))

  (-compartment-ids [_ resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction
       (keep
        (fn [value]
          (when (identical? :fhir/Reference (:fhir/type value))
            (when-let [reference (:value (:reference value))]
              (when-let [[type id] (fsr/split-literal-ref reference)]
                (when (= "Patient" type)
                  id))))))
       values)))

  (-index-values [search-param resolver resource]
    (when-ok [url-values (fhir-path/eval resolver expression resource)]
      (coll/eduction (p/-index-value-compiler search-param) url-values)))

  (-index-value-compiler [_]
    (mapcat (partial index-entries url))))

(defn- identifier-values [{{:keys [value]} :value {system :value} :system}]
  (cond-> []
    value
    (conj value)
    system
    (conj (str system "|"))
    (and value system)
    (conj (str system "|" value))
    (and value (nil? system))
    (conj (str "|" value))))

(defn- matches-identifier-values? [db expression value-set resource-handle]
  (let [resource @(d/pull db resource-handle)
        values (fhir-path/eval noop-resolver expression resource)]
    (assert (not (ba/anomaly? values)))
    (some value-set (mapcat identifier-values values))))

(defrecord SearchParamTokenIdentifier [name url type base code target c-hash expression]
  p/SearchParam
  (-validate-modifier [_ modifier]
    (some->> modifier (u/modifier-anom #{"of-type"} code)))

  (-compile-value [_ _ value]
    (ac/completed-future (codec/v-hash value)))

  (-estimated-scan-size [_ _ _ _ _]
    1)

  (-supports-ordered-index-handles [_ _ _ _ _]
    true)

  (-ordered-index-handles
    [search-param batch-db tid modifier compiled-values]
    (if (= 1 (count compiled-values))
      (p/-index-handles search-param batch-db tid modifier (first compiled-values))
      (let [index-handles #(p/-index-handles search-param batch-db tid modifier %)]
        (u/union-index-handles (map index-handles compiled-values)))))

  (-ordered-index-handles
    [search-param batch-db tid modifier compiled-values start-id]
    (if (= 1 (count compiled-values))
      (p/-index-handles search-param batch-db tid modifier (first compiled-values) start-id)
      (let [index-handles #(p/-index-handles search-param batch-db tid modifier % start-id)]
        (u/union-index-handles (map index-handles compiled-values)))))

  (-index-handles [_ batch-db tid modifier compiled-value]
    (index-handles batch-db (c-hash-w-modifier c-hash code modifier) tid
                   compiled-value))

  (-index-handles [_ batch-db tid modifier compiled-value start-id]
    (index-handles batch-db (c-hash-w-modifier c-hash code modifier) tid
                   compiled-value start-id))

  (-supports-ordered-compartment-index-handles [_ _ _]
    false)

  (-ordered-compartment-index-handles [_ _ _ _ _ _]
    (ba/unsupported))

  (-ordered-compartment-index-handles [_ _ _ _ _ _ _]
    (ba/unsupported))

  (-matcher [_ batch-db modifier compiled-values]
    (r-sp-v/value-prefix-filter
     (:snapshot batch-db) (c-hash-w-modifier c-hash code modifier)
     compiled-values))

  (-single-version-id-matcher [_ batch-db tid modifier compiled-values]
    (r-sp-v/single-version-id-value-prefix-filter
     (:snapshot batch-db) tid (c-hash-w-modifier c-hash code modifier)
     compiled-values))

  (-postprocess-matches [_ batch-db _ values _]
    (filter (partial matches-identifier-values? batch-db expression (set values))))

  (-compartment-ids [_ _ _])

  (-index-values [search-param resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction (p/-index-value-compiler search-param) values)))

  (-index-value-compiler [_]
    (mapcat (partial index-entries url))))

(defrecord SearchParamId [name type code]
  p/SearchParam
  (-validate-modifier [_ modifier]
    (some->> modifier (u/unknown-modifier-anom code)))

  (-compile-value [_ _ value]
    (ac/completed-future (codec/id-byte-string value)))

  (-estimated-scan-size [_ _ _ _ _]
    1)

  (-supports-ordered-index-handles [_ _ _ _ _]
    true)

  (-ordered-index-handles
    [search-param batch-db tid modifier compiled-values]
    (if (= 1 (count compiled-values))
      (p/-index-handles search-param batch-db tid modifier (first compiled-values))
      (let [index-handles #(p/-index-handles search-param batch-db tid modifier %)]
        (coll/eduction (mapcat index-handles) (sort compiled-values)))))

  (-ordered-index-handles
    [search-param batch-db tid modifier compiled-values start-id]
    (let [compiled-values (drop-while #(not= start-id %) (sort compiled-values))
          index-handles #(p/-index-handles search-param batch-db tid modifier %)]
      (condp = (count compiled-values)
        0 []
        1 (index-handles (first compiled-values))
        (coll/eduction (mapcat index-handles) compiled-values))))

  (-index-handles [_ batch-db tid _ compiled-value]
    (or (some-> (u/non-deleted-resource-handle batch-db tid compiled-value)
                (svi/from-resource-handle)
                (ih/from-single-version-id)
                (vector))
        []))

  (-index-handles [sp batch-db tid modifier compiled-value start-id]
    (if (bs/<= start-id compiled-value)
      (p/-index-handles sp batch-db tid modifier compiled-value)
      []))

  (-sorted-index-handles [_ batch-db tid _]
    (coll/eduction
     (map ih/from-resource-handle)
     (rao/type-list batch-db tid)))

  (-sorted-index-handles [_ batch-db tid _ start-id]
    (coll/eduction
     (map ih/from-resource-handle)
     (rao/type-list batch-db tid start-id)))

  (-supports-ordered-compartment-index-handles [_ _ _]
    false)

  (-ordered-compartment-index-handles [_ _ _ _ _ _]
    (ba/unsupported))

  (-ordered-compartment-index-handles [_ _ _ _ _ _ _]
    (ba/unsupported))

  (-matcher [_ _ _ compiled-values]
    (let [ids (into #{} (map codec/id-string) compiled-values)
          pred (comp ids :id)]
      (filter pred)))

  (-postprocess-matches [_ _ _ _ _])

  (-index-values [_ _ _]))

(defn- fix-expr
  "https://github.com/samply/blaze/issues/366"
  [url expression]
  (case url
    "http://hl7.org/fhir/SearchParameter/Observation-component-value-concept"
    "Observation.component.value.ofType(CodeableConcept)"
    "http://hl7.org/fhir/SearchParameter/Observation-combo-value-concept"
    "(Observation.value as CodeableConcept) | Observation.component.value.ofType(CodeableConcept)"
    expression))

(defmethod sc/search-param "token"
  [{:keys [terminology-service expression-type]}
   {:keys [name url type base code target expression]}]
  (if (= "_id" code)
    (->SearchParamId "_id" "id" "_id")
    (if expression
      (let [expr-type (expression-type expression)]
        (when-ok [expression (fhir-path/compile (fix-expr url expression))]
          (if (= "identifier" code)
            (->SearchParamTokenIdentifier name url type base code target
                                          (codec/c-hash code) expression)
            (->SearchParamToken (compile-value-set-cache terminology-service)
                                name url type base code target
                                (codec/c-hash code) expression expr-type nil))))
      (ba/unsupported (u/missing-expression-msg url)))))

(defmethod sc/search-param "reference"
  [{:keys [terminology-service]} {:keys [name url type base code target expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamToken (compile-value-set-cache terminology-service)
                          name url type base code target
                          (codec/c-hash code) expression nil nil))
    (ba/unsupported (u/missing-expression-msg url))))

(defn- url->version-expression
  "Derives a FHIRPath version expression from a canonical-URL expression by
  replacing the trailing `.url` of each union branch with `.version`.

  Uses the FHIRPath parser to split the expression at the top-level `|` union
  operator, so a `|` inside a string literal is left alone. The replacement
  is anchored to the `.url` suffix of each branch so that a `.url` substring
  elsewhere in a path is never touched.

  Example: \"CapabilityStatement.url | ValueSet.url\"
        -> \"CapabilityStatement.version | ValueSet.version\""
  [expression]
  (when-ok [branches (fhir-path/union-paths expression)]
    (->> branches
         (map #(str/replace % #"\.url$" ".version"))
         (str/join " | "))))

(defmethod sc/search-param "uri"
  [{:keys [terminology-service expression-type]}
   {:keys [name url type base code target expression]}]
  (if expression
    (let [expr-type (expression-type expression)]
      (when-ok [compiled-expression (fhir-path/compile expression)
                version-expression (when (= :canonical-url expr-type)
                                     (url->version-expression expression))
                compiled-version-expression (when (some? version-expression)
                                              (fhir-path/compile version-expression))]
        (->SearchParamToken (compile-value-set-cache terminology-service)
                            name url type base code target
                            (codec/c-hash code) compiled-expression expr-type
                            compiled-version-expression)))
    (ba/unsupported (u/missing-expression-msg url))))
