(ns blaze.fhir.operation.evaluate-measure.measure.util
  (:require
    [blaze.anomaly :as ba]
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

      (not= "text/cql" language)
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


(defn- population-tx-ops [{:keys [subject-type]} list-id result]
  [[:create
    {:fhir/type :fhir/List
     :id list-id
     :status #fhir/code"current"
     :mode #fhir/code"working"
     :entry
     (mapv
       (fn [subject-id]
         {:fhir/type :fhir.List/entry
          :item
          (type/map->Reference {:reference (str subject-type "/" subject-id)})})
       result)}]])


(defn population [{:keys [luids] :as context} fhir-type code result]
  (case (:report-type context)
    "population"
    {:result
     (cond->
       {:fhir/type fhir-type
        :count (int result)}
       code
       (assoc :code code))
     :luids luids}
    "subject"
    {:result
     (cond->
       {:fhir/type fhir-type
        :count (if result (int 1) (int 0))}
       code
       (assoc :code code))
     :luids luids}
    "subject-list"
    (let [list-id (first luids)]
      {:result
       (cond->
         {:fhir/type fhir-type
          :count (count result)
          :subjectResults
          (type/map->Reference {:reference (str "List/" list-id)})}
         code
         (assoc :code code))
       :luids (next luids)
       :tx-ops (population-tx-ops context list-id result)})))


(defn- merge-result*
  "Merges `result` into the return value of the reduction `ret`."
  {:arglists '([ret result])}
  [ret {:keys [result luids tx-ops]}]
  (-> (update ret :result conj result)
      (assoc :luids luids)
      (update :tx-ops into tx-ops)))


(defn merge-result
  "Merges `result` into the return value of the reduction `ret`.

  Returns a reduced value if `result` is an anomaly."
  [ret result]
  (-> result
      (ba/map (partial merge-result* ret))
      (ba/exceptionally reduced)))
