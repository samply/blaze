(ns blaze.fhir.operation.evaluate-measure.measure.util
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba]
   [blaze.coll.core :as coll]
   [blaze.fhir.spec.type :as type]
   [blaze.luid :as luid]
   [blaze.util :refer [str]]))

(defn expression-name [population-path-fn criteria]
  (let [language (-> criteria :language type/value)
        expression (-> criteria :expression type/value)]
    (cond
      (nil? criteria)
      (ba/incorrect
       "Missing criteria."
       :fhir/issue "required"
       :fhir.issue/expression (population-path-fn))

      (not (#{"text/cql" "text/cql-identifier"} language))
      (ba/unsupported
       (format "Unsupported language `%s`." language)
       :fhir/issue "not-supported"
       :fhir.issue/expression (str (population-path-fn) ".criteria.language"))

      (nil? expression)
      (ba/incorrect
       "Missing expression."
       :fhir/issue "required"
       :fhir.issue/expression (str (population-path-fn) ".criteria"))

      :else
      expression)))

(defn list-reference [list-id]
  (type/reference {:reference (type/string (str "List/" list-id))}))

(defn- resource-reference [{:keys [id] :as resource}]
  (type/reference {:reference (type/string (str (name (type/type resource)) "/" id))}))

(defn population-tx-ops [list-id handles]
  [[:create
    {:fhir/type :fhir/List
     :id list-id
     :status #fhir/code "current"
     :mode #fhir/code "working"
     :entry
     (mapv
      (fn [{:keys [population-handle]}]
        {:fhir/type :fhir.List/entry
         :item (resource-reference population-handle)})
      handles)}]])

(defn- merge-result*
  "Merges `result` into the return value of the reduction `ret`."
  {:arglists '([ret result])}
  [ret {:keys [result tx-ops] ::luid/keys [generator]}]
  (cond-> (update ret :result conj result)
    generator
    (assoc ::luid/generator generator)
    (seq tx-ops)
    (update :tx-ops into tx-ops)))

(defn merge-result
  "Merges `result` into the return value of the reduction `ret`.

  Returns a reduced value if `result` is an anomaly."
  [ret result]
  (-> result
      (ba/map (partial merge-result* ret))
      (ba/exceptionally reduced)))

(defn- expression-name-of-expression [{:keys [language expression]}]
  (when (#{"text/cql" "text/cql-identifier"} (type/value language))
    (type/value expression)))

(defn- expression-name-of-population [{:keys [criteria]}]
  (expression-name-of-expression criteria))

(defn- expression-names-of-stratifier [{:keys [criteria component]}]
  (if criteria
    (some-> (expression-name-of-expression criteria) vector)
    (coll/eduction (keep expression-name-of-population) component)))

(defn- expression-names-of-group [{:keys [population stratifier]}]
  (-> (into [] (keep expression-name-of-population) population)
      (into (mapcat expression-names-of-stratifier) stratifier)))

(defn expression-names
  "Returns a set of alle CQL expression names found in `measure`."
  {:arglists '([measure])}
  [{:keys [group]}]
  (into #{} (mapcat expression-names-of-group) group))
