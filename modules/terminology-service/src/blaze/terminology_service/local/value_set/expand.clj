(ns blaze.terminology-service.local.value-set.expand
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.spec]
   [blaze.terminology-service.local.code-system :as cs]
   [blaze.terminology-service.local.value-set :as vs]
   [clojure.set :as set]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [java-time.api :as time]))

(defn- all-version-expansion-msg [url]
  (format "Expanding the code system `%s` in all versions is unsupported." url))

(defn- all-version-expansion-anom [url]
  (ba/unsupported (all-version-expansion-msg url)))

(defn- find-version [{:keys [system-version]} system]
  (some
   #(let [[s v] (str/split (type/value %) #"\|")]
      (when (= system s) v))
   system-version))

(defn- find-code-system [{:keys [request] :as context} {:keys [system version]}]
  (condp = (type/value version)
    "*" (ac/completed-future (all-version-expansion-anom (type/value system)))
    nil (if-let [version (find-version request (type/value system))]
          (cs/find context (type/value system) version)
          (cs/find context (type/value system)))
    (cs/find context (type/value system) (type/value version))))

(defn- expand-filters [request code-system filters]
  (->> (map (partial cs/expand-filter request code-system) filters)
       (apply set/intersection)))

(defn- version-parameter [url version]
  {:fhir/type :fhir.ValueSet.expansion/parameter
   :name #fhir/string"version"
   :value (type/uri (str url "|" version))})

(defn- append-version [concepts {:keys [url version]}]
  (cond-> {:contains concepts}
    version (assoc :parameter #{(version-parameter url version)})))

(defn- expand-code-system [{:keys [request]} code-system concepts filters]
  (cond
    (seq concepts) (cs/expand-concept request code-system concepts)
    (seq filters) (expand-filters request code-system filters)
    :else (cs/expand-complete request code-system)))

(defn- include-system
  [context {concepts :concept filters :filter :as include}]
  (if (and (seq concepts) (seq filters))
    (ac/completed-future (ba/incorrect "Incorrect combination of concept and filter."))
    (do-sync [code-system (find-code-system context include)]
      (when-ok [concepts (expand-code-system context code-system concepts filters)]
        (append-version concepts code-system)))))

(declare expand-value-set)

(defn- expand-value-set-by-canonical [{:keys [db] :as context} canonical]
  (-> (vs/find db canonical)
      (ac/then-compose (partial expand-value-set context))))

(defn- include-value-sets [context value-sets]
  (let [futures (mapv #(expand-value-set-by-canonical context (type/value %)) value-sets)]
    (do-sync [_ (ac/all-of futures)]
      (transduce (map (comp #(select-keys % [:parameter :contains]) :expansion ac/join)) (partial merge-with into) futures))))

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
  {:fhir/type :fhir.ValueSet.expansion/parameter
   :name #fhir/string"count"
   :value (type/integer count)})

(defn- append-params [parameters {:keys [count]}]
  (cond-> parameters
    count (conj (count-parameter count))))

(defn- expansion [{:keys [clock] {:keys [count] :as request} :request} parameters concepts]
  (cond->
   {:fhir/type :fhir.ValueSet/expansion
    :identifier (type/uri (str "urn:uuid:" (random-uuid)))
    :timestamp (time/offset-date-time clock)
    :total (clojure.core/count concepts)
    :parameter (append-params parameters request)}
    (nil? count) (assoc :contains concepts)
    (pos-int? count) (assoc :contains (into [] (take count) concepts))))

(defn- expand-value-set**
  [{{:keys [include-definition] :or {include-definition false}} :request
    :as context}
   {{includes :include excludes :exclude} :compose :as value-set}]
  (let [includes (expand-includes context includes)
        excludes (expand-includes context excludes)]
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

(defn- expand-value-set-msg [{:keys [url]}]
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
