(ns blaze.terminology-service.local.value-set.validate-code
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.value-set.expand :as vs-expand]
   [cognitect.anomalies :as anom]))

(defn- infer-xf [{:keys [code]}]
  (comp
   (filter
    (fn [concept]
      (when (= code (type/value (:code concept)))
        concept)))
   (take 2)))

(defn- infer-non-unique-msg [{:keys [url]} {:keys [code]}]
  (if url
    (format "While inferring the system was requested, the provided code `%s` was not unique in the value set `%s`." code url)
    (format "While inferring the system was requested, the provided code `%s` was not unique in the provided value set." code)))

(defn- concept-pred [code system]
  (fn [concept]
    (when (and (= code (type/value (:code concept)))
               (= system (type/value (:system concept))))
      concept)))

(defn- extract-code-system [request]
  (if-let [code (:code request)]
    [code (:system request)]
    (let [{:keys [code system]} (:coding request)]
      [(type/value code) (type/value system)])))

(defn- not-found-msg [{:keys [url]} code system]
  (if url
    (format "The provided code `%s` of system `%s` was not found in the value set `%s`." code system url)
    (format "The provided code `%s` of system `%s` was not found in the provided value set." code system)))

(defn- find-concept
  [{{concepts :contains} :expansion :as value-set}
   {:keys [infer-system] :as request}]
  (if infer-system
    (let [concepts (into [] (infer-xf request) concepts)]
      (if (= 1 (count concepts))
        (first concepts)
        (ba/not-found (infer-non-unique-msg value-set request))))
    (let [[code system] (extract-code-system request)]
      (or (some (concept-pred code system) concepts)
          (ba/not-found (not-found-msg value-set code system))))))

(defn- parameter [name value]
  {:fhir/type :fhir.Parameters/parameter
   :name name
   :value value})

(defn build-response [value-set request]
  (if-ok [{:keys [code system]} (find-concept value-set request)]
    {:fhir/type :fhir/Parameters
     :parameter
     [(parameter "result" #fhir/boolean true)
      (parameter "code" code)
      (parameter "system" system)]}
    (fn [{::anom/keys [message]}]
      {:fhir/type :fhir/Parameters
       :parameter
       [(parameter "result" #fhir/boolean false)
        (parameter "message" (type/string message))]})))

(defn validate-code
  "Returns a CompletableFuture that will complete with a Parameters resource
  that contains the response of the validation request over `value-set`
  or will complete exceptionally with an anomaly in case of errors."
  {:arglists '([context value-set])}
  [{:keys [request] :as context} value-set]
  (-> (vs-expand/expand-value-set context value-set)
      (ac/then-apply #(build-response % request))))
