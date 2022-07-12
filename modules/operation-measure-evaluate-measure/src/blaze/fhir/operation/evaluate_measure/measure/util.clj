(ns blaze.fhir.operation.evaluate-measure.measure.util
  (:require
    [blaze.anomaly :as ba]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.fhir.spec.type :as type]))


(defn expression [population-path-fn criteria]
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


(defn- list-reference [list-id]
  (type/map->Reference {:reference (str "List/" list-id)}))


(defn- resource-handle-reference [resource-handle]
  (type/map->Reference {:reference (rh/reference resource-handle)}))


(defn- population-tx-ops [list-id handles]
  [[:create
    {:fhir/type :fhir/List
     :id list-id
     :status #fhir/code"current"
     :mode #fhir/code"working"
     :entry
     (mapv
       (fn [{:keys [population-handle]}]
         {:fhir/type :fhir.List/entry
          :item (resource-handle-reference population-handle)})
       handles)}]])


(defn population [{:keys [luids] :as context} fhir-type code handles]
  (case (:report-type context)
    ("population" "subject")
    {:result
     (cond->
       {:fhir/type fhir-type
        :count (count handles)}
       code
       (assoc :code code))
     :luids luids}
    "subject-list"
    (let [list-id (first luids)]
      {:result
       (cond->
         {:fhir/type fhir-type
          :count (count handles)
          :subjectResults (list-reference list-id)}
         code
         (assoc :code code))
       :luids (next luids)
       :tx-ops (population-tx-ops list-id handles)})))


(defn- merge-result*
  "Merges `result` into the return value of the reduction `ret`."
  {:arglists '([ret result])}
  [ret {:keys [result handles luids tx-ops]}]
  (cond-> (update ret :result conj result)
    (seq handles)
    (update :handles conj handles)
    luids
    (assoc :luids luids)
    (seq tx-ops)
    (update :tx-ops into tx-ops)))


(defn merge-result
  "Merges `result` into the return value of the reduction `ret`.

  Returns a reduced value if `result` is an anomaly."
  [ret result]
  (-> result
      (ba/map (partial merge-result* ret))
      (ba/exceptionally reduced)))
