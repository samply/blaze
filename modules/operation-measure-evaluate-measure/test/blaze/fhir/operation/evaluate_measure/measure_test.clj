(ns blaze.fhir.operation.evaluate-measure.measure-test
  (:require
    [blaze.anomaly :as ba]
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-system with-system-data]]
    [blaze.fhir.operation.evaluate-measure.measure :as measure]
    [blaze.fhir.operation.evaluate-measure.measure-spec]
    [blaze.fhir.operation.evaluate-measure.measure.population-spec]
    [blaze.fhir.operation.evaluate-measure.measure.stratifier-spec]
    [blaze.fhir.operation.evaluate-measure.measure.util-spec]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]
    [blaze.log]
    [blaze.test-util :as tu]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]
    [java-time.api :as time]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log])
  (:import
    [java.nio.charset StandardCharsets]
    [java.util Base64]))


(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)


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
    :url #fhir/uri"0"
    :content
    [(type/map->Attachment
       {:contentType #fhir/code"text/cql"
        :data (type/->Base64Binary (b64-encode query))})]}
   :request
   {:method #fhir/code"PUT"
    :url #fhir/uri"Library/1"}})


(defn- read-data [name]
  (let [raw (slurp-resource (str name ".json"))
        bundle (fhir-spec/conform-json (fhir-spec/parse-json raw))
        library (library-entry (slurp-resource (str name ".cql")))]
    (update bundle :entry conj library)))


(def system
  (assoc mem-node-system
    :blaze.test/fixed-rng-fn {}))


(defn- evaluate
  ([name]
   (evaluate name "population"))
  ([name report-type]
   (with-system-data
     [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn]} system]
     [(tx-ops (:entry (read-data name)))]

     (let [db (d/db node)
           context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                    :blaze/base-url "" ::reitit/router router}
           period [#system/date"2000" #system/date"2020"]]
       (measure/evaluate-measure context
                                 @(d/pull node (d/resource-handle db "Measure" "0"))
                                 {:period period :report-type report-type})))))


(defn- first-population [result]
  (if (::anom/category result)
    (prn result)
    (-> result
        :resource
        :group first
        :population first)))


(defn- first-stratifier-strata [result]
  (if (::anom/category result)
    (prn result)
    (-> result
        :resource
        :group first
        :stratifier first
        :stratum)))


(defn- population-concept [code]
  (type/codeable-concept
    {:coding
     [(type/coding
        {:system #fhir/uri"http://terminology.hl7.org/CodeSystem/measure-population"
         :code (type/code code)})]}))


(defn- cql-expression [expr]
  {:fhir/type :fhir/Expression
   :language #fhir/code"text/cql-identifier"
   :expression expr})


(defn encode-base64 [^String s]
  (-> (Base64/getEncoder)
      (.encode (.getBytes s StandardCharsets/UTF_8))
      (String. StandardCharsets/UTF_8)))


