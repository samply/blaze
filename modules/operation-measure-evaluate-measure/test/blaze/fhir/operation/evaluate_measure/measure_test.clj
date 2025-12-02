(ns blaze.fhir.operation.evaluate-measure.measure-test
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.elm.expression :as-alias expr]
   [blaze.elm.expression.cache :as ec]
   [blaze.elm.expression.cache.bloom-filter :as-alias bloom-filter]
   [blaze.fhir.operation.evaluate-measure.measure :as measure]
   [blaze.fhir.operation.evaluate-measure.measure-spec]
   [blaze.fhir.operation.evaluate-measure.measure.group-spec]
   [blaze.fhir.operation.evaluate-measure.measure.population-spec]
   [blaze.fhir.operation.evaluate-measure.measure.stratifier-spec]
   [blaze.fhir.operation.evaluate-measure.measure.util-spec]
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [given-failed-future]]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service-spec]
   [blaze.terminology-service.local :as ts-local]
   [blaze.test-util :as tu]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit]
   [taoensso.timbre :as log])
  (:import
   [java.nio.charset StandardCharsets]
   [java.util Base64]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def router
  (reitit/router
   [["/Patient/{id}" {:name :Patient/instance}]
    ["/MeasureReport/{id}/_history/{vid}" {:name :MeasureReport/versioned-instance}]]
   {:syntax :bracket}))

(defmulti entry-tx-op (fn [{{:keys [method]} :request}] (type/value method)))

(defmethod entry-tx-op "PUT"
  [{:keys [resource]}]
  [:put resource])

(defn- tx-ops [entries]
  (mapv entry-tx-op entries))

(defn- slurp-resource [name]
  (slurp (io/resource (str "blaze/fhir/operation/evaluate_measure/" name))))

(defn- b64-encode [s]
  (.encodeToString (Base64/getEncoder) (.getBytes ^String s)))

