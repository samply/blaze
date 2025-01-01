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

(defn- find-version [{:keys [system-versions]} system]
  (some
   #(let [[s v] (str/split (type/value %) #"\|")]
      (when (= system s) v))
   system-versions))

(defn- find-code-system [{:keys [request] :as context} {:keys [system version]}]
  (condp = (type/value version)
    "*" (ac/completed-future (all-version-expansion-anom (type/value system)))
    nil (if-let [version (find-version request (type/value system))]
          (cs/find context (type/value system) version)
          (cs/find context (type/value system)))
    (cs/find context (type/value system) (type/value version))))

(defn- expand-filters [request inactive code-system filters]
  (->> (map (partial cs/expand-filter request inactive code-system) filters)
       (apply set/intersection)))

(defn- expand-code-system [{:keys [request]} inactive code-system concepts filters]
  (cond
    (seq concepts) (cs/expand-concept request inactive code-system concepts)
    (seq filters) (expand-filters request inactive code-system filters)
    :else (cs/expand-complete request inactive code-system)))

(defn- used-codesystem-parameter [{:keys [url version]}]
  {:fhir/type :fhir.ValueSet.expansion/parameter
   :name #fhir/string"used-codesystem"
   :value (type/uri (cond-> (type/value url) (type/value version) (str "|" (type/value version))))})

(defn- version-parameter [url version]
  {:fhir/type :fhir.ValueSet.expansion/parameter
   :name #fhir/string"version"
   :value (type/uri (str url "|" version))})

(defn- code-system-parameters [{:keys [url version] :as code-system}]
  (cond-> #{(used-codesystem-parameter code-system)}
    (type/value version) (conj (version-parameter (type/value url) (type/value version)))))

(defn- include-system
  [context inactive {concepts :concept filters :filter :as include}]
  (if (and (seq concepts) (seq filters))
    (ac/completed-future (ba/incorrect "Incorrect combination of concept and filter."))
    (do-sync [code-system (find-code-system context include)]
      (when-ok [concepts (expand-code-system context inactive code-system concepts filters)]
        {:parameter (code-system-parameters code-system)
         :contains concepts}))))

(declare expand-value-set)

(defn- expand-value-set-by-canonical [context canonical]
  (-> (vs/find context canonical)
      (ac/then-compose (partial expand-value-set context))))

(defn- include-value-sets [context value-sets]
  (let [futures (mapv #(expand-value-set-by-canonical context (type/value %)) value-sets)]
    (do-sync [_ (ac/all-of futures)]
      (transduce (map (comp #(select-keys % [:parameter :contains]) :expansion ac/join)) (partial merge-with into) futures))))

(defn- include [context inactive {:keys [system] value-sets :valueSet :as include}]
  (cond
    (and system value-sets)
    (ac/completed-future (ba/incorrect "Incorrect combination of system and valueSet."))

    system (include-system context inactive include)
    value-sets (include-value-sets context value-sets)

    :else (ac/completed-future (ba/incorrect "Missing system or valueSet."))))

(defn- expand-includes [context inactive includes]
  (let [futures (mapv (partial include context inactive) includes)]
    (do-sync [_ (ac/all-of futures)]
      (transduce (map ac/join) (partial merge-with into) futures))))

(defn- remove-excludes-duplicates [includes excludes]
  (into [] (comp (distinct) (remove (set excludes))) includes))

(defn- count-parameter [count]
  {:fhir/type :fhir.ValueSet.expansion/parameter
   :name #fhir/string"count"
   :value (type/integer count)})

(defn- include-designations-parameter [include-designations]
  {:fhir/type :fhir.ValueSet.expansion/parameter
   :name #fhir/string"includeDesignations"
   :value (type/boolean include-designations)})

(defn- active-only-parameter [active-only]
  {:fhir/type :fhir.ValueSet.expansion/parameter
   :name #fhir/string"activeOnly"
   :value (type/boolean active-only)})

(defn- exclude-nested-parameter [exclude-nested]
  {:fhir/type :fhir.ValueSet.expansion/parameter
   :name #fhir/string"excludeNested"
   :value (type/boolean exclude-nested)})

(defn- append-params
  [parameters {:keys [count include-designations active-only exclude-nested]}]
  (cond-> parameters
    count (conj (count-parameter count))
    (some? include-designations) (conj (include-designations-parameter include-designations))
    (some? active-only) (conj (active-only-parameter active-only))
    (some? exclude-nested) (conj (exclude-nested-parameter exclude-nested))))

(defn- append-property [property]
  (cond-> {:fhir/type :fhir.ValueSet.expansion/property
           :code (type/code property)}
    (#{"status" "definition"} property)
    (assoc :uri (type/uri (str "http://hl7.org/fhir/concept-properties#" property)))))

(defn- append-properties [properties]
  (mapv append-property properties))

(defn- expansion
  [{:keys [clock] {:keys [properties count] :as request} :request} parameters
   concepts]
  (cond->
   {:fhir/type :fhir.ValueSet/expansion
    :identifier (type/uri (str "urn:uuid:" (random-uuid)))
    :timestamp (time/offset-date-time clock)
    :total (clojure.core/count concepts)
    :parameter (append-params parameters request)}
    (seq properties) (assoc :property (append-properties properties))
    (nil? count) (assoc :contains concepts)
    (pos-int? count) (assoc :contains (into [] (take count) concepts))))

(defn- expand-value-set**
  [{{:keys [include-definition] :or {include-definition false}} :request
    :as context}
   {{:keys [inactive] includes :include excludes :exclude} :compose :as value-set}]
  (let [includes (expand-includes context inactive includes)
        excludes (expand-includes context inactive excludes)]
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
