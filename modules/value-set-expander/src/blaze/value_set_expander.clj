(ns blaze.value-set-expander
  "Expands the value sets of search clauses into plain token clauses.

  A clause with a token search param and the `in` modifier, like
  `[\"code:in\" url]`, matches resources with a code from the value set with
  `url`. The outcome of expanding a value set can change over time. So the
  database doesn't expand value sets itself. Instead, callers expand them
  before giving the clauses to the database, so that the transaction log only
  holds clauses whose outcome is determined by the log itself and all pages of
  a search use the same expansion."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.module :as m]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.spec]
   [blaze.util :refer [str]]
   [blaze.value-set-expander.protocols :as p]
   [blaze.value-set-expander.spec]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [java-time.api :as time]
   [taoensso.timbre :as log])
  (:import
   [com.github.benmanes.caffeine.cache AsyncCacheLoader AsyncLoadingCache Caffeine]))

(set! *warn-on-reflection* true)

(defn expand-clauses
  "Returns a CompletableFuture that will complete with `clauses` of resources
  of `type` in which every clause with a token search param and the `in`
  modifier is replaced by a plain token clause holding the distinct codes of
  all its value sets in sorted order.

  Completes with nil if `clauses` can't match any resource, because a clause
  holds only value sets that expand to no codes. An empty value set can't
  become a plain token clause, because such a clause would need at least one
  value.

  Each replaced clause carries the clause it was expanded from in its metadata
  (see `original-clauses`). Clauses with other search params, also with the
  `in` modifier, are kept, so that the database rejects them as usual.

  Completes exceptionally with an anomaly if a value set can't be expanded."
  [expander type clauses]
  (p/-expand-clauses expander type clauses))

(defn expand-condition-clauses
  "Returns a CompletableFuture that will complete with the `clauses` of a
  condition of resources of `type` in which value sets are expanded like
  `expand-clauses` does.

  A condition is the If-None-Exist of a conditional create or the search of a
  conditional delete. Conditions can't hold disjunctions.

  Completes exceptionally with an anomaly with HTTP status 422 if all value sets
  of a clause expand to no codes, because such a condition can't match any
  resource. A conditional create would always create and a conditional delete
  would never delete, which is most likely not what the client intended.

  Completes exceptionally with a too-costly conflict if the value sets of the
  condition expand to more than 10,000 codes, because conditions are stored in
  the transaction log together with their expanded codes.

  Completes exceptionally with an anomaly if a value set can't be expanded."
  [expander type clauses]
  (p/-expand-condition-clauses expander type clauses))

(defn- disjunction? [clause]
  (vector? (first clause)))

(defn- clause-originals [clause]
  (if (disjunction? clause) clause [clause]))

