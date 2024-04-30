(ns blaze.db.impl.search-param.reference
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.compartment.search-param-value-resource :as c-sp-vr]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.resource-search-param-reference-local :as r-sp-rl]
   [blaze.db.impl.index.resource-search-param-reference-url :as r-sp-ru]
   [blaze.db.impl.index.resource-search-param-token-full :as r-sp-tf]
   [blaze.db.impl.index.resource-search-param-token-system :as r-sp-ts]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.index.type-search-param-reference-local-resource :as t-sp-rlr]
   [blaze.db.impl.index.type-search-param-reference-url-resource :as t-sp-rur]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.core :as sc]
   [blaze.db.impl.search-param.reference.impl :as impl]
   [blaze.db.impl.search-param.search-param-code-registry :as search-param-code-registry]
   [blaze.db.impl.search-param.token :as spt]
   [blaze.db.impl.search-param.token.impl :as spt-impl]
   [blaze.db.impl.search-param.util :as u]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.references :as fsr]
   [blaze.fhir.spec.type :as type]
   [clojure.string :as str]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defmulti index-entries
  "Returns index entries for `value` from a resource.

  Index entries are `[modifier value include-in-compartments?]` triples."
  {:arglists '([url value])}
  (fn [_ value] (fhir-spec/fhir-type value)))

(defmethod index-entries :fhir/canonical
  [_ canonical]
  (when-let [value (type/value canonical)]
    (let [[url version-parts] (u/canonical-parts value)]
      (into
       [[nil (codec/v-hash value)]
        ["below" (codec/v-hash url)]]
       (map
        (fn [version-part]
          ["below" (codec/v-hash (str url "|" version-part))]))
       version-parts))))

(defn- literal-reference-entries [reference]
  (when-let [value (type/value reference)]
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
     (conj (u/identifier-index-entries "identifier" identifier)))))

(defmethod index-entries :default
  [url value]
  (log/warn (u/format-skip-indexing-msg value url "reference")))