(defn library-content [content]
  (type/attachment {:contentType #fhir/code"text/cql"
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


(deftest evaluate-measure-test
  (testing "Encounter population basis"
    (with-system-data
      [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn]} system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Encounter :id "0-0" :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Patient :id "1"}]
        [:put {:fhir/type :fhir/Encounter :id "1-0" :subject #fhir/Reference{:reference "Patient/1"}}]
        [:put {:fhir/type :fhir/Encounter :id "1-1" :subject #fhir/Reference{:reference "Patient/1"}}]
        [:put {:fhir/type :fhir/Patient :id "2"}]]
       [[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri"0"
               :content [(library-content library-encounter)]}]]]

      (let [db (d/db node)
            context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                     :blaze/base-url "" ::reitit/router router}
            measure {:fhir/type :fhir/Measure :id "0"
                     :library [#fhir/canonical"0"]
                     :group
                     [{:fhir/type :fhir.Measure/group
                       :extension
                       [#fhir/Extension
                               {:url "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-populationBasis"
                                :value #fhir/code"Encounter"}]
                       :population
                       [{:fhir/type :fhir.Measure.group/population
                         :code (population-concept "initial-population")
                         :criteria (cql-expression "InInitialPopulation")}]}]}]

        (testing "population report"
          (let [params {:period [#system/date"2000" #system/date"2020"]
                        :report-type "population"}]
            (given (:resource (measure/evaluate-measure context measure params))
              :fhir/type := :fhir/MeasureReport
              [:group 0 :population 0 :code :coding 0 :code] := #fhir/code"initial-population"
              [:group 0 :population 0 :count] := 3)))

        (testing "subject-list report"
          (let [params {:period [#system/date"2000" #system/date"2020"]
                        :report-type "subject-list"}
                {:keys [resource tx-ops]} (measure/evaluate-measure context measure params)]

            (given resource
              :fhir/type := :fhir/MeasureReport
              [:group 0 :population 0 :code :coding 0 :code] := #fhir/code"initial-population"
              [:group 0 :population 0 :count] := 3
              [:group 0 :population 0 :subjectResults :reference] := "List/AAAAAAAAAAAAAAAA")

            (given tx-ops
              [0 0] := :create
              [0 1 :id] := "AAAAAAAAAAAAAAAA"
              [0 1 :entry 0 :item :reference] := "Encounter/0-0"
              [0 1 :entry 1 :item :reference] := "Encounter/1-0"
              [0 1 :entry 2 :item :reference] := "Encounter/1-1"))))))

  (testing "missing criteria"
    (with-system-data
      [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn]} system]
      [[[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri"0"
               :content [(library-content (library-gender true))]}]]]

      (let [db (d/db node)
            context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                     :blaze/base-url "" ::reitit/router router}
            measure-id "measure-id-133021"
            measure {:fhir/type :fhir/Measure :id measure-id
                     :library [#fhir/canonical"0"]
                     :group
                     [{:fhir/type :fhir.Measure/group
                       :population
                       [{:fhir/type :fhir.Measure.group/population
                         :code (population-concept "initial-population")}]}]}
            params {:period [#system/date"2000" #system/date"2020"]
                    :report-type "population"}]
        (given (measure/evaluate-measure context measure params)
          ::anom/category := ::anom/incorrect
          ::anom/message := "Missing criteria."
          :measure-id := measure-id
          :fhir/issue := "required"
          :fhir.issue/expression := "Measure.group[0].population[0]"))))

  (testing "evaluation timeout"
    (with-system-data
      [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn]} system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri"0"
               :content [(library-content (library-gender true))]}]]]

      (let [db (d/db node)
            context {:clock fixed-clock :rng-fn fixed-rng-fn
                     :db db :timeout (time/seconds 0)
                     :blaze/base-url "" ::reitit/router router}
            measure-id "measure-id-132321"
            measure {:fhir/type :fhir/Measure :id measure-id
                     :library [#fhir/canonical"0"]
                     :group
                     [{:fhir/type :fhir.Measure/group
                       :population
                       [{:fhir/type :fhir.Measure.group/population
                         :code (population-concept "initial-population")
                         :criteria (cql-expression "InInitialPopulation")}]}]}
            params {:period [#system/date"2000" #system/date"2020"]
                    :report-type "population"}]
        (given (measure/evaluate-measure context measure params)
          ::anom/category := ::anom/interrupted
          ::anom/message := "Timeout of 0 millis eclipsed while evaluating."
          :measure-id := measure-id))))

  (testing "single subject"
    (doseq [subject-ref ["0" ["Patient" "0"]]
            [library count] [[(library true) 1] [(library false) 0]]]
      (with-system-data
        [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn]} system]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri"0"
                 :content [(library-content library)]}]]]

        (let [db (d/db node)
              context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                       :blaze/base-url "" ::reitit/router router}
              measure {:fhir/type :fhir/Measure :id "0"
                       :url #fhir/uri"measure-155437"
                       :library [#fhir/canonical"0"]
                       :group
                       [{:fhir/type :fhir.Measure/group
                         :population
                         [{:fhir/type :fhir.Measure.group/population
                           :code (population-concept "initial-population")
                           :criteria (cql-expression "InInitialPopulation")}]}]}
              params {:period [#system/date"2000" #system/date"2020"]
                      :report-type "subject"
                      :subject-ref subject-ref}]
          (given (:resource (measure/evaluate-measure context measure params))
            :fhir/type := :fhir/MeasureReport
            :status := #fhir/code"complete"
            :type := #fhir/code"individual"
            :measure := #fhir/canonical"measure-155437"
            [:subject :reference] := "Patient/0"
            :date := #system/date-time"1970-01-01T00:00Z"
            :period := #fhir/Period{:start #system/date-time"2000"
                                    :end #system/date-time"2020"}
            [:group 0 :population 0 :code :coding 0 :code] := #fhir/code"initial-population"
            [:group 0 :population 0 :count] := count))))

    (testing "with stratifiers"
      (doseq [[library count] [[(library-gender true) 1] [(library-gender false) 0]]]
        (with-system-data
          [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn]} system]
          [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"male"}]
            [:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri"0"
                   :content [(library-content library)]}]]]

          (let [db (d/db node)
                context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                         :blaze/base-url "" ::reitit/router router}
                measure {:fhir/type :fhir/Measure :id "0"
                         :url #fhir/uri"measure-155502"
                         :library [#fhir/canonical"0"]
                         :group
                         [{:fhir/type :fhir.Measure/group
                           :population
                           [{:fhir/type :fhir.Measure.group/population
                             :code (population-concept "initial-population")
                             :criteria (cql-expression "InInitialPopulation")}]
                           :stratifier
                           [{:fhir/type :fhir.Measure.group/stratifier
                             :code #fhir/CodeableConcept{:text #fhir/string"gender"}
                             :criteria (cql-expression "Gender")}]}]}
                params {:period [#system/date"2000" #system/date"2020"]
                        :report-type "subject"
                        :subject-ref "0"}]
            (given (:resource (measure/evaluate-measure context measure params))
              :fhir/type := :fhir/MeasureReport
              :status := #fhir/code"complete"
              :type := #fhir/code"individual"
              :measure := #fhir/canonical"measure-155502"
              [:subject :reference] := "Patient/0"
              :date := #system/date-time"1970-01-01T00:00Z"
              :period := #fhir/Period{:start #system/date-time"2000"
                                      :end #system/date-time"2020"}
              [:group 0 :population 0 :code :coding 0 :code] := #fhir/code"initial-population"
              [:group 0 :population 0 :count] := count
              [:group 0 :stratifier 0 :code 0 :text type/value] := "gender"
              [:group 0 :stratifier 0 :stratum 0 :value :text type/value] := (when (= 1 count) "male")
              [:group 0 :stratifier 0 :stratum 0 :population 0 :count] := (when (= 1 count) 1))))))

    (testing "invalid subject"
      (with-system-data
        [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn]} system]
        [[[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri"0"
                 :content [(library-content (library-gender true))]}]]]

        (let [db (d/db node)
              context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                       :blaze/base-url "" ::reitit/router router}
              measure {:fhir/type :fhir/Measure :id "0"
                       :library [#fhir/canonical"0"]
                       :group
                       [{:fhir/type :fhir.Measure/group
                         :population
                         [{:fhir/type :fhir.Measure.group/population
                           :code (population-concept "initial-population")
                           :criteria (cql-expression "InInitialPopulation")}]}]}
              params {:period [#system/date"2000" #system/date"2020"]
                      :report-type "subject"
                      :subject-ref ["Observation" "0"]}]
          (given (measure/evaluate-measure context measure params)
            ::anom/category := ::anom/incorrect
            ::anom/message := "Type mismatch between evaluation subject `Observation` and Measure subject `Patient`."))))

    (testing "missing subject"
      (with-system-data
        [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn]} system]
        [[[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri"0"
                 :content [(library-content (library-gender true))]}]]]

        (let [db (d/db node)
              context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                       :blaze/base-url "" ::reitit/router router}
              measure {:fhir/type :fhir/Measure :id "0"
                       :library [#fhir/canonical"0"]
                       :group
                       [{:fhir/type :fhir.Measure/group
                         :population
                         [{:fhir/type :fhir.Measure.group/population
                           :code (population-concept "initial-population")
                           :criteria (cql-expression "InInitialPopulation")}]}]}
              params {:period [#system/date"2000" #system/date"2020"]
                      :report-type "subject"
                      :subject-ref "0"}]
          (given (measure/evaluate-measure context measure params)
            ::anom/category := ::anom/incorrect
            ::anom/message := "Subject with type `Patient` and id `0` was not found."))))

    (testing "deleted subject"
      (with-system-data
        [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock fixed-rng-fn]} system]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri"0"
                 :content [(library-content (library-gender true))]}]]
         [[:delete "Patient" "0"]]]

        (let [db (d/db node)
              context {:clock fixed-clock :rng-fn fixed-rng-fn :db db
                       :blaze/base-url "" ::reitit/router router}
              measure {:fhir/type :fhir/Measure :id "0"
                       :library [#fhir/canonical"0"]
                       :group
                       [{:fhir/type :fhir.Measure/group
                         :population
                         [{:fhir/type :fhir.Measure.group/population
                           :code (population-concept "initial-population")
                           :criteria (cql-expression "InInitialPopulation")}]}]}
              params {:period [#system/date"2000" #system/date"2020"]
                      :report-type "subject"
                      :subject-ref "0"}]
          (given (measure/evaluate-measure context measure params)
            ::anom/category := ::anom/incorrect
            ::anom/message := "Subject with type `Patient` and id `0` was not found."))))))


