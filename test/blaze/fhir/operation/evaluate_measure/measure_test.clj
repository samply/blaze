(ns blaze.fhir.operation.evaluate-measure.measure-test
  (:require
    [blaze.cql-translator :as cql-translator]
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.datomic.value :as value]
    [blaze.elm.compiler :as compiler]
    [blaze.fhir.operation.evaluate-measure.cql :as cql]
    [blaze.fhir.operation.evaluate-measure.measure :refer :all]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]])
  (:import
    [java.time Instant OffsetDateTime ZoneOffset]))


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


(defn stub-evaluate-expression [db now library expression-name result]
  (st/instrument
    [`cql/evaluate-expression]
    {:spec
     {`cql/evaluate-expression
      (s/fspec
        :args (s/cat :db #{db} :now #{now} :library #{library}
                     :expression-name #{expression-name})
        :ret #{result})}
     :stub
     #{`cql/evaluate-expression}}))


(def now (OffsetDateTime/ofInstant Instant/EPOCH (ZoneOffset/ofHours 0)))


(deftest evaluate-measure-test
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
        ::db now ::compiled-library "InInitialPopulation" 1)
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
