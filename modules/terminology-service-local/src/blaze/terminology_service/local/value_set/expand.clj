(ns blaze.terminology-service.local.value-set.expand
  (:refer-clojure :exclude [filter str])
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.spec]
   [blaze.terminology-service.local.code-system :as cs]
   [blaze.terminology-service.local.value-set :as vs]
   [blaze.terminology-service.local.value-set.util :as vs-u]
   [blaze.time :as bt]
   [blaze.util :refer [str]]
   [clojure.set :as set]
   [cognitect.anomalies :as anom]))

(set! *warn-on-reflection* true)

(defn- all-version-expansion-msg [url]
  (format "Expanding the code system `%s` in all versions is unsupported." url))

(defn- all-version-expansion-anom [url]
  (ba/unsupported (all-version-expansion-msg url)))

(defn- find-code-system
  [{:keys [params] :as context} {{system :value} :system {version :value} :version}]
  (condp = version
    "*" (ac/completed-future (all-version-expansion-anom system))
    nil (if-let [version (vs-u/find-version params system)]
          (cs/find context system version)
          (cs/find context system))
    (cs/find context system version)))

(defn- expand-filters [code-system filters params]
  (->> (map #(cs/expand-filter code-system % params) filters)
       (apply set/intersection)))

(defn- expand-code-system [code-system concepts filters params]
  (cond
    (seq concepts) (cs/expand-concept code-system concepts params)
    (seq filters) (expand-filters code-system filters params)
    :else (cs/expand-complete code-system params)))

(defn- used-codesystem-parameter [url version]
  (type/fhir-map {:fhir/type :fhir.ValueSet.expansion/parameter
                  :name #fhir/string "used-codesystem"
                  :value (type/uri-interned (cond-> url version (str "|" version)))}))

(defn- version-parameter [url version]
  (type/fhir-map {:fhir/type :fhir.ValueSet.expansion/parameter
                  :name #fhir/string "version"
                  :value (type/uri-interned (str url "|" version))}))

(defn- code-system-parameters [{{url :value} :url {version :value} :version}]
  (cond-> #{(used-codesystem-parameter url version)}
    version (conj (version-parameter url version))))

(defn- include-system
  [{:keys [params] :as context} {concepts :concept filters :filter :as include}]
  (if (and (seq concepts) (seq filters))
    (ac/completed-future (ba/incorrect "Incorrect combination of concept and filter."))
    (do-sync [code-system (find-code-system context include)]
      (when-ok [concepts (expand-code-system code-system concepts filters params)]
        {:parameter (code-system-parameters code-system)
         :contains concepts}))))

(declare expand-value-set)

(defn- expand-value-set-by-canonical [context canonical]
  (-> (vs/find context canonical)
      (ac/then-compose (partial expand-value-set context))))

(defn- include-value-sets [context value-sets]
  (let [futures (mapv #(expand-value-set-by-canonical context (:value %)) value-sets)]
    (do-sync [_ (ac/all-of futures)]
      ;; plain `select-keys`, because the result is the internal accumulator
      ;; `include-system` builds, not a FHIR value
      (transduce (map (comp #(select-keys % [:parameter :contains]) :expansion ac/join))
                 (partial merge-with into) futures))))

(defn- include [context {:keys [system] value-sets :valueSet :as include}]
  (cond
    (and system value-sets)
    (ac/completed-future (ba/incorrect "Incorrect combination of system and valueSet."))

    system (include-system context include)
    value-sets (include-value-sets context value-sets)

    :else (ac/completed-future (ba/incorrect "Missing system or valueSet."))))

(defn- expand-includes [context includes]
  (let [futures (mapv (partial include context) includes)]
    (do-sync [_ (ac/all-of futures)]
      (transduce (map ac/join) (partial merge-with into) futures))))

(defn- remove-excludes-duplicates [includes excludes]
  (into [] (comp (distinct) (remove (set excludes))) includes))

(defn- count-parameter [count]
  (type/fhir-map {:fhir/type :fhir.ValueSet.expansion/parameter
                  :name #fhir/string "count"
                  :value (type/integer count)}))

(defn- include-designations-parameter [include-designations]
  (type/fhir-map {:fhir/type :fhir.ValueSet.expansion/parameter
                  :name #fhir/string "includeDesignations"
                  :value (type/boolean include-designations)}))

(defn- active-only-parameter [active-only]
  (type/fhir-map {:fhir/type :fhir.ValueSet.expansion/parameter
                  :name #fhir/string "activeOnly"
                  :value (type/boolean active-only)}))

(defn- exclude-nested-parameter [exclude-nested]
  (type/fhir-map {:fhir/type :fhir.ValueSet.expansion/parameter
                  :name #fhir/string "excludeNested"
                  :value (type/boolean exclude-nested)}))

(defn- filter-parameter [filter-text]
  (type/fhir-map {:fhir/type :fhir.ValueSet.expansion/parameter
                  :name #fhir/string "filter"
                  :value (type/string filter-text)}))

(defn- append-params
  [parameters {:keys [count include-designations active-only exclude-nested
                      filter]}]
  (cond-> parameters
    count (conj (count-parameter count))
    (some? include-designations) (conj (include-designations-parameter include-designations))
    (some? active-only) (conj (active-only-parameter active-only))
    (some? exclude-nested) (conj (exclude-nested-parameter exclude-nested))
    filter (conj (filter-parameter filter))))

(defn- contains-value
  "Turns the internally built `concept` into a `ValueSet.expansion.contains`
  value.

  The code system implementations build their concepts as plain maps, because
  they are collected in sets and compared with each other before they become
  part of the expansion. Concepts taken from an already expanded value set are
  contains values already."
  [concept]
  (if (:fhir/type concept)
    concept
    (type/fhir-map (assoc concept :fhir/type :fhir.ValueSet.expansion/contains))))

(defn- expansion
  [{:keys [clock] {:keys [count] :as params} :params} parameters concepts]
  (cond->
   (type/fhir-map {:fhir/type :fhir.ValueSet/expansion
                   :identifier (type/uri (str "urn:uuid:" (random-uuid)))
                   :timestamp (type/dateTime (bt/offset-date-time clock))
                   :total (type/integer (clojure.core/count concepts))
                   :parameter (append-params parameters params)})
    (nil? count) (assoc :contains (mapv contains-value concepts))
    (pos-int? count) (assoc :contains (into [] (comp (take count) (map contains-value)) concepts))))

(defn- expand-value-set**
  [{{:keys [include-definition] :or {include-definition false}} :params
    :as context}
   {{{inactive :value} :inactive includes :include excludes :exclude} :compose :as value-set}]
  (let [new-context (update-in context [:params :active-only] #(or % (false? inactive)))
        includes (expand-includes new-context includes)
        excludes (expand-includes new-context excludes)]
    (do-sync [_ (ac/all-of [includes excludes])]
      (let [includes (ac/join includes)
            excludes (ac/join excludes)
            concepts (remove-excludes-duplicates (:contains includes) (:contains excludes))]
        (cond->
         (assoc
          value-set
          :expansion
          (expansion context (vec (:parameter includes)) concepts))
          (not include-definition) (dissoc :compose))))))

(defn- expand-value-set-msg [{{url :value} :url}]
  (if url
    (format "Error while expanding the value set `%s`. " url)
    "Error while expanding the provided value set. "))

(defn- expand-value-set* [context {:keys [expansion] :as value-set}]
  (if expansion
    (ac/completed-future value-set)
    (-> (expand-value-set** context value-set)
        (ac/exceptionally
         #(update % ::anom/message (partial str (expand-value-set-msg value-set)))))))

(defn expand-value-set
  "Returns a CompletableFuture that will complete with the expanded `value-set`
  or will complete exceptionally with an anomaly in case of errors."
  [context value-set]
  (expand-value-set* context (dissoc value-set :id :meta)))