(defn- originals
  "Returns a map of each expanded clause in `expanded-clauses` to the clause it
  was expanded from."
  [expanded-clauses]
  (into
   {}
   (comp (mapcat clause-originals)
         (keep #(when-let [original (::original (meta %))] [% original])))
   expanded-clauses))

(defn original-clauses
  "Returns `clauses` with every clause that `expand-clauses` produced in
  `expanded-clauses` replaced by the clause it was expanded from.

  Used to show the clauses as the client wrote them, for example in the self
  link of a search, also after `clauses` were decoded from a query compiled
  from `expanded-clauses`."
  [expanded-clauses clauses]
  (let [originals (originals expanded-clauses)]
    (if (empty? originals)
      clauses
      (mapv
       (fn [clause]
         (if (disjunction? clause)
           (mapv #(get originals % %) clause)
           (get originals clause clause)))
       clauses))))

(defn expanded?
  "Returns true if one of `clauses` was expanded by `expand-clauses`."
  [clauses]
  (boolean (some (comp ::original meta) (mapcat clause-originals clauses))))

(defn- url-param [url]
  {:fhir/type :fhir.Parameters/parameter
   :name #fhir/string "url"
   :value (type/uri url)})

(defn- parameters [url]
  {:fhir/type :fhir/Parameters
   :parameter [(url-param url)]})

(defn- concept-value [{:keys [system code]}]
  (str (:value system) "|" (:value code)))

(defn- concept-values [url value-set]
  (let [values (mapv concept-value (:contains (:expansion value-set)))]
    (log/trace "Expanded the value set with URL" url "into" (count values) "codes")
    values))

(defn- expand-value-set [terminology-service url]
  (do-sync [value-set (ts/expand-value-set terminology-service (parameters url))]
    (concept-values url value-set)))

(defn- expansion-cache
  "Returns a cache of the codes of value sets by URL.

  Expansions are refreshed one hour after they were loaded. Failed expansions
  aren't kept."
  [terminology-service]
  (-> (Caffeine/newBuilder)
      (.maximumSize 1000)
      (.refreshAfterWrite (time/hours 1))
      (^[AsyncCacheLoader] Caffeine/.buildAsync
       (fn [url _]
         (expand-value-set terminology-service url)))))

(defn- expand-value-set-msg [param url cause-msg]
  (format "Error in search parameter `%s`: Error while expanding the ValueSet `%s`. Cause: %s"
          param url cause-msg))

(defn- codes [cache param url]
  (-> (.get ^AsyncLoadingCache cache url)
      (ac/exceptionally
       #(ba/fault (expand-value-set-msg param url (::anom/message %))))))

(defn- expand-value-set-clause
  "Returns a CompletableFuture of the plain token clause of `clause` with the
  search param `search-param-code` or of nil if all its value sets are empty."
  [cache search-param-code [param & urls :as clause]]
  (let [futures (mapv (partial codes cache param) urls)]
    (do-sync [_ (ac/all-of futures)]
      (when-let [codes (seq (into (sorted-set) (mapcat ac/join) futures))]
        (with-meta (into [search-param-code] codes) {::original clause})))))

(defn- value-set-param-code
  "Returns the code of the search param of `clause` without its `in` modifier
  if `clause` has value sets as values (see `sr/value-set-param-code`)."
  [search-param-registry type [param & values]]
  (when (and (string? param) (seq values))
    (sr/value-set-param-code search-param-registry type param)))

(defn- expand-search-clause
  [{:keys [search-param-registry cache]} type clause]
  (if-let [search-param-code (value-set-param-code search-param-registry type clause)]
    (expand-value-set-clause cache search-param-code clause)
    (ac/completed-future clause)))

(defn- expand-disjunction
  "Returns a CompletableFuture of the expanded `disjunction` without the
  clauses that can't match anything, or of nil if no clause is left."
  [context type disjunction]
  (do-sync [clauses (ac/all-values (mapv (partial expand-search-clause context type) disjunction))]
    (not-empty (into [] (remove nil?) clauses))))

(defn- empty-value-sets-msg [[param & urls]]
  (if (= 1 (count urls))
    (format "Error in search parameter `%s`: The ValueSet `%s` expands to no codes. A condition with such a value set can't match any resource."
            param (first urls))
    (format "Error in search parameter `%s`: The ValueSets `%s` expand to no codes. A condition with such value sets can't match any resource."
            param (str/join "`, `" urls))))

(defn- expand-condition-clause
  "Returns a CompletableFuture of the expanded `clause` of a condition, which
  completes exceptionally if all value sets of `clause` expand to no codes."
  [context type clause]
  (do-sync [expanded-clause (expand-search-clause context type clause)]
    (or expanded-clause
        (ba/incorrect (empty-value-sets-msg clause) :http/status 422))))

(def ^:private ^:const ^long max-condition-codes 10000)

(defn- num-expanded-codes
  "Returns the number of codes the clauses expanded from value sets in
  `clauses` hold."
  [clauses]
  (transduce (comp (filter (comp ::original meta)) (map (comp dec count))) + clauses))

(defn- too-many-condition-codes-msg [num-codes]
  (format "The value sets of the condition expand to %,d codes, which is more than the limit of %,d codes."
          num-codes max-condition-codes))

(defn- check-num-condition-codes
  "Returns `clauses` of a condition or an anomaly if they hold more than 10,000
  codes expanded from value sets.

  Conditions with expanded value sets are stored in the transaction log, so
  that large value sets would make its records too large."
  [clauses]
  (let [num-codes (num-expanded-codes clauses)]
    (if (< max-condition-codes num-codes)
      (ba/conflict (too-many-condition-codes-msg num-codes) :fhir/issue "too-costly")
      clauses)))

(defn- expand-clause [context type clause]
  (if (disjunction? clause)
    (expand-disjunction context type clause)
    (expand-search-clause context type clause)))

(defrecord ValueSetExpander [search-param-registry cache]
  p/ValueSetExpander
  (-expand-clauses [context type clauses]
    (do-sync [expanded-clauses (ac/all-values (mapv (partial expand-clause context type) clauses))]
      (when (every? some? expanded-clauses)
        expanded-clauses)))
  (-expand-condition-clauses [context type clauses]
    (do-sync [expanded-clauses (ac/all-values (mapv (partial expand-condition-clause context type) clauses))]
      (check-num-condition-codes expanded-clauses))))

(defmethod m/pre-init-spec :blaze/value-set-expander [_]
  (s/keys :req-un [:blaze.db/search-param-registry :blaze/terminology-service]))

(defmethod ig/init-key :blaze/value-set-expander
  [_ {:keys [search-param-registry terminology-service]}]
  (log/info "Init value set expander")
  (->ValueSetExpander search-param-registry (expansion-cache terminology-service)))