(defn- library-entry [query]
  {:resource
   {:fhir/type :fhir/Library
    :id "1"
    :url #fhir/uri "0"
    :content
    [(type/attachment
      {:contentType #fhir/code "text/cql"
       :data (type/base64Binary (b64-encode query))})]}
   :request
   {:method #fhir/code "PUT"
    :url #fhir/uri "Library/1"}})

(def ^:private parsing-context
  (ig/init-key
   :blaze.fhir/parsing-context
   {:structure-definition-repo structure-definition-repo}))

(defn- read-data [name]
  (let [raw (slurp-resource (str name ".json"))
        bundle (fhir-spec/parse-json parsing-context "Bundle" raw)
        library (library-entry (slurp-resource (str name ".cql")))]
    (update bundle :entry conj library)))

(def ^:private config
  (assoc mem-node-config
         ::expr/cache
         {:node (ig/ref :blaze.db/node)
          :executor (ig/ref :blaze.test/executor)}
         ::ts/local
         {:node (ig/ref :blaze.db/node)
          :clock (ig/ref :blaze.test/fixed-clock)
          :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
          :graph-cache (ig/ref ::ts-local/graph-cache)}
         :blaze.test/fixed-rng-fn {}
         :blaze.test/executor {}
         ::ts-local/graph-cache {}))

(defn- evaluate
  ([name]
   (evaluate name "population"))
  ([name report-type]
   (evaluate name report-type false))
  ([name report-type twice-for-caching]
   (with-system-data
     [{:blaze.db/keys [node]
       ::expr/keys [cache]
       :blaze.test/keys [fixed-clock fixed-rng-fn executor]
       ::ts/keys [local]} config]
     [(tx-ops (:entry (read-data name)))]

     (let [db (d/db node)
           context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                    ::expr/cache cache :blaze/base-url "" ::reitit/router router
                    :terminology-service local :executor executor}
           measure @(d/pull node (d/resource-handle db "Measure" "0"))
           period [#system/date "2000" #system/date "2020"]
           params {:period period :report-type report-type}
           eval #(try
                   @(measure/evaluate-measure context measure params)
                   (catch Exception e
                     (ex-data (ex-cause e))))]
       (if twice-for-caching
         (when-ok [_ (eval)]
           (Thread/sleep 200)
           (eval))
         (eval))))))

(defn- first-population [result]
  (if (::anom/category result)
    (prn result)
    (-> result
        :resource
        :group first
        :population first)))

(defn- first-stratifier [result]
  (if (::anom/category result)
    (prn result)
    (-> result
        :resource
        :group first
        :stratifier first)))

(defn- first-stratifier-strata [result]
  (:stratum (first-stratifier result)))

(defn- population-concept [code]
  (type/codeable-concept
   {:coding
    [(type/coding
      {:system #fhir/uri "http://terminology.hl7.org/CodeSystem/measure-population"
       :code (type/code code)})]}))

(defn- cql-expression [expr]
  {:fhir/type :fhir/Expression
   :language #fhir/code "text/cql-identifier"
   :expression (type/string expr)})

(defn encode-base64 [^String s]
  (-> (Base64/getEncoder)
      (.encode (.getBytes s StandardCharsets/UTF_8))
      (String. StandardCharsets/UTF_8)))

(defn library-content [content]
  (type/attachment {:contentType #fhir/code "text/cql"
                    :data (type/base64Binary (encode-base64 content))}))

(defn library [in-initial-population]
  (format "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'

  context Patient

  define InInitialPopulation:
    %s" in-initial-population))

(defn library-gender [in-initial-population]
  (format "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'

  context Patient

  define InInitialPopulation:
    %s

  define Gender:
    Patient.gender" in-initial-population))

(def library-encounter
  "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'

  context Patient

  define InInitialPopulation:
    [Encounter]")

(def library-exists-condition
  "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'

  context Patient

  define InInitialPopulation:
    exists [Condition]")

(def library-medication
  "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'

  codesystem atc: 'http://fhir.de/CodeSystem/dimdi/atc'

  context Unfiltered

  define \"Temozolomid Refs\":
    [Medication: Code 'L01AX03' from atc] M return 'Medication/' + M.id

  context Patient

  define InInitialPopulation:
    Patient.gender = 'female' and
    exists from [MedicationStatement] M
      where M.medication.reference in \"Temozolomid Refs\"")

(def library-patient-encounter
  "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'

  context Patient

  define InInitialPopulation:
    true

  define AllEncounters:
    [Encounter]

  define Gender:
    Patient.gender")

(deftest evaluate-measure-test
  (testing "Encounter population basis"
    (with-system-data
      [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn executor]
        ::ts/keys [local]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Encounter :id "0-0" :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put {:fhir/type :fhir/Patient :id "1"}]
        [:put {:fhir/type :fhir/Encounter :id "1-0" :subject #fhir/Reference{:reference #fhir/string "Patient/1"}}]
        [:put {:fhir/type :fhir/Encounter :id "1-1" :subject #fhir/Reference{:reference #fhir/string "Patient/1"}}]
        [:put {:fhir/type :fhir/Patient :id "2"}]]
       [[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri "0"
               :content [(library-content library-encounter)]}]]]

      (let [db (d/db node)
            context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                     :executor executor :terminology-service local
                     :blaze/base-url "" ::reitit/router router}
            measure {:fhir/type :fhir/Measure :id "0"
                     :library [#fhir/canonical "0"]
                     :group
                     [{:fhir/type :fhir.Measure/group
                       :extension
                       [#fhir/Extension
                         {:url "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-populationBasis"
                          :value #fhir/code "Encounter"}]
                       :population
                       [{:fhir/type :fhir.Measure.group/population
                         :code (population-concept "initial-population")
                         :criteria (cql-expression "InInitialPopulation")}]}]}]

        (testing "population report"
          (let [params {:period [#system/date "2000" #system/date "2100"]
                        :report-type "population"}]
            (given (:resource @(measure/evaluate-measure context measure params))
              :fhir/type := :fhir/MeasureReport
              [:group 0 :population 0 :code :coding 0 :code] := #fhir/code "initial-population"
              [:group 0 :population 0 :count] := #fhir/integer 3)))

        (testing "subject-list report"
          (let [params {:period [#system/date "2000" #system/date "2100"]
                        :report-type "subject-list"}
                {:keys [resource tx-ops]} @(measure/evaluate-measure context measure params)]

            (given resource
              :fhir/type := :fhir/MeasureReport
              [:group 0 :population 0 :code :coding 0 :code] := #fhir/code "initial-population"
              [:group 0 :population 0 :count] := #fhir/integer 3
              [:group 0 :population 0 :subjectResults :reference] := #fhir/string "List/AAAAAAAAAAAAAAAA")

            (given tx-ops
              [0 0] := :create
              [0 1 :id] := "AAAAAAAAAAAAAAAA"
              [0 1 :entry 0 :item :reference] := #fhir/string "Encounter/0-0"
              [0 1 :entry 1 :item :reference] := #fhir/string "Encounter/1-0"
              [0 1 :entry 2 :item :reference] := #fhir/string "Encounter/1-1"))))))

  (testing "two groups"
    (with-system-data
      [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn executor]
        ::ts/keys [local]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "male"}]
        [:put {:fhir/type :fhir/Patient :id "1" :gender #fhir/code "female"}]
        [:put {:fhir/type :fhir/Encounter :id "1-0" :subject #fhir/Reference{:reference #fhir/string "Patient/1"}}]
        [:put {:fhir/type :fhir/Patient :id "2" :gender #fhir/code "female"}]
        [:put {:fhir/type :fhir/Encounter :id "2-0" :subject #fhir/Reference{:reference #fhir/string "Patient/2"}}]
        [:put {:fhir/type :fhir/Encounter :id "2-1" :subject #fhir/Reference{:reference #fhir/string "Patient/2"}}]
        [:put {:fhir/type :fhir/Patient :id "3" :gender #fhir/code "female"}]
        [:put {:fhir/type :fhir/Encounter :id "3-0" :subject #fhir/Reference{:reference #fhir/string "Patient/3"}}]
        [:put {:fhir/type :fhir/Encounter :id "3-1" :subject #fhir/Reference{:reference #fhir/string "Patient/3"}}]
        [:put {:fhir/type :fhir/Encounter :id "3-2" :subject #fhir/Reference{:reference #fhir/string "Patient/3"}}]]
       [[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri "0"
               :content [(library-content library-patient-encounter)]}]]]

      (let [db (d/db node)
            context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                     :executor executor :terminology-service local
                     :blaze/base-url "" ::reitit/router router}
            measure {:fhir/type :fhir/Measure :id "0"
                     :library [#fhir/canonical "0"]
                     :group
                     [{:fhir/type :fhir.Measure/group
                       :code #fhir/CodeableConcept{:text #fhir/string "group-1"}
                       :extension
                       [#fhir/Extension
                         {:url "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-populationBasis"
                          :value #fhir/code "boolean"}]
                       :population
                       [{:fhir/type :fhir.Measure.group/population
                         :code (population-concept "initial-population")
                         :criteria (cql-expression "InInitialPopulation")}]
                       :stratifier
                       [{:fhir/type :fhir.Measure.group/stratifier
                         :code #fhir/CodeableConcept{:text #fhir/string "gender"}
                         :criteria (cql-expression "Gender")}]}
                      {:fhir/type :fhir.Measure/group
                       :code #fhir/CodeableConcept{:text #fhir/string "group-2"}
                       :extension
                       [#fhir/Extension
                         {:url "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-populationBasis"
                          :value #fhir/code "Encounter"}]
                       :population
                       [{:fhir/type :fhir.Measure.group/population
                         :code (population-concept "initial-population")
                         :criteria (cql-expression "AllEncounters")}]}]}]

        (testing "population report"
          (let [params {:period [#system/date "2000" #system/date "2100"]
                        :report-type "population"}]
            (given (:resource @(measure/evaluate-measure context measure params))
              :fhir/type := :fhir/MeasureReport
              [:group count] := 2
              [:group 0 :code] := #fhir/CodeableConcept{:text #fhir/string "group-1"}
              [:group 0 :population 0 :code :coding 0 :code] := #fhir/code "initial-population"
              [:group 0 :population 0 :count] := #fhir/integer 4
              [:group 1 :code] := #fhir/CodeableConcept{:text #fhir/string "group-2"}
              [:group 1 :population 0 :code :coding 0 :code] := #fhir/code "initial-population"
              [:group 1 :population 0 :count] := #fhir/integer 6)))

        (testing "subject-list report"
          (let [params {:period [#system/date "2000" #system/date "2100"]
                        :report-type "subject-list"}
                {:keys [resource tx-ops]} @(measure/evaluate-measure context measure params)]

            (given resource
              :fhir/type := :fhir/MeasureReport
              [:group count] := 2
              [:group 0 :code] := #fhir/CodeableConcept{:text #fhir/string "group-1"}
              [:group 0 :population count] := 1
              [:group 0 :population 0 :code :coding 0 :code] := #fhir/code "initial-population"
              [:group 0 :population 0 :count] := #fhir/integer 4
              [:group 0 :population 0 :subjectResults :reference] := #fhir/string "List/AAAAAAAAAAAAAAAA"
              [:group 0 :stratifier count] := 1
              [:group 0 :stratifier 0 :stratum count] := 2
              [:group 0 :stratifier 0 :stratum 0 :value :text] := #fhir/string "male"
              [:group 0 :stratifier 0 :stratum 0 :population 0 :count] := #fhir/integer 1
              [:group 0 :stratifier 0 :stratum 0 :population 0 :subjectResults :reference] := #fhir/string "List/AAAAAAAAAAAAAAAB"
              [:group 0 :stratifier 0 :stratum 1 :value :text] := #fhir/string "female"
              [:group 0 :stratifier 0 :stratum 1 :population 0 :count] := #fhir/integer 3
              [:group 0 :stratifier 0 :stratum 1 :population 0 :subjectResults :reference] := #fhir/string "List/AAAAAAAAAAAAAAAC"
              [:group 1 :code] := #fhir/CodeableConcept{:text #fhir/string "group-2"}
              [:group 1 :population count] := 1
              [:group 1 :population 0 :code :coding 0 :code] := #fhir/code "initial-population"
              [:group 1 :population 0 :count] := #fhir/integer 6
              [:group 1 :population 0 :subjectResults :reference] := #fhir/string "List/AAAAAAAAAAAAAAAD")

            (given tx-ops
              count := 4
              [0 0] := :create
              [0 1 :id] := "AAAAAAAAAAAAAAAA"
              [0 1 :entry count] := 4
              [0 1 :entry 0 :item :reference] := #fhir/string "Patient/0"
              [0 1 :entry 1 :item :reference] := #fhir/string "Patient/1"
              [0 1 :entry 2 :item :reference] := #fhir/string "Patient/2"
              [0 1 :entry 3 :item :reference] := #fhir/string "Patient/3"
              [1 0] := :create
              [1 1 :id] := "AAAAAAAAAAAAAAAB"
              [1 1 :entry count] := 1
              [1 1 :entry 0 :item :reference] := #fhir/string "Patient/0"
              [2 0] := :create
              [2 1 :id] := "AAAAAAAAAAAAAAAC"
              [2 1 :entry count] := 3
              [2 1 :entry 0 :item :reference] := #fhir/string "Patient/1"
              [2 1 :entry 1 :item :reference] := #fhir/string "Patient/2"
              [2 1 :entry 2 :item :reference] := #fhir/string "Patient/3"
              [3 0] := :create
              [3 1 :id] := "AAAAAAAAAAAAAAAD"
              [3 1 :entry count] := 6
              [3 1 :entry 0 :item :reference] := #fhir/string "Encounter/1-0"
              [3 1 :entry 1 :item :reference] := #fhir/string "Encounter/2-0"
              [3 1 :entry 2 :item :reference] := #fhir/string "Encounter/2-1"
              [3 1 :entry 3 :item :reference] := #fhir/string "Encounter/3-0"
              [3 1 :entry 4 :item :reference] := #fhir/string "Encounter/3-1"
              [3 1 :entry 5 :item :reference] := #fhir/string "Encounter/3-2"))))))

  (testing "library with syntax error"
    (with-system-data
      [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn executor]
        ::ts/keys [local]} config]
      [[[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri "0"
               :content [(library-content "library Test
                                           define Error: (")]}]]]

      (let [db (d/db node)
            context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                     :blaze/base-url "" ::reitit/router router
                     :executor executor :terminology-service local}
            measure-id "measure-id-133021"
            measure {:fhir/type :fhir/Measure :id measure-id
                     :library [#fhir/canonical "0"]
                     :group
                     [{:fhir/type :fhir.Measure/group
                       :population
                       [{:fhir/type :fhir.Measure.group/population
                         :code (population-concept "initial-population")}]}]}
            params {:period [#system/date "2000" #system/date "2020"]
                    :report-type "population"}]

        (given-failed-future (measure/evaluate-measure context measure params)
          ::anom/category := ::anom/incorrect
          ::anom/message := "Syntax error at <EOF>"
          :measure-id := measure-id
          :fhir/issue := "value"
          :fhir.issue/expression := "Measure.library"))))

  (testing "missing criteria"
    (with-system-data
      [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn executor]
        ::ts/keys [local]} config]
      [[[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri "0"
               :content [(library-content (library-gender true))]}]]]

      (let [db (d/db node)
            context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                     :blaze/base-url "" ::reitit/router router
                     :executor executor :terminology-service local}
            measure-id "measure-id-133021"
            measure {:fhir/type :fhir/Measure :id measure-id
                     :library [#fhir/canonical "0"]
                     :group
                     [{:fhir/type :fhir.Measure/group
                       :population
                       [{:fhir/type :fhir.Measure.group/population
                         :code (population-concept "initial-population")}]}]}
            params {:period [#system/date "2000" #system/date "2020"]
                    :report-type "population"}]

        (given-failed-future (measure/evaluate-measure context measure params)
          ::anom/category := ::anom/incorrect
          ::anom/message := "Missing criteria."
          :measure-id := measure-id
          :fhir/issue := "required"
          :fhir.issue/expression := "Measure.group[0].population[0]"))))

  (testing "evaluation timeout"
    (with-system-data
      [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn executor]
        ::ts/keys [local]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri "0"
               :content [(library-content (library-gender true))]}]]]

      (let [db (d/db node)
            context {:clock fixed-clock :rng-fn fixed-rng-fn
                     :db db :timeout (time/seconds 0)
                     :executor executor :terminology-service local
                     :blaze/base-url "" ::reitit/router router}
            measure-id "measure-id-132321"
            measure {:fhir/type :fhir/Measure :id measure-id
                     :library [#fhir/canonical "0"]
                     :group
                     [{:fhir/type :fhir.Measure/group
                       :population
                       [{:fhir/type :fhir.Measure.group/population
                         :code (population-concept "initial-population")
                         :criteria (cql-expression "InInitialPopulation")}]}]}
            params {:period [#system/date "2000" #system/date "2020"]}]

        (doseq [report-type ["population" "subject-list"]
                :let [params (assoc params :report-type report-type)]]

          (given-failed-future (measure/evaluate-measure context measure params)
            ::anom/category := ::anom/interrupted
            ::anom/message := "Timeout of 0 millis eclipsed while evaluating."
            :measure-id := measure-id)))))

  (testing "cancellation"
    (with-system-data
      [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn executor]
        ::ts/keys [local]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri "0"
               :content [(library-content (library-gender true))]}]]]

      (doseq [cancelled? [(ba/interrupted "The evaluation was cancelled.") nil]]
        (let [db (d/db node)
              context {:clock fixed-clock :rng-fn fixed-rng-fn
                       :db db :blaze/cancelled? (constantly cancelled?)
                       :executor executor :terminology-service local
                       :blaze/base-url "" ::reitit/router router}
              measure-id "measure-id-132321"
              measure {:fhir/type :fhir/Measure :id measure-id
                       :library [#fhir/canonical "0"]
                       :group
                       [{:fhir/type :fhir.Measure/group
                         :population
                         [{:fhir/type :fhir.Measure.group/population
                           :code (population-concept "initial-population")
                           :criteria (cql-expression "InInitialPopulation")}]}]}
              params {:period [#system/date "2000" #system/date "2020"]}]

          (doseq [report-type ["population" "subject-list"]
                  :let [params (assoc params :report-type report-type)]]

            (if cancelled?
              (given-failed-future (measure/evaluate-measure context measure params)
                ::anom/category := ::anom/interrupted
                ::anom/message := "The evaluation was cancelled."
                :measure-id := measure-id)

              (given @(measure/evaluate-measure context measure params)
                [:resource :fhir/type] := :fhir/MeasureReport))))))

    (testing "Encounter population basis"
      (with-system-data
        [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn executor]
          ::ts/keys [local]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Encounter :id "0-0" :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
          [:put {:fhir/type :fhir/Patient :id "1"}]
          [:put {:fhir/type :fhir/Encounter :id "1-0" :subject #fhir/Reference{:reference #fhir/string "Patient/1"}}]
          [:put {:fhir/type :fhir/Encounter :id "1-1" :subject #fhir/Reference{:reference #fhir/string "Patient/1"}}]
          [:put {:fhir/type :fhir/Patient :id "2"}]]
         [[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri "0"
                 :content [(library-content library-encounter)]}]]]

        (let [db (d/db node)
              context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                       :blaze/cancelled?
                       (constantly (ba/interrupted "msg-114556"))
                       :executor executor :terminology-service local
                       :blaze/base-url "" ::reitit/router router}
              measure {:fhir/type :fhir/Measure :id "0"
                       :library [#fhir/canonical "0"]
                       :group
                       [{:fhir/type :fhir.Measure/group
                         :extension
                         [#fhir/Extension
                           {:url "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-populationBasis"
                            :value #fhir/code "Encounter"}]
                         :population
                         [{:fhir/type :fhir.Measure.group/population
                           :code (population-concept "initial-population")
                           :criteria (cql-expression "InInitialPopulation")}]}]}
              params {:period [#system/date "2000" #system/date "2020"]}]

          (doseq [report-type ["population" "subject-list"]
                  :let [params (assoc params :report-type report-type)]]

            (given-failed-future (measure/evaluate-measure context measure params)
              ::anom/category := ::anom/interrupted
              ::anom/message := "msg-114556"
              :measure-id := "0"))))))

  (testing "single subject"
    (doseq [subject-ref ["0" ["Patient" "0"]]
            [library count] [[(library true) 1] [(library false) 0]]]
      (with-system-data
        [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn executor]
          ::ts/keys [local]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri "0"
                 :content [(library-content library)]}]]]

        (let [db (d/db node)
              context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                       :executor executor :terminology-service local
                       :blaze/base-url "" ::reitit/router router}
              measure {:fhir/type :fhir/Measure :id "0"
                       :url #fhir/uri "measure-155437"
                       :library [#fhir/canonical "0"]
                       :group
                       [{:fhir/type :fhir.Measure/group
                         :population
                         [{:fhir/type :fhir.Measure.group/population
                           :code (population-concept "initial-population")
                           :criteria (cql-expression "InInitialPopulation")}]}]}
              params {:period [#system/date "2000" #system/date "2020"]
                      :report-type "subject"
                      :subject-ref subject-ref}]

          (given (:resource @(measure/evaluate-measure context measure params))
            :fhir/type := :fhir/MeasureReport
            :status := #fhir/code "complete"
            :type := #fhir/code "individual"
            :measure := #fhir/canonical "measure-155437"
            [:subject :reference] := #fhir/string "Patient/0"
            :date := #fhir/dateTime #system/date-time "1970-01-01T00:00Z"
            :period := #fhir/Period{:start #fhir/dateTime #system/date-time "2000"
                                    :end #fhir/dateTime #system/date-time "2020"}
            [:group 0 :population 0 :code :coding 0 :code] := #fhir/code "initial-population"
            [:group 0 :population 0 :count type/value] := count))))

    (testing "with stratifiers"
      (doseq [[library count] [[(library-gender true) 1] [(library-gender false) 0]]]
        (with-system-data
          [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn executor]
            ::ts/keys [local]} config]
          [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "male"}]
            [:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri "0"
                   :content [(library-content library)]}]]]

          (let [db (d/db node)
                context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                         :executor executor :terminology-service local
                         :blaze/base-url "" ::reitit/router router}
                measure {:fhir/type :fhir/Measure :id "0"
                         :url #fhir/uri "measure-155502"
                         :library [#fhir/canonical "0"]
                         :group
                         [{:fhir/type :fhir.Measure/group
                           :population
                           [{:fhir/type :fhir.Measure.group/population
                             :code (population-concept "initial-population")
                             :criteria (cql-expression "InInitialPopulation")}]
                           :stratifier
                           [{:fhir/type :fhir.Measure.group/stratifier
                             :code #fhir/CodeableConcept{:text #fhir/string "gender"}
                             :criteria (cql-expression "Gender")}]}]}
                params {:period [#system/date "2000" #system/date "2020"]
                        :report-type "subject"
                        :subject-ref "0"}]

            (given (:resource @(measure/evaluate-measure context measure params))
              :fhir/type := :fhir/MeasureReport
              :status := #fhir/code "complete"
              :type := #fhir/code "individual"
              :measure := #fhir/canonical "measure-155502"
              [:subject :reference] := #fhir/string "Patient/0"
              :date := #system/date-time "1970-01-01T00:00Z"
              :period := #fhir/Period{:start #fhir/dateTime "2000"
                                      :end #fhir/dateTime "2020"}
              [:group 0 :population 0 :code :coding 0 :code] := #fhir/code "initial-population"
              [:group 0 :population 0 :count type/value] := count
              [:group 0 :stratifier 0 :code 0 :text] := #fhir/string "gender"
              [:group 0 :stratifier 0 :stratum 0 :value :text type/value] := (when (= 1 count) "male")
              [:group 0 :stratifier 0 :stratum 0 :population 0 :count type/value] := (when (= 1 count) 1))))))

    (testing "invalid subject"
      (with-system-data
        [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn executor]
          ::ts/keys [local]} config]
        [[[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri "0"
                 :content [(library-content (library-gender true))]}]]]

        (let [db (d/db node)
              context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                       :blaze/base-url "" ::reitit/router router
                       :executor executor :terminology-service local}
              measure {:fhir/type :fhir/Measure :id "0"
                       :library [#fhir/canonical "0"]
                       :group
                       [{:fhir/type :fhir.Measure/group
                         :population
                         [{:fhir/type :fhir.Measure.group/population
                           :code (population-concept "initial-population")
                           :criteria (cql-expression "InInitialPopulation")}]}]}
              params {:period [#system/date "2000" #system/date "2020"]
                      :report-type "subject"
                      :subject-ref ["Observation" "0"]}]

          (given-failed-future (measure/evaluate-measure context measure params)
            ::anom/category := ::anom/incorrect
            ::anom/message := "Type mismatch between evaluation subject `Observation` and Measure subject `Patient`."))))

    (testing "missing subject"
      (with-system-data
        [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn executor]
          ::ts/keys [local]} config]
        [[[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri "0"
                 :content [(library-content (library-gender true))]}]]]

        (let [db (d/db node)
              context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                       :blaze/base-url "" ::reitit/router router
                       :executor executor :terminology-service local}
              measure {:fhir/type :fhir/Measure :id "0"
                       :library [#fhir/canonical "0"]
                       :group
                       [{:fhir/type :fhir.Measure/group
                         :population
                         [{:fhir/type :fhir.Measure.group/population
                           :code (population-concept "initial-population")
                           :criteria (cql-expression "InInitialPopulation")}]}]}
              params {:period [#system/date "2000" #system/date "2020"]
                      :report-type "subject"
                      :subject-ref "0"}]

          (given-failed-future (measure/evaluate-measure context measure params)
            ::anom/category := ::anom/incorrect
            ::anom/message := "Subject with type `Patient` and id `0` was not found."))))

    (testing "deleted subject"
      (with-system-data
        [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn executor]
          ::ts/keys [local]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri "0"
                 :content [(library-content (library-gender true))]}]]
         [[:delete "Patient" "0"]]]

        (let [db (d/db node)
              context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                       :blaze/base-url "" ::reitit/router router
                       :executor executor :terminology-service local}
              measure {:fhir/type :fhir/Measure :id "0"
                       :library [#fhir/canonical "0"]
                       :group
                       [{:fhir/type :fhir.Measure/group
                         :population
                         [{:fhir/type :fhir.Measure.group/population
                           :code (population-concept "initial-population")
                           :criteria (cql-expression "InInitialPopulation")}]}]}
              params {:period [#system/date "2000" #system/date "2020"]
                      :report-type "subject"
                      :subject-ref "0"}]

          (given-failed-future (measure/evaluate-measure context measure params)
            ::anom/category := ::anom/incorrect
            ::anom/message := "Subject with type `Patient` and id `0` was not found."))))))

(def ^:private ^:const bloom-filter-ratio-url
  "https://samply.github.io/blaze/fhir/StructureDefinition/bloom-filter-ratio")

(defn- bloom-filter-ratio [extensions]
  (some #(when (= bloom-filter-ratio-url (:url %)) %) extensions))

(defn- patient-condition-tx-ops [id]
  (cond-> [[:put {:fhir/type :fhir/Patient :id (str id)}]]
    (even? id)
    (conj [:put {:fhir/type :fhir/Condition :id (str id)
                 :subject (type/reference {:reference (type/string (str "Patient/" id))})}])))

(defn- patient-medication-tx-ops [id]
  (cond-> [[:put {:fhir/type :fhir/Patient :id (str id)
                  :gender (if (even? id) #fhir/code "female" #fhir/code "male")}]]
    (zero? (rem id 4))
    (conj [:put {:fhir/type :fhir/MedicationStatement :id (str id)
                 :medication #fhir/Reference{:reference #fhir/string "Medication/0"}
                 :subject (type/reference {:reference (type/string (str "Patient/" id))})}])))

(deftest evaluate-measure-cache-test
  (testing "Condition"
    (with-system-data
      [{:blaze.db/keys [node]
        ::expr/keys [cache]
        :blaze.test/keys [fixed-clock fixed-rng-fn executor]
        ::ts/keys [local]} config]
      [(into [] (mapcat patient-condition-tx-ops) (range 2000))
       [[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri "0"
               :content [(library-content library-exists-condition)]}]]]

      (let [db (d/db node)
            context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                     ::expr/cache cache :executor executor
                     :terminology-service local
                     :blaze/base-url "" ::reitit/router router}
            measure {:fhir/type :fhir/Measure :id "0"
                     :library [#fhir/canonical "0"]
                     :group
                     [{:fhir/type :fhir.Measure/group
                       :population
                       [{:fhir/type :fhir.Measure.group/population
                         :code (population-concept "initial-population")
                         :criteria (cql-expression "InInitialPopulation")}]}]}]

        (testing "without bloom filter because it's not available yet"
          (let [params {:period [#system/date "2000" #system/date "2100"]
                        :report-type "population"}]
            (given (:resource @(measure/evaluate-measure context measure params))
              :fhir/type := :fhir/MeasureReport
              [:extension bloom-filter-ratio :value :numerator :value] := #fhir/decimal 0M
              [:extension bloom-filter-ratio :value :denominator :value] := #fhir/decimal 1M
              [:group 0 :population 0 :count] := #fhir/integer 1000)))

        (Thread/sleep 1000)

        (testing "with bloom filter"
          (let [params {:period [#system/date "2000" #system/date "2100"]
                        :report-type "population"}]
            (given (:resource @(measure/evaluate-measure context measure params))
              :fhir/type := :fhir/MeasureReport
              [:extension bloom-filter-ratio :value :numerator :value] := #fhir/decimal 1M
              [:extension bloom-filter-ratio :value :denominator :value] := #fhir/decimal 1M
              [:group 0 :population 0 :count] := #fhir/integer 1000))

          (given (into [] (ec/list-by-t cache))
            count := 1
            [0 ::bloom-filter/expr-form] := "(exists (retrieve \"Condition\"))")))))

  (testing "Medication"
    (with-system-data
      [{:blaze.db/keys [node]
        ::expr/keys [cache]
        :blaze.test/keys [fixed-clock fixed-rng-fn executor]
        ::ts/keys [local]} config]
      [[[:put {:fhir/type :fhir/Medication :id "0"
               :code #fhir/CodeableConcept{:coding [#fhir/Coding{:system #fhir/uri "http://fhir.de/CodeSystem/dimdi/atc" :code #fhir/code "L01AX03"}]}}]]
       (into [] (mapcat patient-medication-tx-ops) (range 2000))
       [[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri "0"
               :content [(library-content library-medication)]}]]]

      (let [db (d/db node)
            context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                     ::expr/cache cache :executor executor
                     :terminology-service local
                     :blaze/base-url "" ::reitit/router router}
            measure {:fhir/type :fhir/Measure :id "0"
                     :library [#fhir/canonical "0"]
                     :group
                     [{:fhir/type :fhir.Measure/group
                       :population
                       [{:fhir/type :fhir.Measure.group/population
                         :code (population-concept "initial-population")
                         :criteria (cql-expression "InInitialPopulation")}]}]}]

        (testing "without bloom filter because it's not available yet"
          (let [params {:period [#system/date "2000" #system/date "2100"]
                        :report-type "population"}]
            (given (:resource @(measure/evaluate-measure context measure params))
              :fhir/type := :fhir/MeasureReport
              [:extension bloom-filter-ratio :value :numerator :value] := #fhir/decimal 0M
              [:extension bloom-filter-ratio :value :denominator :value] := #fhir/decimal 1M
              [:group 0 :population 0 :count] := #fhir/integer 500)))

        (Thread/sleep 1000)

        (testing "with bloom filter"
          (let [params {:period [#system/date "2000" #system/date "2100"]
                        :report-type "population"}]
            (given (:resource @(measure/evaluate-measure context measure params))
              :fhir/type := :fhir/MeasureReport
              [:extension bloom-filter-ratio :value :numerator :value] := #fhir/decimal 1M
              [:extension bloom-filter-ratio :value :denominator :value] := #fhir/decimal 1M
              [:group 0 :population 0 :count] := #fhir/integer 500))

          (given (into [] (ec/list-by-t cache))
            count := 1
            [0 ::bloom-filter/expr-form] := "(exists (eduction-query (matcher [[\"medication\" \"Medication/0\"]]) (retrieve \"MedicationStatement\")))"))))))

(defmacro testing-query [name count]
  `(testing ~name
     (is (= ~count (-> (first-population (evaluate ~name)) :count type/value)))))

(deftest integration-test
  (testing-query "q1" 2)

  (testing-query "q2" 1)

  (testing-query "q3" 1)

  (testing-query "q4" 1)

  (testing-query "q5" 1)

  (testing-query "q6" 1)

  (testing-query "q7" 1)

  (testing-query "q8" 1)

  (testing-query "q9" 1)

  (testing-query "q10" 1)

  (testing-query "q11" 1)

  (testing-query "q12" 1)

  (testing-query "q13" 1)

  (testing-query "q14" 1)

  (testing-query "q15" 1)

  (testing-query "q16" 1)

  (testing-query "q17" 2)

  (testing-query "q18-specimen-bmi" 1)

  (testing-query "q24" 1)

  (testing-query "q28-relationship-procedure-condition" 1)

  (testing-query "q33-incompatible-quantities" 1)

  (testing-query "q34-medication" 1)

  (testing-query "q35-literal-library-ref" 1)

  (testing-query "q36-parameter" 1)

  (testing-query "q37-overlaps" 3)

  (testing-query "q38-di-surv" 2)

  (testing-query "q39-social-sec-num" 1)

  (testing-query "q42-medication-2" 2)

  (testing-query "q43-medication-3" 2)

  (testing-query "q44-tnm-t" 1)

  (testing-query "q45-histology" 1)

  (testing-query "q46-between-date" 1)

  (testing-query "q47-managing-organization" 1)

  (testing-query "q48-concept" 2)

  (testing-query "q49-length" 1)

  (testing-query "q50-specimen-condition-reference" 1)

  (testing-query "q51-specimen-condition-reference-2" 1)

  (testing-query "q53-population-basis-boolean" 2)

  (testing "q57-mii-specimen-reference"
    ;; we have only one Bloom filter here, because the database contains only
    ;; specimen with one of the six codes
    (given (evaluate "q57-mii-specimen-reference" "population" true)
      [:resource :extension bloom-filter-ratio :value :numerator :value] := #fhir/decimal 1M
      [:resource :extension bloom-filter-ratio :value :denominator :value] := #fhir/decimal 1M
      [first-population :count] := #fhir/integer 1))

  (testing-query "q59-icd10-code-version-independent" 2)

  (testing-query "q60-medication" 2)

  (testing-query "q61-encounter-class" 2)

  (testing-query "q62-consent" 4)

  (testing-query "q63-blood-pressure" 1)

  (testing-query "q64-in-value-set-gender" 1)

  (let [result (evaluate "q1" "subject-list")]
    (testing "MeasureReport is valid"
      (is (s/valid? :fhir/Resource (:resource result))))

    (given (first-population result)
      :count := #fhir/integer 2
      [:subjectResults :reference] := #fhir/string "List/AAAAAAAAAAAAAAAA")

    (given (second (first (:tx-ops result)))
      :fhir/type := :fhir/List
      :id := "AAAAAAAAAAAAAAAA"
      [:entry 0 :item :reference] := #fhir/string "Patient/0"
      [:entry 1 :item :reference] := #fhir/string "Patient/3")))

(deftest stratifier-integration-test
  (let [result (evaluate "q19-stratifier-ageclass")]
    (testing "MeasureReport is valid"
      (is (s/valid? :fhir/Resource (:resource result))))

    (testing "MeasureReport type is `summary`"
      (is (= #fhir/code "summary" (-> result :resource :type))))

    (given (first-stratifier result)
      keys := [:fhir/type :code :stratum]
      :fhir/type := :fhir.MeasureReport.group/stratifier
      [:code count] := 1
      [:code 0] := #fhir/CodeableConcept{:text #fhir/string "age-class"})

    (given (first-stratifier-strata result)
      [0 :value :text] := #fhir/string "10"
      [0 :population 0 :code :coding 0 :system] := #fhir/uri "http://terminology.hl7.org/CodeSystem/measure-population"
      [0 :population 0 :code :coding 0 :code] := #fhir/code "initial-population"
      [0 :population 0 :count] := #fhir/integer 1
      [1 :value :text] := #fhir/string "70"
      [1 :population 0 :count] := #fhir/integer 2))

  (let [result (evaluate "q19-stratifier-ageclass" "subject-list")]
    (testing "MeasureReport is valid"
      (is (s/valid? :fhir/Resource (:resource result))))

    (testing "MeasureReport type is `subject-list`"
      (is (= #fhir/code "subject-list" (-> result :resource :type))))

    (given (first-stratifier-strata result)
      [0 :value :text] := #fhir/string "10"
      [0 :population 0 :count] := #fhir/integer 1
      [0 :population 0 :subjectResults :reference] := #fhir/string "List/AAAAAAAAAAAAAAAB"
      [1 :value :text] := #fhir/string "70"
      [1 :population 0 :count] := #fhir/integer 2
      [1 :population 0 :subjectResults :reference] := #fhir/string "List/AAAAAAAAAAAAAAAC")

    (given (:tx-ops result)
      [1 1 :fhir/type] := :fhir/List
      [1 1 :id] := "AAAAAAAAAAAAAAAB"
      [1 1 :status] := #fhir/code "current"
      [1 1 :mode] := #fhir/code "working"
      [1 1 :entry 0 :item :reference] := #fhir/string "Patient/0"
      [2 1 :fhir/type] := :fhir/List
      [2 1 :id] := "AAAAAAAAAAAAAAAC"
      [2 1 :entry 0 :item :reference] := #fhir/string "Patient/1"
      [2 1 :entry 1 :item :reference] := #fhir/string "Patient/2"))

  (given (first-stratifier-strata (evaluate "q20-stratifier-city"))
    [0 :value :text] := #fhir/string "Jena"
    [0 :population 0 :count] := #fhir/integer 3
    [1 :value :text] := #fhir/string "Leipzig"
    [1 :population 0 :count] := #fhir/integer 1)

  (given (first-stratifier-strata (evaluate "q21-stratifier-city-of-only-women"))
    [0 :value :text] := #fhir/string "Jena"
    [0 :population 0 :count] := #fhir/integer 2)

  (given (evaluate "q22-stratifier-multiple-cities-fail")
    ::anom/category := ::anom/incorrect
    ::anom/message := "CQL expression `City` returned more than one value for resource `Patient/0`.")

  (let [result (evaluate "q23-stratifier-ageclass-and-gender")]
    (given (first-stratifier result)
      keys := [:fhir/type :stratum]
      :fhir/type := :fhir.MeasureReport.group/stratifier)

    (given (first-stratifier-strata result)
      count := 4
      [0 keys] := [:fhir/type :component :population]
      [0 :fhir/type] := :fhir.MeasureReport.group.stratifier/stratum
      [0 :component 0 :code :text] := #fhir/string "age-class"
      [0 :component 0 :value :text] := #fhir/string "10"
      [0 :component 1 :code :text] := #fhir/string "gender"
      [0 :component 1 :value :text] := #fhir/string "male"
      [0 :population 0 :code :coding 0 :system] := #fhir/uri "http://terminology.hl7.org/CodeSystem/measure-population"
      [0 :population 0 :code :coding 0 :code] := #fhir/code "initial-population"
      [0 :population 0 :count] := #fhir/integer 1
      [1 :component 0 :value :text] := #fhir/string "70"
      [1 :component 1 :value :text] := #fhir/string "male"
      [1 :population 0 :count] := #fhir/integer 1
      [2 :component 0 :value :text] := #fhir/string "70"
      [2 :component 1 :value :text] := #fhir/string "female"
      [2 :population 0 :count] := #fhir/integer 2
      [3 :component 0 :value :text] := #fhir/string "10"
      [3 :component 1 :value :text] := #fhir/string "divers"
      [3 :population 0 :count] := #fhir/integer 1))

  (let [result (evaluate "q23-stratifier-ageclass-and-gender" "subject-list")]
    (testing "MeasureReport is valid"
      (is (s/valid? :fhir/Resource (:resource result))))

    (given (first-stratifier-strata result)
      count := 4
      [0 :component 0 :code :text] := #fhir/string "age-class"
      [0 :component 0 :value :text] := #fhir/string "10"
      [0 :component 1 :code :text] := #fhir/string "gender"
      [0 :component 1 :value :text] := #fhir/string "male"
      [0 :population 0 :code :coding 0 :system] := #fhir/uri "http://terminology.hl7.org/CodeSystem/measure-population"
      [0 :population 0 :code :coding 0 :code] := #fhir/code "initial-population"
      [0 :population 0 :count] := #fhir/integer 1
      [0 :population 0 :subjectResults :reference] := #fhir/string "List/AAAAAAAAAAAAAAAB"
      [1 :component 0 :value :text] := #fhir/string "70"
      [1 :component 1 :value :text] := #fhir/string "male"
      [1 :population 0 :count] := #fhir/integer 1
      [1 :population 0 :subjectResults :reference] := #fhir/string "List/AAAAAAAAAAAAAAAC"
      [2 :component 0 :value :text] := #fhir/string "70"
      [2 :component 1 :value :text] := #fhir/string "female"
      [2 :population 0 :count] := #fhir/integer 2
      [2 :population 0 :subjectResults :reference] := #fhir/string "List/AAAAAAAAAAAAAAAD"
      [3 :component 0 :value :text] := #fhir/string "10"
      [3 :component 1 :value :text] := #fhir/string "divers"
      [3 :population 0 :count] := #fhir/integer 1
      [3 :population 0 :subjectResults :reference] := #fhir/string "List/AAAAAAAAAAAAAAAE")

    (given (:tx-ops result)
      [1 1 :fhir/type] := :fhir/List
      [1 1 :id] := "AAAAAAAAAAAAAAAB"
      [1 1 :entry 0 :item :reference] := #fhir/string "Patient/0"
      [2 1 :fhir/type] := :fhir/List
      [2 1 :id] := "AAAAAAAAAAAAAAAC"
      [2 1 :entry 0 :item :reference] := #fhir/string "Patient/1"
      [3 1 :fhir/type] := :fhir/List
      [3 1 :id] := "AAAAAAAAAAAAAAAD"
      [3 1 :entry 0 :item :reference] := #fhir/string "Patient/2"
      [3 1 :entry 1 :item :reference] := #fhir/string "Patient/3"))

  (given (first-stratifier-strata (evaluate "q25-stratifier-collection"))
    [0 :value :text] := #fhir/string "Organization/collection-0"
    [0 :population 0 :count] := #fhir/integer 1
    [1 :value :text] := #fhir/string "Organization/collection-1"
    [1 :population 0 :count] := #fhir/integer 1)

  (given (first-stratifier-strata (evaluate "q26-stratifier-bmi"))
    [0 :value :text] := #fhir/string "37"
    [0 :population 0 :count] := #fhir/integer 1
    [1 :value :text] := #fhir/string "null"
    [1 :population 0 :count] := #fhir/integer 2)

  (given (first-stratifier-strata (evaluate "q27-stratifier-calculated-bmi"))
    [0 :value :text] := #fhir/string "26.8"
    [0 :population 0 :count] := #fhir/integer 1
    [1 :value :text] := #fhir/string "null"
    [1 :population 0 :count] := #fhir/integer 2)

  (given (first-stratifier-strata (evaluate "q29-stratifier-sample-material-type"))
    count := 2
    [0 :value :text] := #fhir/string "liquid"
    [0 :population 0 :count] := #fhir/integer 1
    [1 :value :text] := #fhir/string "tissue"
    [1 :population 0 :count] := #fhir/integer 1)

  (given (evaluate "q30-stratifier-with-missing-expression")
    ::anom/category := ::anom/incorrect
    ::anom/message := "Missing expression with name `SampleMaterialTypeCategory`."
    :expression-name := "SampleMaterialTypeCategory"
    :measure-id := "0")

  (given (first-stratifier-strata (evaluate "q31-stratifier-storage-temperature"))
    count := 2
    [0 :value :text] := #fhir/string "temperature2to10"
    [0 :population 0 :count] := #fhir/integer 1
    [1 :value :text] := #fhir/string "temperatureGN"
    [1 :population 0 :count] := #fhir/integer 1)

  (given (first-stratifier-strata (evaluate "q32-stratifier-underweight"))
    count := 2
    [0 :value :text] := #fhir/string "true"
    [0 :population 0 :count] := #fhir/integer 1
    [1 :value :text] := #fhir/string "false"
    [1 :population 0 :count] := #fhir/integer 2)

  (given (first-stratifier-strata (evaluate "q40-specimen-stratifier"))
    count := 2
    [0 :value :text] := #fhir/string "blood-plasma"
    [0 :population 0 :count] := #fhir/integer 4
    [1 :value :text] := #fhir/string "peripheral-blood-cells-vital"
    [1 :population 0 :count] := #fhir/integer 3)

  (given (first-stratifier-strata (evaluate "q41-specimen-multi-stratifier"))
    count := 4
    [0 :component 0 :code :coding 0 :code type/value] := "sample-diagnosis"
    [0 :component 0 :value :text] := #fhir/string "C34.9"
    [0 :component 1 :code :coding 0 :code type/value] := "sample-type"
    [0 :component 1 :value :text] := #fhir/string "blood-plasma"
    [0 :population 0 :count] := #fhir/integer 2
    [1 :component 0 :value :text] := #fhir/string "C34.9"
    [1 :component 1 :value :text] := #fhir/string "peripheral-blood-cells-vital"
    [1 :population 0 :count] := #fhir/integer 1
    [2 :component 0 :value :text] := #fhir/string "C50.9"
    [2 :component 1 :value :text] := #fhir/string "blood-plasma"
    [2 :population 0 :count] := #fhir/integer 2
    [3 :component 0 :value :text] := #fhir/string "C50.9"
    [3 :component 1 :value :text] := #fhir/string "peripheral-blood-cells-vital"
    [3 :population 0 :count] := #fhir/integer 2)

  (given (first-stratifier-strata (evaluate "q52-sort-with-missing-values"))
    count := 1
    [0 :value :text] := #fhir/string "Condition[id = 0, t = 1, last-change-t = 1]"
    [0 :population 0 :count] := #fhir/integer 1)

  (given (first-stratifier-strata (evaluate "q54-stratifier-condition-code"))
    count := 2
    [0 :value :coding 0 :system] := #fhir/uri "http://hl7.org/fhir/sid/icd-10"
    [0 :value :coding 0 :code] := #fhir/code "C41.9"
    [0 :population 0 :count] := #fhir/integer 1
    [1 :value :coding 0 :system] := #fhir/uri "http://hl7.org/fhir/sid/icd-10"
    [1 :value :coding 0 :code] := #fhir/code "C41.6"
    [1 :population 0 :count] := #fhir/integer 2)

  (given (first-stratifier-strata (evaluate "q55-stratifier-bmi-observation"))
    count := 1
    [0 :value :text] := #fhir/string "36.6 kg/m2"
    [0 :extension 0 :url] := "http://hl7.org/fhir/5.0/StructureDefinition/extension-MeasureReport.group.stratifier.stratum.value"
    [0 :extension 0 :value :value] := #fhir/decimal 36.6M
    [0 :extension 0 :value :unit] := #fhir/string "kg/m2"
    [0 :extension 0 :value :system] := #fhir/uri "http://unitsofmeasure.org"
    [0 :extension 0 :value :code] := #fhir/code "kg/m2"
    [0 :population 0 :count] := #fhir/integer 1)

  (given (first-stratifier-strata (evaluate "q56-stratifier-observation-code-value"))
    count := 2
    [0 :component 0 :code :text] := #fhir/string "code"
    [0 :component 0 :value :coding 0 :system] := #fhir/uri "http://loinc.org"
    [0 :component 0 :value :coding 0 :code] := #fhir/code "39156-5"
    [0 :component 1 :code :text] := #fhir/string "value"
    [0 :component 1 :value :text] := #fhir/string "36.6 kg/m2"
    [0 :component 1 :extension 0 :url] := "http://hl7.org/fhir/5.0/StructureDefinition/extension-MeasureReport.group.stratifier.stratum.component.value"
    [0 :component 1 :extension 0 :value :value] := #fhir/decimal 36.6M
    [0 :component 1 :extension 0 :value :unit] := #fhir/string "kg/m2"
    [0 :component 1 :extension 0 :value :system] := #fhir/uri "http://unitsofmeasure.org"
    [0 :component 1 :extension 0 :value :code] := #fhir/code "kg/m2"
    [0 :population 0 :count] := #fhir/integer 1
    [1 :component 1 :extension 0 :url] := "http://hl7.org/fhir/5.0/StructureDefinition/extension-MeasureReport.group.stratifier.stratum.component.value"
    [1 :component 0 :code :text] := #fhir/string "code"
    [1 :component 0 :value :coding 0 :system] := #fhir/uri "http://loinc.org"
    [1 :component 0 :value :coding 0 :code] := #fhir/code "8302-2"
    [1 :component 1 :code :text] := #fhir/string "value"
    [1 :component 1 :value :text] := #fhir/string "178 cm"
    [1 :component 1 :extension 0 :value :value] := #fhir/decimal 178M
    [1 :component 1 :extension 0 :value :unit] := #fhir/string "cm"
    [1 :component 1 :extension 0 :value :system] := #fhir/uri "http://unitsofmeasure.org"
    [1 :component 1 :extension 0 :value :code] := #fhir/code "cm"
    [1 :population 0 :count] := #fhir/integer 1))

(deftest queries-with-errors-test
  (testing "overly large non-parsable query"
    (given (evaluate "q58-overly-large-nonparsable-query")
      ::anom/category := ::anom/fault
      ::anom/message := "Error while parsing the ELM representation of a CQL library: Could not convert library to JSON using JAXB serializer.")))

(comment
  (log/set-min-level! :debug)
  (evaluate "q61-in-value-set-gender"))