(deftest integration-test
  (are [name count] (= count (:count (first-population (evaluate name))))
    "q1" 2
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
    "q18-specimen-bmi" 1
    "q24" 1
    "q28-relationship-procedure-condition" 1
    "q33-incompatible-quantities" 1
    "q34-medication" 1
    "q35-literal-library-ref" 1
    "q36-parameter" 1
    "q37-overlaps" 3
    "q38-di-surv" 2
    "q39-social-sec-num" 1
    "q42-medication-2" 2
    "q43-medication-3" 2
    "q44-tnm-t" 1
    "q45-histology" 1
    "q46-between-date" 1
    "q47-managing-organization" 1
    "q48-concept" 2
    "q49-length" 1)

  (let [result (evaluate "q1" "subject-list")]
    (testing "MeasureReport is valid"
      (is (s/valid? :blaze/resource (:resource result))))

    (given (first-population result)
      :count := 2
      [:subjectResults :reference] := "List/AAAAAAAAAAAAAAAA")

    (given (second (first (:tx-ops result)))
      :fhir/type := :fhir/List
      :id := "AAAAAAAAAAAAAAAA"
      [:entry 0 :item :reference] := "Patient/0"
      [:entry 1 :item :reference] := "Patient/3"))

  (let [result (evaluate "q19-stratifier-ageclass")]
    (testing "MeasureReport is valid"
      (is (s/valid? :blaze/resource (:resource result))))

    (testing "MeasureReport type is `summary`"
      (is (= #fhir/code"summary" (-> result :resource :type))))

    (given (first-stratifier-strata result)
      [0 :value :text type/value] := "10"
      [0 :population 0 :code :coding 0 :system] := #fhir/uri"http://terminology.hl7.org/CodeSystem/measure-population"
      [0 :population 0 :code :coding 0 :code] := #fhir/code"initial-population"
      [0 :population 0 :count] := 1
      [1 :value :text type/value] := "70"
      [1 :population 0 :count] := 2))

  (let [result (evaluate "q19-stratifier-ageclass" "subject-list")]
    (testing "MeasureReport is valid"
      (is (s/valid? :blaze/resource (:resource result))))

    (testing "MeasureReport type is `subject-list`"
      (is (= #fhir/code"subject-list" (-> result :resource :type))))

    (given (first-stratifier-strata result)
      [0 :value :text type/value] := "10"
      [0 :population 0 :count] := 1
      [0 :population 0 :subjectResults :reference] := "List/AAAAAAAAAAAAAAAB"
      [1 :value :text type/value] := "70"
      [1 :population 0 :count] := 2
      [1 :population 0 :subjectResults :reference] := "List/AAAAAAAAAAAAAAAC")

    (given (:tx-ops result)
      [1 1 :fhir/type] := :fhir/List
      [1 1 :id] := "AAAAAAAAAAAAAAAB"
      [1 1 :status] := #fhir/code"current"
      [1 1 :mode] := #fhir/code"working"
      [1 1 :entry 0 :item :reference] := "Patient/0"
      [2 1 :fhir/type] := :fhir/List
      [2 1 :id] := "AAAAAAAAAAAAAAAC"
      [2 1 :entry 0 :item :reference] := "Patient/1"
      [2 1 :entry 1 :item :reference] := "Patient/2"))

  (given (first-stratifier-strata (evaluate "q20-stratifier-city"))
    [0 :value :text type/value] := "Jena"
    [0 :population 0 :count] := 3
    [1 :value :text type/value] := "Leipzig"
    [1 :population 0 :count] := 1)

  (given (first-stratifier-strata (evaluate "q21-stratifier-city-of-only-women"))
    [0 :value :text type/value] := "Jena"
    [0 :population 0 :count] := 2)

  (is (ba/incorrect? (evaluate "q22-stratifier-multiple-cities-fail")))

  (given (first-stratifier-strata (evaluate "q23-stratifier-ageclass-and-gender"))
    [0 :component 0 :code :text type/value] := "age-class"
    [0 :component 0 :value :text type/value] := "10"
    [0 :component 1 :code :text type/value] := "gender"
    [0 :component 1 :value :text type/value] := "male"
    [0 :population 0 :code :coding 0 :system] := #fhir/uri"http://terminology.hl7.org/CodeSystem/measure-population"
    [0 :population 0 :code :coding 0 :code] := #fhir/code"initial-population"
    [0 :population 0 :count] := 1
    [1 :component 0 :value :text type/value] := "70"
    [1 :component 1 :value :text type/value] := "female"
    [1 :population 0 :count] := 2
    [2 :component 0 :value :text type/value] := "70"
    [2 :component 1 :value :text type/value] := "male"
    [2 :population 0 :count] := 1)

  (let [result (evaluate "q23-stratifier-ageclass-and-gender" "subject-list")]
    (testing "MeasureReport is valid"
      (is (s/valid? :blaze/resource (:resource result))))

    (given (first-stratifier-strata result)
      [0 :component 0 :code :text type/value] := "age-class"
      [0 :component 0 :value :text type/value] := "10"
      [0 :component 1 :code :text type/value] := "gender"
      [0 :component 1 :value :text type/value] := "male"
      [0 :population 0 :code :coding 0 :system] := #fhir/uri"http://terminology.hl7.org/CodeSystem/measure-population"
      [0 :population 0 :code :coding 0 :code] := #fhir/code"initial-population"
      [0 :population 0 :count] := 1
      [0 :population 0 :subjectResults :reference] := "List/AAAAAAAAAAAAAAAB"
      [1 :component 0 :value :text type/value] := "70"
      [1 :component 1 :value :text type/value] := "female"
      [1 :population 0 :count] := 2
      [1 :population 0 :subjectResults :reference] := "List/AAAAAAAAAAAAAAAD"
      [2 :component 0 :value :text type/value] := "70"
      [2 :component 1 :value :text type/value] := "male"
      [2 :population 0 :count] := 1
      [2 :population 0 :subjectResults :reference] := "List/AAAAAAAAAAAAAAAC")

    (given (:tx-ops result)
      [1 1 :fhir/type] := :fhir/List
      [1 1 :id] := "AAAAAAAAAAAAAAAB"
      [1 1 :entry 0 :item :reference] := "Patient/0"
      [2 1 :fhir/type] := :fhir/List
      [2 1 :id] := "AAAAAAAAAAAAAAAC"
      [2 1 :entry 0 :item :reference] := "Patient/1"
      [3 1 :fhir/type] := :fhir/List
      [3 1 :id] := "AAAAAAAAAAAAAAAD"
      [3 1 :entry 0 :item :reference] := "Patient/2"
      [3 1 :entry 1 :item :reference] := "Patient/3"))

  (given (first-stratifier-strata (evaluate "q25-stratifier-collection"))
    [0 :value :text type/value] := "Organization/collection-0"
    [0 :population 0 :count] := 1
    [1 :value :text type/value] := "Organization/collection-1"
    [1 :population 0 :count] := 1)

  (given (first-stratifier-strata (evaluate "q26-stratifier-bmi"))
    [0 :value :text type/value] := "37"
    [0 :population 0 :count] := 1
    [1 :value :text type/value] := "null"
    [1 :population 0 :count] := 2)

  (given (first-stratifier-strata (evaluate "q27-stratifier-calculated-bmi"))
    [0 :value :text type/value] := "26.8"
    [0 :population 0 :count] := 1
    [1 :value :text type/value] := "null"
    [1 :population 0 :count] := 2)

  (given (first-stratifier-strata (evaluate "q29-stratifier-sample-material-type"))
    [0 :value :text type/value] := "liquid"
    [0 :population 0 :count] := 1
    [1 :value :text type/value] := "tissue"
    [1 :population 0 :count] := 1)

  (given (evaluate "q30-stratifier-with-missing-expression")
    ::anom/category := ::anom/incorrect,
    ::anom/message := "Missing expression with name `SampleMaterialTypeCategory`.",
    :expression-name := "SampleMaterialTypeCategory"
    :measure-id := "0")

  (given (first-stratifier-strata (evaluate "q31-stratifier-storage-temperature"))
    [0 :value :text type/value] := "temperature2to10"
    [0 :population 0 :count] := 1
    [1 :value :text type/value] := "temperatureGN"
    [1 :population 0 :count] := 1)

  (given (first-stratifier-strata (evaluate "q32-stratifier-underweight"))
    [0 :value :text type/value] := "false"
    [0 :population 0 :count] := 2
    [1 :value :text type/value] := "true"
    [1 :population 0 :count] := 1)

  (given (first-stratifier-strata (evaluate "q40-specimen-stratifier"))
    [0 :value :text type/value] := "blood-plasma"
    [0 :population 0 :count] := 4
    [1 :value :text type/value] := "peripheral-blood-cells-vital"
    [1 :population 0 :count] := 3)

  (given (first-stratifier-strata (evaluate "q41-specimen-multi-stratifier"))
    [0 :component 0 :code :coding 0 :code type/value] := "sample-diagnosis"
    [0 :component 0 :value :text type/value] := "C34.9"
    [0 :component 1 :code :coding 0 :code type/value] := "sample-type"
    [0 :component 1 :value :text type/value] := "blood-plasma"
    [0 :population 0 :count] := 2
    [1 :component 0 :value :text type/value] := "C34.9"
    [1 :component 1 :value :text type/value] := "peripheral-blood-cells-vital"
    [1 :population 0 :count] := 1
    [2 :component 0 :value :text type/value] := "C50.9"
    [2 :component 1 :value :text type/value] := "blood-plasma"
    [2 :population 0 :count] := 2
    [3 :component 0 :value :text type/value] := "C50.9"
    [3 :component 1 :value :text type/value] := "peripheral-blood-cells-vital"
    [3 :population 0 :count] := 2))

(comment
  (log/set-level! :debug)
  (evaluate "q49-length")
  )
