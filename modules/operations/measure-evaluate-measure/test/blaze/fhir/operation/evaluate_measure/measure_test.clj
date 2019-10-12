(ns blaze.fhir.operation.evaluate-measure.measure-test
  (:require
    [blaze.bundle :as bundle]
    [blaze.cql-translator :as cql-translator]
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.datomic.value :as value]
    [blaze.elm.compiler :as compiler]
    [blaze.fhir.operation.evaluate-measure.cql :as cql]
    [blaze.fhir.operation.evaluate-measure.measure :refer :all]
    [blaze.terminology-service.extern :as ts]
    [cheshire.core :as json]
    [cheshire.parse :refer [*use-bigdecimals?*]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant OffsetDateTime ZoneOffset Year]
    [java.util Base64]))


(defn fixture [f]
  (st/instrument)
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(use-fixtures :each fixture)


(defn stub-cql-translate [code library]
  (st/instrument
    [`cql-translator/translate]
    {:spec
     {`cql-translator/translate
      (s/fspec
        :args (s/cat :cql #{code} :opts (s/* some?))
        :ret #{library})}
     :stub
     #{`cql-translator/translate}}))


(defn stub-compile-library [db library compiled-library]
  (st/instrument
    [`compiler/compile-library]
    {:spec
     {`compiler/compile-library
      (s/fspec
        :args (s/cat :db #{db} :library #{library} :opts #{{}})
        :ret #{compiled-library})}
     :stub
     #{`compiler/compile-library}}))


(defn stub-evaluate-expression [db now library subject expression-name result]
  (st/instrument
    [`cql/evaluate-expression]
    {:spec
     {`cql/evaluate-expression
      (s/fspec
        :args (s/cat :db #{db} :now #{now} :library #{library}
                     :subject #{subject}
                     :expression-name #{expression-name})
        :ret #{result})}
     :stub
     #{`cql/evaluate-expression}}))


(def now (OffsetDateTime/ofInstant Instant/EPOCH (ZoneOffset/ofHours 0)))


(deftest evaluate-measure-test
  (st/instrument
    [`evaluate-measure]
    {:spec
     {`evaluate-measure
      (s/fspec
        :args (s/cat :now #{now} :db #{::db}
                     :period (s/tuple #{::start} #{::end})
                     :measure some?))}})

  (testing "Missing Primary Library"
    (given (evaluate-measure now ::db [::start ::end] {})
      ::anom/category := ::anom/unsupported
      ::anom/message := "Missing primary library. Currently only CQL expressions together with one primary library are supported."
      :fhir/issue := "not-supported"))


  (testing "Success"
    (let [population-code
          {:CodeableConcept/coding
           [{:Coding/code
             {:code/system "http://terminology.hl7.org/CodeSystem/measure-population"
              :code/code "initial-population"}}]}
          population
          {:Measure.group.population/code population-code
           :Measure.group.population/criteria
           {:Expression/language {:code/code "text/cql"}
            :Expression/expression "InInitialPopulation"}}
          group
          {:Measure.group/population [population]}
          measure
          {:Measure/id "0"
           :Measure/url ::measure-url
           :Measure/scoring
           {:CodeableConcept/coding
            [{:Coding/code
              {:code/system "http://terminology.hl7.org/CodeSystem/measure-scoring"
               :code/code "cohort"}}]}
           :Measure/library ["http://foo"]
           :Measure/group [group]}
          library-content
          {:Attachment/contentType "text/cql"
           :Attachment/data (value/write (.getBytes "library-data" "utf-8"))}
          library
          {:Library/url "http://foo"
           :Library/content [library-content]}]
      (datomic-test-util/stub-resource-by ::db #{:Library/url} #{"http://foo"} #{library})
      (stub-cql-translate "library-data" ::elm-library)
      (stub-compile-library ::db ::elm-library ::compiled-library)
      (stub-evaluate-expression
        ::db now ::compiled-library "Patient" "InInitialPopulation" 1)
      (datomic-test-util/stub-pull-non-primitive
        ::db :CodeableConcept population-code ::population-code)

      (let [measure-report (evaluate-measure now ::db [::start ::end] measure)]

        (is (= "MeasureReport" (:resourceType measure-report)))

        (is (= "complete" (:status measure-report)))

        (is (= "summary" (:type measure-report)))

        (is (= ::measure-url (:measure measure-report)))

        (is (= "1970-01-01T00:00:00Z" (:date measure-report)))

        (is (= ::start (:start (:period measure-report))))

        (is (= ::end (:end (:period measure-report))))

        (is (= ::population-code (-> measure-report :group first :population first :code)))

        (is (= 1 (-> measure-report :group first :population first :count)))))))


(defn stub-evaluate-measure [now db period-start period-end measure measure-report]
  (st/instrument
    [`evaluate-measure]
    {:spec
     {`evaluate-measure
      (s/fspec
        :args (s/cat :now #{now} :db #{db}
                     :period (s/tuple #{period-start} #{period-end})
                     :measure #{measure})
        :ret #{measure-report})}
     :stub
     #{`evaluate-measure}}))


(defonce db (d/db (st/with-instrument-disabled (datomic-test-util/connect))))


(def term-service
  (ts/term-service "http://tx.fhir.org/r4" {} nil nil))


(defn- db-with [{:strs [entry]}]
  (let [entries @(bundle/annotate-codes term-service db entry)
        {db :db-after} (d/with db (bundle/code-tx-data db entries))]
    (:db-after (d/with db (bundle/tx-data db entries)))))


(defn- slurp-resource [name]
  (let [cl (.getContextClassLoader (Thread/currentThread))
        name (str "blaze/fhir/operation/evaluate_measure/" name)
        res (.getResource cl name)]
    (slurp res)))


(defn- b64-encode [s]
  (.encodeToString (Base64/getEncoder) (.getBytes s)))


(defn- library [query]
  {"resource"
   {"resourceType" "Library"
    "id" "0"
    "url" "0"
    "content"
    [{"contentType" "text/cql"
      "data" (b64-encode query)}]}
   "request"
   {"method" "PUT"
    "url" "Library/0"}})


(defn- read-data [name]
  (let [raw (slurp-resource (str name "-data.json"))
        bundle (binding [*use-bigdecimals?* true] (json/parse-string raw))
        library (library (slurp-resource (str name "-query.cql")))]
    (update bundle "entry" conj library)))


(defn- evaluate [name]
  (cql/clear-resource-cache!)
  (let [db (db-with (read-data name))
        period [(Year/of 2000) (Year/of 2020)]
        measure-report (evaluate-measure now db period (d/entity db [:Measure/id "0"]))]
    (if (::anom/category measure-report)
      measure-report
      (-> measure-report :group first :population first :count))))


(deftest integration-test
  (are [name count] (= count (evaluate name))
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
    "q17" 2))

(comment
  (evaluate "q16")
  (clojure.repl/pst)
  )
