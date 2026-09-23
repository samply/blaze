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
   [blaze.util :refer [conj-vec str]]
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
  {:fhir/type :fhir.ValueSet.expansion/parameter
   :name #fhir/string "used-codesystem"
   :value (type/uri-interned (cond-> url version (str "|" version)))})

(defn- version-parameter [url version]
  {:fhir/type :fhir.ValueSet.expansion/parameter
   :name #fhir/string "version"
   :value (type/uri-interned (str url "|" version))})

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

(defn- expand-value-set-by-canonical
  "Expands the value set with `canonical` completely, because `count` applies
  only to the outer expansion."
  [context canonical]
  (-> (vs/find context canonical)
      (ac/then-compose
       (partial expand-value-set (update context :params dissoc :count)))))

(defn- include-value-sets [context value-sets]
  (let [futures (mapv #(expand-value-set-by-canonical context (:value %)) value-sets)]
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
   :name #fhir/string "count"
   :value (type/integer count)})

(defn- include-designations-parameter [include-designations]
  {:fhir/type :fhir.ValueSet.expansion/parameter
   :name #fhir/string "includeDesignations"
   :value (type/boolean include-designations)})

(defn- active-only-parameter [active-only]
  {:fhir/type :fhir.ValueSet.expansion/parameter
   :name #fhir/string "activeOnly"
   :value (type/boolean active-only)})

(defn- exclude-nested-parameter [exclude-nested]
  {:fhir/type :fhir.ValueSet.expansion/parameter
   :name #fhir/string "excludeNested"
   :value (type/boolean exclude-nested)})

(defn- filter-parameter [filter-text]
  {:fhir/type :fhir.ValueSet.expansion/parameter
   :name #fhir/string "filter"
   :value (type/string filter-text)})

(defn- append-params
  [parameters {:keys [count include-designations active-only exclude-nested
                      filter]}]
  (cond-> parameters
    count (conj (count-parameter count))
    (some? include-designations) (conj (include-designations-parameter include-designations))
    (some? active-only) (conj (active-only-parameter active-only))
    (some? exclude-nested) (conj (exclude-nested-parameter exclude-nested))
    filter (conj (filter-parameter filter))))

(defn- append-property [property]
  (cond-> {:fhir/type :fhir.ValueSet.expansion/property
           :code (type/code property)}
    (#{"status" "definition"} property)
    (assoc :uri (type/uri-interned (str "http://hl7.org/fhir/concept-properties#" property)))))

(defn- append-properties [properties]
  (mapv append-property properties))

(defn- expansion
  [{:keys [clock] {:keys [properties count] :as params} :params} parameters
   concepts]
  (cond->
   {:fhir/type :fhir.ValueSet/expansion
    :identifier (type/uri (str "urn:uuid:" (random-uuid)))
    :timestamp (type/dateTime (bt/offset-date-time clock))
    :total (type/integer (clojure.core/count concepts))
    :parameter (append-params parameters params)}
    (seq properties) (assoc :property (append-properties properties))
    (nil? count) (assoc :contains concepts)
    (pos-int? count) (assoc :contains (into [] (take count) concepts))))

(defn- expand-compose*
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

(defn- expand-compose [context value-set]
  (-> (expand-compose* context value-set)
      (ac/exceptionally
       #(update % ::anom/message (partial str (expand-value-set-msg value-set))))))

(def ^:private appended-param-names
  "Names of the parameters `append-params` appends."
  #{"count" "includeDesignations" "activeOnly" "excludeNested" "filter"})

(defn- recorded-params
  "Returns a map of parameter name to the values recorded in `expansion`."
  [{parameters :parameter}]
  (reduce
   (fn [ret {:keys [name value]}]
     (update ret (:value name) conj-vec value))
   {}
   parameters))

(defn- recorded-value [recorded name]
  (:value (first (recorded name))))

(defn- recorded-properties [recorded {properties :property}]
  (into #{} (map :value) (into (recorded "property") (map :code) properties)))

(defn- concept-seq
  "Returns a lazy seq of all concepts with code, including nested ones, in
  depth-first order."
  [concepts]
  (clojure.core/filter :code (mapcat #(tree-seq :contains :contains %) concepts)))

(defn- flatten-concepts
  "Returns the concepts with nested concepts appended after their parent.
  Concepts without code are removed."
  [concepts]
  (into [] (map #(dissoc % :contains)) (concept-seq concepts)))

(defn- nested? [concepts]
  (some :contains concepts))

(defn- designations? [concepts]
  (some #(or (contains? % :designation) (designations? (:contains %))) concepts))

(defn- remove-designations [concepts]
  (mapv
   (fn [{nested :contains :as concept}]
     (cond-> (dissoc concept :designation)
       nested (update :contains remove-designations)))
   concepts))

(defn- remove-inactive [concepts]
  (into [] (remove (comp true? :value :inactive)) concepts))

(defn- used-version
  "Returns the version of the code system with `system` used in the existing
  expansion."
  [recorded concepts system]
  (or (vs-u/find-version
       {:system-versions (into (recorded "version") (recorded "used-codesystem"))}
       system)
      (some #(when (= system (:value (:system %))) (:value (:version %)))
            (concept-seq concepts))))

(defn- system-versions-consistent? [params recorded concepts]
  (every?
   (fn [system]
     (if-let [version (vs-u/find-version params system)]
       (= version (used-version recorded concepts system))
       true))
   (into #{} (map (comp :value :system)) (concept-seq concepts))))

(defn- offset [recorded existing]
  (or (:value (:offset existing)) (recorded-value recorded "offset") 0))

(defn- incomplete? [recorded {:keys [total]} offset concepts]
  (or (some? (recorded "count"))
      (pos? offset)
      (when-let [total (:value total)]
        (< (count (concept-seq concepts)) total))))

(defn- existing-expansion-info
  "Returns a map describing how `params` can be applied to `existing`, the
  expansion already present in a value set."
  [{:keys [active-only]} {concepts :contains :as existing}]
  (let [recorded (recorded-params existing)
        offset (offset recorded existing)]
    {:recorded recorded
     :offset offset
     :incomplete (incomplete? recorded existing offset concepts)
     :active-only (and active-only (not (true? (recorded-value recorded "activeOnly"))))}))

(defn- inconsistent-param
  "Returns the name of the first parameter in `params` that is inconsistent
  with the existing expansion or nil if all parameters are consistent."
  [{:keys [filter count active-only include-designations display-language
           properties] :as params}
   {concepts :contains :as existing}
   {:keys [recorded offset incomplete]
    apply-active-only :active-only}]
  (cond
    (not= filter (recorded-value recorded "filter"))
    "filter"

    (if (true? (recorded-value recorded "activeOnly"))
      (not active-only)
      (and active-only (not-any? #(contains? % :inactive) (concept-seq concepts))))
    "activeOnly"

    (and include-designations
         (not (true? (recorded-value recorded "includeDesignations"))))
    "includeDesignations"

    (not= display-language (recorded-value recorded "displayLanguage"))
    "displayLanguage"

    (not= (set properties) (recorded-properties recorded existing))
    "property"

    (not (system-versions-consistent? params recorded concepts))
    "system-version"

    (and incomplete
         (or apply-active-only (pos? offset) (nil? count)
             (not= count (recorded-value recorded "count"))))
    "count"))

(defn- inconsistent-anom [value-set name]
  (ba/conflict
   (str
    (expand-value-set-msg value-set)
    (format "The existing expansion is inconsistent with the parameter `%s` and there is no compose to expand instead." name))))

(defn- new-expansion
  "Returns a new expansion based on `existing` with `concepts`."
  [context {:keys [incomplete]} existing concepts]
  (cond->
   (merge
    (dissoc existing :contains)
    (expansion
     context
     (into [] (remove (comp appended-param-names :value :name)) (:parameter existing))
     concepts))
    incomplete (-> (dissoc :total) (merge (select-keys existing [:total])))))

(defn- apply-params
  "Applies the params of `context` to the existing expansion of `value-set`."
  [{{:keys [count include-designations exclude-nested include-definition]}
    :params :as context}
   {existing :expansion :as value-set}
   {apply-active-only :active-only :as info}]
  (let [concepts (:contains existing)
        flatten? (and (nested? concepts)
                      (or apply-active-only count exclude-nested))
        strip-designations? (and (not include-designations)
                                 (designations? concepts))
        new-concepts (cond->> concepts
                       flatten? flatten-concepts
                       apply-active-only remove-inactive
                       strip-designations? remove-designations)]
    (cond-> value-set
      (or flatten? apply-active-only strip-designations?
          (and count (< count (clojure.core/count new-concepts))))
      (assoc :expansion (new-expansion context info existing new-concepts))
      (not include-definition) (dissoc :compose))))

(defn- use-existing-expansion
  "Uses the existing expansion of `value-set` if it's consistent with the
  params of `context`. Otherwise expands the compose or fails if there is no
  compose."
  [{:keys [params] :as context} {existing :expansion :keys [compose] :as value-set}]
  (let [info (existing-expansion-info params existing)]
    (if-let [name (inconsistent-param params existing info)]
      (if compose
        (expand-compose context value-set)
        (ac/completed-future (inconsistent-anom value-set name)))
      (ac/completed-future (apply-params context value-set info)))))

(defn- expand-value-set* [context {:keys [expansion] :as value-set}]
  (if expansion
    (use-existing-expansion context value-set)
    (expand-compose context value-set)))

(defn expand-value-set
  "Returns a CompletableFuture that will complete with the expanded `value-set`
  or will complete exceptionally with an anomaly in case of errors.

  An existing expansion is used if the parameters are consistent with it."
  [context value-set]
  (expand-value-set* context (dissoc value-set :id :meta)))
