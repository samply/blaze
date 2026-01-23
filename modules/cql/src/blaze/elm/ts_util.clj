(ns blaze.elm.ts-util
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.elm.code :as code]
   [blaze.fhir.spec.type :as type]))

(def ^:private result-pred
  #(when (= "result" (:value (:name %))) %))

(defn extract-result [response msg-fn]
  (try
    (:value (:value (some result-pred (:parameter @response))))
    (catch Exception e
      (throw-anom (ba/fault (msg-fn (ex-message (ex-cause e))))))))

(defn- to-code [{:keys [system version code]}]
  (code/code (:value system) (:value version) (:value code)))

(defn extract-codes [response msg-fn]
  (try
    (mapv to-code (:contains (:expansion @response)))
    (catch Exception e
      (throw-anom (ba/fault (msg-fn (ex-message (ex-cause e))))))))

(defn code-param [code]
  {:fhir/type :fhir.Parameters/parameter
   :name #fhir/string "code"
   :value (type/code code)})

(defn- to-coding [{:keys [system code]}]
  (type/coding
   (cond-> {}
     system (assoc :system (type/uri system))
     code (assoc :code (type/code code)))))

(defn- to-codeable-concept [{:keys [codes]}]
  (type/codeable-concept {:coding (mapv to-coding codes)}))

(defn codeable-concept-param [concept]
  {:fhir/type :fhir.Parameters/parameter
   :name #fhir/string "codeableConcept"
   :value (to-codeable-concept concept)})
