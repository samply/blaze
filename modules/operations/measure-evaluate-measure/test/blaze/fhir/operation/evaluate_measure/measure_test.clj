(ns blaze.fhir.operation.evaluate-measure.measure-test
  (:require
    [blaze.bundle :as bundle]
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.fhir.operation.evaluate-measure.measure :refer [evaluate-measure]]
    [cheshire.core :as json]
    [cheshire.parse :refer [*use-bigdecimals?*]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]
    [clojure.java.io :as io])
  (:import
    [java.time Instant OffsetDateTime ZoneOffset Year]
    [java.util Base64]))


(defn fixture [f]
  (st/instrument)
  (log/with-merged-config {:level :debug} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(def now (OffsetDateTime/ofInstant Instant/EPOCH (ZoneOffset/ofHours 0)))


(defn- node-with [{:keys [entry]}]
  (mem-node-with [(bundle/tx-ops entry)]))


(defn- slurp-resource [name]
  (slurp (io/resource (str "blaze/fhir/operation/evaluate_measure/" name))))


(defn- b64-encode [s]
  (.encodeToString (Base64/getEncoder) (.getBytes s)))


(defn- library [query]
  {:resource
   {:resourceType "Library"
    :id "0"
    :url "0"
    :content
    [{:contentType "text/cql"
      :data (b64-encode query)}]}
   :request
   {:method "PUT"
    :url "Library/0"}})


(defn- read-data [name]
  (let [raw (slurp-resource (str name "-data.json"))
        bundle (binding [*use-bigdecimals?* true] (json/parse-string raw keyword))
        library (library (slurp-resource (str name "-query.cql")))]
    (update bundle :entry conj library)))


(defn- evaluate [name]
  (let [node (node-with (read-data name))
        db (d/db node)
        period [(Year/of 2000) (Year/of 2020)]]
    (evaluate-measure now node db ::router period (d/resource db "Measure" "0"))))


(defn- first-population-count [measure-report]
  (if (::anom/category measure-report)
    (prn measure-report)
    (-> measure-report
        :group first
        :population first
        :count)))


(defn- first-stratifier-stratums [measure-report]
  (if (::anom/category measure-report)
    (prn measure-report)
    (-> measure-report
        :group first
        :stratifier first
        :stratum)))


(deftest integration-test
  (are [name count] (= count (first-population-count (evaluate name)))
    "q1" 1
    "q2" 1
    "q3" 1
    "q4" 1
    "q5" 1
    "q6" 1
    "q7" 1
    "q8" 1
    "q9" 1
    "q10" 1
    "q11" 1
    "q12" 1
    "q13" 1
    "q14" 1
    "q15" 1
    "q16" 1
    "q17" 2
    "q18" 1
    "q24" 1
    "q28-relationship-procedure-condition" 1)

  (given (first-stratifier-stratums (evaluate "q19-stratifier-ageclass"))
    [0 :value :text] := "10"
    [0 :population 0 :count] := 1
    [1 :value :text] := "70"
    [1 :population 0 :count] := 2)

  (given (first-stratifier-stratums (evaluate "q20-stratifier-city"))
    [0 :value :text] := "Jena"
    [0 :population 0 :count] := 3
    [1 :value :text] := "Leipzig"
    [1 :population 0 :count] := 1)

  (given (first-stratifier-stratums (evaluate "q21-stratifier-city-of-only-women"))
    [0 :value :text] := "Jena"
    [0 :population 0 :count] := 2)

  (is (= ::anom/incorrect (::anom/category (evaluate "q22-stratifier-multiple-cities-fail"))))

  (given
    (->> (first-stratifier-stratums (evaluate "q23-stratifier-ageclass-and-gender"))
         (map (fn [c] (update c :component (fn [x] (sort-by #(get-in % [:code :text]) x)))))
         (sort-by (fn [{:keys [component]}] (mapv #(get-in % [:value :text]) component))))
    [0 :component 0 :value :text] := "10"
    [0 :component 1 :value :text] := "male"
    [0 :population 0 :count] := 1
    [1 :component 0 :value :text] := "70"
    [1 :component 1 :value :text] := "female"
    [1 :population 0 :count] := 2
    [2 :component 0 :value :text] := "70"
    [2 :component 1 :value :text] := "male"
    [2 :population 0 :count] := 1)

  (given (first-stratifier-stratums (evaluate "q25-stratifier-collection"))
    [0 :value :text] := "Organization/collection-0"
    [0 :population 0 :count] := 1
    [1 :value :text] := "Organization/collection-1"
    [1 :population 0 :count] := 1)

  (given (first-stratifier-stratums (evaluate "q26-stratifier-bmi"))
    [0 :value :text] := "37"
    [0 :population 0 :count] := 1
    [1 :value :text] := "null"
    [1 :population 0 :count] := 2)

  (given (first-stratifier-stratums (evaluate "q27-stratifier-calculated-bmi"))
    [0 :value :text] := "26.8"
    [0 :population 0 :count] := 1
    [1 :value :text] := "null"
    [1 :population 0 :count] := 2)

  (given (first-stratifier-stratums (evaluate "q29-stratifier-sample-material-type"))
    [0 :value :text] := "liquid"
    [0 :population 0 :count] := 1
    [1 :value :text] := "tissue"
    [1 :population 0 :count] := 1))

(comment
  (evaluate "q29-stratifier-sample-material-type")
  (clojure.repl/pst)
  )
