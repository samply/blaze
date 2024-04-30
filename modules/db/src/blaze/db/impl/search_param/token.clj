(ns blaze.db.impl.search-param.token
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.compartment.search-param-value-resource :as c-sp-vr]
   [blaze.db.impl.index.patient-type-search-param-token-full-resource :as pt-sp-tfr]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.resource-search-param-token-full :as r-sp-tf]
   [blaze.db.impl.index.resource-search-param-token-system :as r-sp-ts]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.index.type-search-param-token-full-resource :as t-sp-tfr]
   [blaze.db.impl.index.type-search-param-token-system-resource :as t-sp-tsr]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.core :as sc]
   [blaze.db.impl.search-param.search-param-code-registry :as search-param-code-registry]
   [blaze.db.impl.search-param.system-registry :as system-registry]
   [blaze.db.impl.search-param.token.impl :as impl]
   [blaze.db.impl.search-param.util :as u]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.references :as fsr]
   [blaze.fhir.spec.type :as type]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defmulti index-entries
  "Returns index entries for `value` from a resource.

  Index entries are `[modifier value include-in-compartments?]` triples."
  {:arglists '([url value])}
  (fn [_ value] (fhir-spec/fhir-type value)))

(defmethod index-entries :fhir/id
  [_ id]
  (when-let [value (type/value id)]
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :fhir/string
  [_ s]
  (when-let [value (type/value s)]
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :fhir/uri
  [_ uri]
  (when-let [value (type/value uri)]
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :fhir/url
  [_ url]
  (when-let [value (type/value url)]
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :fhir/boolean
  [_ boolean]
  (when-some [value (type/value boolean)]
    [[nil (codec/v-hash (str value))]]))

(defmethod index-entries :fhir/code
  [_ code]
  ;; TODO: system
  (when-let [value (type/value code)]
    [[nil (codec/v-hash value) true]]))

(defn token-coding-index-entries [{:keys [code system]}]
  (let [code (type/value code)
        system (type/value system)]
    (cond-> []
      code
      (conj [nil (codec/v-hash code)])
      system
      (conj [nil (codec/v-hash (str system "|"))])
      (and code system)
      (conj [nil (codec/v-hash (str system "|" code)) true])
      (and code (nil? system))
      (conj [nil (codec/v-hash (str "|" code))]))))

(defmethod index-entries :fhir/Coding
  [_ coding]
  (token-coding-index-entries coding))

(defmethod index-entries :fhir/CodeableConcept
  [_ {:keys [coding]}]
  (coll/eduction (mapcat token-coding-index-entries) coding))

(defmethod index-entries :fhir/Identifier
  [_ identifier]
  (u/identifier-index-entries nil identifier))

(defmethod index-entries :fhir/ContactPoint
  [_ {:keys [value]}]
  (when-let [value (type/value value)]
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :default
  [url value]
  (log/warn (u/format-skip-indexing-msg value url "token")))

(defmulti new-index-entries*
  {:arglists '([type-byte-index kv-store tb code-id id hash value])}
  (fn [_ _ _ _ _ _ _ value] (fhir-spec/fhir-type value)))

(defmethod new-index-entries* :fhir/id
  [_ _ tb code-id _ id hash value]
  (when-let [value (some-> value type/value bs/from-utf8-string)]
    [(t-sp-tfr/index-entry tb code-id value codec/null-system-id id hash)
     (r-sp-tf/index-entry tb id hash code-id value codec/null-system-id)]))

(defmethod new-index-entries* :fhir/string
  [_ _ tb code-id _ id hash value]
  (when-let [value (some-> value type/value bs/from-utf8-string)]
    [(t-sp-tfr/index-entry tb code-id value codec/null-system-id id hash)
     (r-sp-tf/index-entry tb id hash code-id value codec/null-system-id)]))

(defmethod new-index-entries* :fhir/uri
  [_ _ tb code-id _ id hash value]
  (when-let [value (some-> value type/value bs/from-utf8-string)]
    [(t-sp-tfr/index-entry tb code-id value codec/null-system-id id hash)
     (r-sp-tf/index-entry tb id hash code-id value codec/null-system-id)]))

(defmethod new-index-entries* :fhir/url
  [_ _ tb code-id _ id hash value]
  (when-let [value (some-> value type/value bs/from-utf8-string)]
    [(t-sp-tfr/index-entry tb code-id value codec/null-system-id id hash)
     (r-sp-tf/index-entry tb id hash code-id value codec/null-system-id)]))

(defmethod new-index-entries* :fhir/boolean
  [_ _ tb code-id _ id hash value]
  (when-let [value (some-> value type/value str bs/from-utf8-string)]
    [(t-sp-tfr/index-entry tb code-id value codec/null-system-id id hash)
     (r-sp-tf/index-entry tb id hash code-id value codec/null-system-id)]))

(defmethod new-index-entries* :fhir/code
  [_ _ tb code-id patient-ids id hash value]
  (when-let [value (some-> value type/value bs/from-utf8-string)]
    (into
     [(t-sp-tfr/index-entry tb code-id value codec/null-system-id id hash)
      (r-sp-tf/index-entry tb id hash code-id value codec/null-system-id)]
     (map #(pt-sp-tfr/index-entry % tb code-id value codec/null-system-id id hash))
     patient-ids)))

(defmethod new-index-entries* :fhir/Coding
  [_ kv-store tb code-id patient-ids id hash {:keys [system code]}]
  (when-ok [system-id (system-registry/id-of kv-store (type/value system))]
    (let [code (some-> code type/value bs/from-utf8-string)]
      (cond-> []
        code
        (conj (t-sp-tfr/index-entry tb code-id code system-id id hash)
              (r-sp-tf/index-entry tb id hash code-id code system-id))
        (not= codec/null-system-id system-id)
        (conj (t-sp-tsr/index-entry tb code-id system-id id hash)
              (r-sp-ts/index-entry tb id hash code-id system-id))
        (and (not= codec/null-system-id system-id) code)
        (into (map #(pt-sp-tfr/index-entry % tb code-id code system-id id hash))
              patient-ids)))))

(defmethod new-index-entries* :fhir/CodeableConcept
  [type-byte-index kv-store tb code-id patient-ids id hash {:keys [coding]}]
  (coll/eduction
   (mapcat
    (partial new-index-entries* type-byte-index kv-store tb code-id patient-ids
             id hash))
   coding))

(defmethod new-index-entries* :fhir/Identifier
  [_ kv-store tb code-id _ id hash {:keys [system value]}]
  (when-ok [system-id (system-registry/id-of kv-store (type/value system))]
    (let [value (some-> value type/value bs/from-utf8-string)]
      (cond-> []
        value
        (conj (t-sp-tfr/index-entry tb code-id value system-id id hash)
              (r-sp-tf/index-entry tb id hash code-id value system-id))
        (not= codec/null-system-id system-id)
        (conj (t-sp-tsr/index-entry tb code-id system-id id hash)
              (r-sp-ts/index-entry tb id hash code-id system-id))))))

(defmethod new-index-entries* :fhir/ContactPoint
  [_ _ tb code-id _ id hash {:keys [value]}]
  (when-let [value (some-> value type/value bs/from-utf8-string)]
    [(t-sp-tfr/index-entry tb code-id value codec/null-system-id id hash)
     (r-sp-tf/index-entry tb id hash code-id value codec/null-system-id)]))

(defmethod new-index-entries* :default
  [_ _ _ _ _ _ _ _])

(defn- patient-ids [linked-compartments]
  (into
   []
   (keep
    (fn [[code id]] (when (= "Patient" code) (codec/id-byte-string id))))
   linked-compartments))

(defn- new-index-entries
  [type-byte-index kv-store resolver code-id expression linked-compartments
   hash {:keys [id] :as resource}]
  (let [id (codec/id-byte-string id)
        type (name (fhir-spec/fhir-type resource))
        tb (type-byte-index type)]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (into
       []
       (comp
        (mapcat (partial new-index-entries* type-byte-index kv-store tb code-id
                         (patient-ids linked-compartments) id hash))
        (halt-when ba/anomaly?))
       values))))

(def use-new-indices
  true)

(defn resource-keys
  "Returns a reducible collection of `[id hash-prefix]` tuples that have `value`
  starting at `start-id` (optional)."
  ([{:keys [snapshot]} c-hash tid value]
   (sp-vr/prefix-keys snapshot c-hash tid (bs/size value) value))
  ([{:keys [snapshot]} c-hash tid value start-id]
   (sp-vr/prefix-keys snapshot c-hash tid (bs/size value) value start-id)))

(defn resource-keys-new
  "Returns a reducible collection of `[id hash-prefix]` tuples that have `value`
  starting at `start-id` (optional)."
  ([{:keys [snapshot]} type-byte-index tid search-param-code-id
    {:keys [value system-id]}]
   (let [tb (type-byte-index (codec/tid->type tid))]
     (if value
       (if system-id
         (t-sp-tfr/prefix-keys snapshot tb search-param-code-id value system-id)
         (t-sp-tfr/prefix-keys snapshot tb search-param-code-id value))
       (t-sp-tsr/prefix-keys snapshot tb search-param-code-id system-id))))
  ([{:keys [snapshot t]} type-byte-index tid search-param-code-id
    {:keys [value system-id]} start-id]
   (let [tb (type-byte-index (codec/tid->type tid))]
     (if value
       (if system-id
         (t-sp-tfr/prefix-keys snapshot tb search-param-code-id value system-id
                               start-id)
         (when-let [resource-handle (rao/resource-handle snapshot tid start-id t)]
           (when-let [system-id (r-sp-tf/system-id snapshot type-byte-index
                                                   resource-handle search-param-code-id
                                                   value)]
             (t-sp-tfr/prefix-keys snapshot tb search-param-code-id value
                                   system-id start-id))))
       (t-sp-tsr/prefix-keys snapshot tb search-param-code-id system-id start-id)))))

(defn value-filter [snapshot type-byte-index code-id values]
  (let [system-only (filterv (comp nil? :value) values)
        full (filterv (comp some? :value) values)]
    (cond
      (and (seq system-only) (seq full))
      (comp (r-sp-ts/value-filter snapshot type-byte-index code-id system-only)
            (r-sp-tf/value-filter snapshot type-byte-index code-id full))

      (seq system-only)
      (r-sp-ts/value-filter snapshot type-byte-index code-id system-only)

      (seq full)
      (r-sp-tf/value-filter snapshot type-byte-index code-id full))))

(defrecord SearchParamToken [type-byte-index kv-store name url type base
                             code target c-hash code-id expression]
  p/SearchParam
  (-compile-value [_ _ value]
    (if use-new-indices
      (impl/compile-value-new kv-store value)
      (if (= "reference" type)
        (if-let [[type id] (fsr/split-literal-ref value)]
          (codec/tid-id (codec/tid type) (codec/id-byte-string id))
          (if (and (= 1 (count target)) (.matches (re-matcher #"[A-Za-z0-9\-\.]{1,64}" value)))
            (codec/tid-id (codec/tid (first target)) (codec/id-byte-string value))
            (codec/v-hash value)))
        (codec/v-hash value))))

  (-compile-value-composite [_ _ value]
    (if (= "reference" type)
      (if-let [[type id] (fsr/split-literal-ref value)]
        (codec/tid-id (codec/tid type) (codec/id-byte-string id))
        (if (and (= 1 (count target)) (.matches (re-matcher #"[A-Za-z0-9\-\.]{1,64}" value)))
          (codec/tid-id (codec/tid (first target)) (codec/id-byte-string value))
          (codec/v-hash value)))
      (codec/v-hash value)))

  (-chunked-resource-handles [_ batch-db tid modifier value]
    (coll/eduction
     (u/resource-handle-chunk-mapper batch-db tid)
     (if (map? value)
       (resource-keys-new batch-db type-byte-index tid code-id value)
       (resource-keys batch-db (u/c-hash-w-modifier c-hash code modifier) tid
                      value))))

  (-resource-handles [_ batch-db tid modifier value]
    (coll/eduction
     (u/resource-handle-mapper batch-db tid)
     (if (map? value)
       (resource-keys-new batch-db type-byte-index tid code-id value)
       (resource-keys batch-db (u/c-hash-w-modifier c-hash code modifier) tid
                      value))))

  (-resource-handles [_ batch-db tid modifier value start-id]
    (coll/eduction
     (u/resource-handle-mapper batch-db tid)
     (if (map? value)
       (resource-keys-new batch-db type-byte-index tid code-id value start-id)
       (resource-keys batch-db (u/c-hash-w-modifier c-hash code modifier) tid
                      value start-id))))

  (-compartment-keys [_ context compartment tid value]
    (if (map? value)
      ;; patient compartment only
      (when (= -1508652135 (first compartment))
        (let [tb (type-byte-index (codec/tid->type tid))
              {:keys [value system-id]} value]
          (pt-sp-tfr/prefix-keys (:snapshot context) (second compartment) tb
                                 code-id value (or system-id codec/null-system-id))))
      (c-sp-vr/prefix-keys (:snapshot context) compartment c-hash tid value)))

  (-matcher [_ batch-db modifier values]
    (if (some map? values)
      (value-filter (:snapshot batch-db) type-byte-index code-id values)
      (r-sp-v/value-prefix-filter (:snapshot batch-db)
                                  (u/c-hash-w-modifier c-hash code modifier)
                                  values)))

  (-compartment-ids [_ resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction
       (keep
        (fn [value]
          (when (identical? :fhir/Reference (fhir-spec/fhir-type value))
            (when-let [reference (type/value (:reference value))]
              (when-let [[type id] (fsr/split-literal-ref reference)]
                (when (= "Patient" type)
                  id))))))
       values)))

  (-index-entries [search-param resolver linked-compartments hash resource]
    (when-ok [entries
              (u/index-entries search-param resolver linked-compartments hash
                               resource)
              new-entries
              (new-index-entries type-byte-index kv-store resolver code-id
                                 expression linked-compartments hash resource)]
      (coll/eduction cat [entries new-entries])))

  (-index-values [search-param resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction (p/-index-value-compiler search-param) values)))

  (-index-value-compiler [_]
    (mapcat (partial index-entries url))))

(defn- resource-handles
  ([batch-db c-hash tid value]
   (into
    []
    (u/resource-handle-mapper batch-db tid)
    (resource-keys batch-db c-hash tid (codec/v-hash value))))
  ([batch-db c-hash tid value start-id]
   (into
    []
    (u/resource-handle-mapper batch-db tid)
    (resource-keys batch-db c-hash tid (codec/v-hash value) start-id))))

(def ^:private noop-resolver
  (reify fhir-path/Resolver (-resolve [_ _])))

(defn- identifier-values [{:keys [value system]}]
  (let [value (type/value value)
        system (type/value system)]
    (cond-> []
      value
      (conj value)
      system
      (conj (str system "|"))
      (and value system)
      (conj (str system "|" value))
      (and value (nil? system))
      (conj (str "|" value)))))

(defn- matches-identifier-values? [db expression value-set resource-handle]
  (let [resource @(d/pull db resource-handle)
        values (fhir-path/eval noop-resolver expression resource)]
    (assert (not (ba/anomaly? values)))
    (some value-set (mapcat identifier-values values))))

(defrecord SearchParamTokenIdentifier [name url type base code target c-hash expression]
  p/SearchParam
  (-compile-value [_ _ value]
    value)

  (-chunked-resource-handles [search-param batch-db tid modifier value]
    [(p/-resource-handles search-param batch-db tid modifier value)])

  (-resource-handles [_ batch-db tid modifier value]
    (let [c-hash (c-hash-w-modifier c-hash code modifier)
          resource-handles (resource-handles batch-db c-hash tid value)]
      (filterv (partial matches-identifier-values? batch-db expression #{value}) resource-handles)))

  (-resource-handles [_ batch-db tid modifier value start-id]
    (let [c-hash (c-hash-w-modifier c-hash code modifier)
          resource-handles (resource-handles batch-db c-hash tid value start-id)]
      (filterv (partial matches-identifier-values? batch-db expression #{value}) resource-handles)))

  (-compartment-keys [_ _ _ _ _])

  (-matcher [_ batch-db modifier values]
    (comp
     (r-sp-v/value-prefix-filter (:snapshot batch-db)
                                 (c-hash-w-modifier c-hash code modifier)
                                 (mapv codec/v-hash values))
     (filter (partial matches-identifier-values? batch-db expression (set values)))))

  (-compartment-ids [_ _ _])

  (-index-values [search-param resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction (p/-index-value-compiler search-param) values)))

  (-index-value-compiler [_]
    (mapcat (partial index-entries url))))

(defrecord SearchParamId [name type code]
  p/SearchParam
  (-compile-value [_ _ value]
    (codec/id-byte-string value))

  (-chunked-resource-handles [search-param batch-db tid modifier value]
    [(p/-resource-handles search-param batch-db tid modifier value)])

  (-resource-handles [_ batch-db tid _ value]
    (some-> (u/non-deleted-resource-handle batch-db tid value) vector))

  (-resource-handles [sp batch-db tid modifier value start-id]
    (when (= value start-id)
      (p/-resource-handles sp batch-db tid modifier value)))

  (-sorted-resource-handles [_ batch-db tid _]
    (rao/type-list batch-db tid))

  (-sorted-resource-handles [_ batch-db tid _ start-id]
    (rao/type-list batch-db tid start-id))

  (-index-entries [search-param resolver linked-compartments hash resource]
    (u/index-entries search-param resolver linked-compartments hash resource))

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
  [_ {:keys [name url type base code target expression]}]
  (if (= "_id" code)
    (->SearchParamId "_id" "id" "_id")
    (if expression
      (when-ok [expression (fhir-path/compile (fix-expr url expression))]
        (if (= "identifier" code)
          (->SearchParamTokenIdentifier name url type base code target (codec/c-hash code) expression)
          (->SearchParamToken name url type base code target (codec/c-hash code) expression)))
      (ba/unsupported (u/missing-expression-msg url)))))

(defmethod sc/search-param "uri"
  [_ {:keys [name url type base code target expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamToken name url type base code target (codec/c-hash code) expression))
    (ba/unsupported (u/missing-expression-msg url))))
