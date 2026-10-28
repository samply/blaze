(ns blaze.db.impl.search-param.composite.token-date
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.composite.common :as cc]
   [blaze.db.impl.search-param.date :as spd]
   [blaze.db.impl.search-param.util :as u]
   [blaze.fhir-path :as fhir-path]))

(set! *warn-on-reflection* true)

(def ^:private ^:const ^long prefix-length
  "Length of the prefix of the index values consisting of the token."
  codec/v-hash-size)

(defn- matches?
  "Returns true if the date part of the index `value` matches the
  `compiled-value`."
  [value compiled-value]
  (spd/matches? (bs/subs value prefix-length) compiled-value))

(defn- resource-values
  "Returns a reducible collection of the index values of the resource with
  `tid`, `id` and `hash-prefix` starting with the token of `compiled-value`."
  [snapshot tid id hash-prefix c-hash {:keys [token]}]
  (r-sp-v/prefix-keys snapshot tid id hash-prefix c-hash prefix-length token))

(defn- some-value-matches?
  "Returns true if one of the index values of the resource with `tid`, `id` and
  `hash-prefix` matches one of the `compiled-values`.

  Resources can have more than one value per token, so all of them have to be
  considered."
  [snapshot tid id hash-prefix c-hash compiled-values]
  (some
   (fn [compiled-value]
     (coll/some
      #(matches? % compiled-value)
      (resource-values snapshot tid id hash-prefix c-hash compiled-value)))
   compiled-values))

(defn- first-value
  "Returns the first index value of the resource with `tid` and `id` matching
  `compiled-value` or nil if there is none.

  The scan finds the resource first at that value, because the values of each
  token are scanned in ascending order."
  [{:keys [snapshot] :as batch-db} c-hash tid id compiled-value]
  (when-let [{:keys [hash-prefix]} (p/-resource-handle batch-db tid id)]
    (coll/first
     (coll/eduction
      (filter #(matches? % compiled-value))
      (resource-values snapshot tid id hash-prefix c-hash compiled-value)))))

(defn- remove-found-before-start
  "Returns a transducer which removes index handles of resources found before
  the scan position of `start-value` and `start-id`.

  Resources with more than one value matching `compiled-value` are found again
  at their later values. Starting the scan at `start-value` and `start-id`
  skips their first values, so their later values have to be removed."
  [batch-db c-hash tid compiled-value start-value start-id]
  (let [start-key (sp-vr/encode-seek-key c-hash tid start-value start-id)]
    (remove
     (fn [index-handle]
       (let [id (ih/id index-handle)]
         (when-let [value (first-value batch-db c-hash tid id compiled-value)]
           (bs/< (sp-vr/encode-seek-key c-hash tid value id) start-key)))))))

(defn- matching-index-handles [compiled-value]
  (comp
   (filter (fn [[value]] (matches? value compiled-value)))
   (map #(nth % 1))
   u/by-id-grouper))

(defn- index-handles
  "Returns a reducible collection of index handles of all keys with the token of
  `compiled-value` and a date matching `compiled-value` starting at `start-id`
  (optional)."
  ([{:keys [snapshot]} c-hash tid {:keys [token] :as compiled-value}]
   (coll/eduction
    (matching-index-handles compiled-value)
    (sp-vr/value-prefix-keys snapshot c-hash tid prefix-length token)))
  ([{:keys [snapshot] :as batch-db} c-hash tid compiled-value start-id]
   (if-let [start-value (first-value batch-db c-hash tid start-id compiled-value)]
     (coll/eduction
      (comp (matching-index-handles compiled-value)
            (remove-found-before-start batch-db c-hash tid compiled-value
                                       start-value start-id))
      (sp-vr/value-prefix-keys snapshot c-hash tid prefix-length start-value
                               start-id))
     [])))

(defn- matcher [{:keys [snapshot]} c-hash compiled-values]
  (filter
   (fn [{:keys [tid id hash-prefix]}]
     (some-value-matches? snapshot tid id hash-prefix c-hash compiled-values))))

(defn- single-version-id-matcher [{:keys [snapshot]} tid c-hash compiled-values]
  (filter
   (fn [single-version-id]
     (some-value-matches? snapshot tid (svi/id single-version-id)
                          (svi/hash-prefix single-version-id) c-hash
                          compiled-values))))

(defrecord SearchParamCompositeTokenDate [name url type base code c-hash
                                          main-expression c1 c2]
  p/SearchParam
  (-validate-modifier [_ modifier]
    (some->> modifier (u/unknown-modifier-anom code)))

  (-compile-value [_ _ value]
    (if-ok [[v1 v2] (cc/split-value value)]
      (let [f1 (cc/compile-component-value c1 v1)
            f2 (cc/compile-component-value c2 v2)]
        (-> (ac/all-of [f1 f2])
            (ac/then-apply (fn [_] (assoc (ac/join f2) :token (ac/join f1))))))
      ac/completed-future))

  (-estimated-scan-size [_ _ _ _ _]
    (ba/unsupported))

  (-supports-ordered-index-handles [_ _ _ _ _]
    false)

  (-ordered-index-handles [_ _ _ _ _]
    (ba/unsupported))

  (-ordered-index-handles [_ _ _ _ _ _]
    (ba/unsupported))

  (-index-handles [_ batch-db tid _ compiled-value]
    (index-handles batch-db c-hash tid compiled-value))

  (-index-handles [_ batch-db tid _ compiled-value start-id]
    (index-handles batch-db c-hash tid compiled-value start-id))

  (-supports-ordered-compartment-index-handles [_ _ _]
    false)

  (-ordered-compartment-index-handles [_ _ _ _ _ _]
    (ba/unsupported))

  (-ordered-compartment-index-handles [_ _ _ _ _ _ _]
    (ba/unsupported))

  (-matcher [_ batch-db _ compiled-values]
    (matcher batch-db c-hash compiled-values))

  (-single-version-id-matcher [_ batch-db tid _ compiled-values]
    (single-version-id-matcher batch-db tid c-hash compiled-values))

  (-postprocess-matches [_ _ _ _ _])

  (-index-values [_ resolver resource]
    (when-ok [values (fhir-path/eval resolver main-expression resource)]
      (coll/eduction (cc/index-values resolver c1 c2) values))))