(defmulti new-index-entries*
  {:arglists '([kv-store type-byte-index tb code-id id hash value])}
  (fn [_ _ _ _ _ _ value] (fhir-spec/fhir-type value)))

(defmethod new-index-entries* :fhir/canonical
  [_ _ tb code-id id hash value]
  (when-let [value (type/value value)]
    (let [[url version] (str/split value #"\|" 2)
          url (bs/from-utf8-string (or url value))
          version (or (some-> version bs/from-utf8-string)
                      #blaze/byte-string"")]
      [(t-sp-rur/index-entry tb code-id url version id hash)
       (r-sp-ru/index-entry tb id hash code-id url version)])))

(defn- new-literal-reference-entries [type-byte-index tb code-id reference id hash]
  (when-let [value (type/value reference)]
    (if-let [[ref-type ref-id] (fsr/split-literal-ref value)]
      [(t-sp-rlr/index-entry tb code-id (codec/id-byte-string ref-id)
                             (type-byte-index ref-type) id hash)
       (r-sp-rl/index-entry tb id hash code-id (codec/id-byte-string ref-id)
                            (type-byte-index ref-type))]
      [(t-sp-rur/index-entry tb code-id (bs/from-utf8-string value) #blaze/byte-string"" id hash)
       (r-sp-ru/index-entry tb id hash code-id (bs/from-utf8-string value) #blaze/byte-string"")])))

(defmethod new-index-entries* :fhir/Reference
  [type-byte-index kv-store tb code-id id hash {:keys [reference identifier]}]
  (coll/eduction
   cat
   (cond-> []
     reference
     (conj (new-literal-reference-entries type-byte-index tb code-id reference id hash))
     identifier
     (conj (spt/new-index-entries* type-byte-index kv-store tb code-id [] id hash identifier)))))

(defmethod new-index-entries* :default
  [_ _ _ _ _ _ _])

(defn- new-index-entries
  [type-byte-index kv-store resolver code-id expression hash
   {:keys [id] :as resource}]
  (let [id (codec/id-byte-string id)
        type (name (fhir-spec/fhir-type resource))
        tb (type-byte-index type)]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (into
       []
       (comp
        (mapcat (partial new-index-entries* type-byte-index kv-store tb code-id id hash))
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

(defn- start-version
  [{:keys [snapshot t]} type-byte-index tid search-param-code-id url start-id]
  (when-let [resource-handle (rao/resource-handle snapshot tid start-id t)]
    (r-sp-ru/version snapshot type-byte-index resource-handle
                     search-param-code-id url)))

(defn resource-keys-new*
  "Returns a reducible collection of `[id hash-prefix]` tuples that have `value`
  starting at `start-id` (optional)."
  ([{:keys [snapshot]} type-byte-index tid search-param-code-id
    {::keys [modifier] :keys [url version ref-id ref-tb]}]
   (let [tb (type-byte-index (codec/tid->type tid))]
     (if url
       (if version
         (if (= :below modifier)
           (t-sp-rur/prefix-keys-version-prefix snapshot tb search-param-code-id url version)
           (t-sp-rur/prefix-keys-version snapshot tb search-param-code-id url version))
         (if (= :below modifier)
           (t-sp-rur/prefix-keys snapshot tb search-param-code-id url)
           (t-sp-rur/prefix-keys-version snapshot tb search-param-code-id url #blaze/byte-string"")))
       (if ref-tb
         (t-sp-rlr/prefix-keys snapshot tb search-param-code-id ref-id ref-tb)
         (t-sp-rlr/prefix-keys snapshot tb search-param-code-id ref-id)))))
  ([{:keys [snapshot] :as batch-db} type-byte-index tid search-param-code-id
    {::keys [modifier] :keys [url version ref-id ref-tb]} start-id]
   (let [tb (type-byte-index (codec/tid->type tid))]
     (if url
       (if version
         (if (= :below modifier)
           (when-let [start-version (start-version batch-db type-byte-index tid
                                                   search-param-code-id url start-id)]
             (t-sp-rur/prefix-keys-version-prefix snapshot tb search-param-code-id
                                                  url version start-version start-id))
           (t-sp-rur/prefix-keys-version snapshot tb search-param-code-id url version start-id))
         (if (= :below modifier)
           (when-let [start-version (start-version batch-db type-byte-index tid
                                                   search-param-code-id url start-id)]
             (t-sp-rur/prefix-keys snapshot tb search-param-code-id url
                                   start-version start-id))
           (t-sp-rur/prefix-keys snapshot tb search-param-code-id url #blaze/byte-string"" start-id)))
       (if ref-tb
         (t-sp-rlr/prefix-keys snapshot tb search-param-code-id ref-id ref-tb)
         (t-sp-rlr/prefix-keys snapshot tb search-param-code-id ref-id))))))

(defn resource-keys-new
  "Returns a reducible collection of `[id hash-prefix]` tuples that have `value`
  starting at `start-id` (optional)."
  ([batch-db type-byte-index tid search-param-code-id {::keys [type] :as value}]
   (if (= :token type)
     (spt/resource-keys-new batch-db type-byte-index tid search-param-code-id
                            value)
     (resource-keys-new* batch-db type-byte-index tid search-param-code-id
                         value)))
  ([batch-db type-byte-index tid search-param-code-id {::keys [type] :as value}
    start-id]
   (if (= :token type)
     (spt/resource-keys-new batch-db type-byte-index tid search-param-code-id
                            value start-id)
     (resource-keys-new* batch-db type-byte-index tid search-param-code-id
                         value start-id))))

(defn- value-filter [snapshot type-byte-index code-id values]
  (let [token-values (filterv (comp #{:token} ::type) values)
        url-values (filterv (comp some? :url) values)
        full (filterv (comp some? :value) values)]
    (cond
      (and (seq url-values) (seq full))
      (comp (r-sp-ts/value-filter snapshot type-byte-index code-id url-values)
            (r-sp-tf/value-filter snapshot type-byte-index code-id full))

      (seq token-values)
      (spt/value-filter snapshot type-byte-index code-id token-values)

      (seq url-values)
      (r-sp-ru/value-filter snapshot type-byte-index code-id url-values)

      (seq full)
      (r-sp-tf/value-filter snapshot type-byte-index code-id full))))

(defrecord SearchParamReference [type-byte-index kv-store name url type base
                                 code target c-hash code-id expression]
  p/SearchParam
  (-compile-value [_ modifier value]
    (if use-new-indices
      (if (= "identifier" modifier)
        (when-ok [value (spt-impl/compile-value-new kv-store value)]
          (assoc value ::type :token))
        (cond-> (impl/compile-value-new type-byte-index value)
          (= "below" modifier)
          (assoc ::modifier :below)))
      (codec/v-hash value)))

  (-compile-value-composite [_ _ value]
    (codec/v-hash value))

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
    (c-sp-vr/prefix-keys (:snapshot context) compartment c-hash tid value))

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
              (some-> (fsr/split-literal-ref reference) (coll/nth 1))))))
       values)))

  (-index-entries [search-param resolver linked-compartments hash resource]
    (when-ok [entries
              (u/index-entries search-param resolver linked-compartments hash
                               resource)
              new-entries
              (new-index-entries type-byte-index kv-store resolver code-id
                                 expression hash resource)]
      (coll/eduction cat [entries new-entries])))

  (-index-values [search-param resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction (p/-index-value-compiler search-param) values)))

  (-index-value-compiler [_]
    (mapcat (partial index-entries url))))

(defmethod sc/search-param "reference"
  [{:keys [kv-store type-byte-index]} _
   {:keys [name url type base code target expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile expression)
              code-id (search-param-code-registry/id-of kv-store code)]
      (->SearchParamReference type-byte-index kv-store name url type base code
                              target (codec/c-hash code) code-id expression))
    (ba/unsupported (u/missing-expression-msg url))))
