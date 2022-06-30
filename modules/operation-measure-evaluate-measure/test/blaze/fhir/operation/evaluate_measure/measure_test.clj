(ns blaze.fhir.operation.evaluate-measure.measure-test
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-system with-system-data]]
    [blaze.fhir.operation.evaluate-measure.measure :refer [evaluate-measure]]
    [blaze.fhir.operation.evaluate-measure.measure-spec]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]
    [blaze.log]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log])
  (:import
    [java.util Base64]))


(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


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
  (let [raw (slurp-resource (str name "-data.json"))
        bundle (fhir-spec/conform-json (fhir-spec/parse-json raw))
        library (library-entry (slurp-resource (str name "-query.cql")))]
    (update bundle :entry conj library)))


(def system
  (assoc mem-node-system
    :blaze.test/fixed-rng-fn {}))


(defn- evaluate
  ([name]
   (evaluate name "population"))
  ([name report-type]
   (with-system-data
     [{:blaze.db/keys [node] :blaze.test/keys [clock fixed-rng-fn]} system]
     [(tx-ops (:entry (read-data name)))]

     (let [db (d/db node)
           context {:clock clock :rng-fn fixed-rng-fn :db db
                    :blaze/base-url "" ::reitit/router router}
           period [#system/date"2000" #system/date"2020"]]
       (evaluate-measure context
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


(def library-content
  #fhir/Attachment
      {:contentType #fhir/code"text/cql"
       :data #fhir/base64Binary"bGlicmFyeSBSZXRyaWV2ZQp1c2luZyBGSElSIHZlcnNpb24gJzQuMC4wJwppbmNsdWRlIEZISVJIZWxwZXJzIHZlcnNpb24gJzQuMC4wJwoKY29udGV4dCBQYXRpZW50CgpkZWZpbmUgSW5Jbml0aWFsUG9wdWxhdGlvbjoKICB0cnVlCgpkZWZpbmUgR2VuZGVyOgogIFBhdGllbnQuZ2VuZGVyCg=="})


(deftest evaluate-measure-test
  (testing "missing criteria"
    (with-system-data
      [{:blaze.db/keys [node] :blaze.test/keys [clock fixed-rng-fn]} system]
      [[[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri"0"
               :content [library-content]}]]]

      (let [db (d/db node)
            context {:clock clock :rng-fn fixed-rng-fn :db db
                     :blaze/base-url "" ::reitit/router router}
            measure {:fhir/type :fhir/Measure :id "0"
                     :library [#fhir/canonical"0"]
                     :group
                     [{:fhir/type :fhir.Measure/group
                       :population
                       [{:fhir/type :fhir.Measure.group/population
                         :code (population-concept "initial-population")}]}]}
            params {:period [#system/date"2000" #system/date"2020"]
                    :report-type "subject"
                    :subject "Patient/0"}]
        (given (evaluate-measure context measure params)
          ::anom/category := ::anom/incorrect
          ::anom/message := "Missing criteria."
          :fhir/issue := "required"
          :fhir.issue/expression := "Measure.group[0].population[0]"))))

  (testing "single subject"
    (with-system-data
      [{:blaze.db/keys [node] :blaze.test/keys [clock fixed-rng-fn]} system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri"0"
               :content [library-content]}]]]

      (let [db (d/db node)
            context {:clock clock :rng-fn fixed-rng-fn :db db
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
        (given (:resource (evaluate-measure context measure params))
          :fhir/type := :fhir/MeasureReport
          :status := #fhir/code"complete"
          :type := #fhir/code"individual"
          :measure := #fhir/canonical"/0"
          [:subject :reference] := "Patient/0"
          :date := #system/date-time"1970-01-01T00:00Z"
          :period := #fhir/Period{:start #system/date-time"2000"
                                  :end #system/date-time"2020"}
          [:group 0 :population 0 :code :coding 0 :code] := #fhir/code"initial-population"
          [:group 0 :population 0 :count] := 1)))

    (testing "with stratifiers"
      (with-system-data
        [{:blaze.db/keys [node] :blaze.test/keys [clock fixed-rng-fn]} system]
        [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"male"}]
          [:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri"0"
                 :content [library-content]}]]]

        (let [db (d/db node)
              context {:clock clock :rng-fn fixed-rng-fn :db db
                       :blaze/base-url "" ::reitit/router router}
              measure {:fhir/type :fhir/Measure :id "0"
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
          (given (:resource (evaluate-measure context measure params))
            :fhir/type := :fhir/MeasureReport
            :status := #fhir/code"complete"
            :type := #fhir/code"individual"
            :measure := #fhir/canonical"/0"
            [:subject :reference] := "Patient/0"
            :date := #system/date-time"1970-01-01T00:00Z"
            :period := #fhir/Period{:start #system/date-time"2000"
                                    :end #system/date-time"2020"}
            [:group 0 :population 0 :code :coding 0 :code] := #fhir/code"initial-population"
            [:group 0 :population 0 :count] := 1
            [:group 0 :stratifier 0 :code 0 :text type/value] := "gender"
            [:group 0 :stratifier 0 :stratum 0 :value :text type/value] := "male"
            [:group 0 :stratifier 0 :stratum 0 :population 0 :count] := 1))))

    (testing "invalid subject"
      (with-system-data
        [{:blaze.db/keys [node] :blaze.test/keys [clock fixed-rng-fn]} system]
        [[[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri"0"
                 :content [library-content]}]]]

        (let [db (d/db node)
              context {:clock clock :rng-fn fixed-rng-fn :db db
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
          (given (evaluate-measure context measure params)
            ::anom/category := ::anom/incorrect
            ::anom/message := "Type mismatch between evaluation subject `Observation` and Measure subject `Patient`."))))

    (testing "missing subject"
      (with-system-data
        [{:blaze.db/keys [node] :blaze.test/keys [clock fixed-rng-fn]} system]
        [[[:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri"0"
                 :content [library-content]}]]]

        (let [db (d/db node)
              context {:clock clock :rng-fn fixed-rng-fn :db db
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
          (given (evaluate-measure context measure params)
            ::anom/category := ::anom/incorrect
            ::anom/message := "Subject with type `Patient` and id `0` was not found."))))

    (testing "deleted subject"
      (with-system-data
        [{:blaze.db/keys [node] :blaze.test/keys [clock fixed-rng-fn]} system]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Library :id "0" :url #fhir/uri"0"
                 :content [library-content]}]]
         [[:delete "Patient" "0"]]]

        (let [db (d/db node)
              context {:clock clock :rng-fn fixed-rng-fn :db db
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
          (given (evaluate-measure context measure params)
            ::anom/category := ::anom/incorrect
            ::anom/message := "Subject with type `Patient` and id `0` was not found."))))))


(deftest integration-test
  (are [name count] (= count (:count (first-population (evaluate name))))
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
    "q18-specimen-bmi" 1
    "q24" 1
    "q28-relationship-procedure-condition" 1
    "q33-incompatible-quantities" 1
    "q34-medication" 1
    "q35-literal-library-ref" 1
    "q36-parameter" 1
    "q37-overlaps" 3
    "q38-di-surv" 2)

  (let [result (evaluate "q1" "subject-list")]
    (testing "MeasureReport is valid"
      (is (s/valid? :blaze/resource (:resource result))))

    (given (first-population result)
      :count := 1
      [:subjectResults :reference] := "List/AAAAAAAAAAAAAAAA")

    (given (second (first (:tx-ops result)))
      :fhir/type := :fhir/List
      :id := "AAAAAAAAAAAAAAAA"
      [:entry 0 :item :reference] := "Patient/0"))

  (let [result (evaluate "q19-stratifier-ageclass")]
    (testing "MeasureReport is valid"
      (is (s/valid? :blaze/resource (:resource result))))

    (testing "MeasureReport type is `summary`"
      (is (= #fhir/code"summary" (-> result :resource :type))))

    (given (first-stratifier-strata result)
      [0 :value :text type/value] := "10"
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

  (is (= ::anom/incorrect (::anom/category (evaluate "q22-stratifier-multiple-cities-fail"))))

  (given (first-stratifier-strata (evaluate "q23-stratifier-ageclass-and-gender"))
    [0 :component 0 :code :text type/value] := "age-class"
    [0 :component 0 :value :text type/value] := "10"
    [0 :component 1 :code :text type/value] := "gender"
    [0 :component 1 :value :text type/value] := "male"
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

  (given (first-stratifier-strata (evaluate "q30-stratifier-with-missing-expression"))
    [0 :value :text type/value] := "null"
    [0 :population 0 :count] := 2)

  (given (first-stratifier-strata (evaluate "q31-stratifier-storage-temperature"))
    [0 :value :text type/value] := "temperature2to10"
    [0 :population 0 :count] := 1
    [1 :value :text type/value] := "temperatureGN"
    [1 :population 0 :count] := 1)

  (given (first-stratifier-strata (evaluate "q32-stratifier-underweight"))
    [0 :value :text type/value] := "false"
    [0 :population 0 :count] := 2
    [1 :value :text type/value] := "true"
    [1 :population 0 :count] := 1))

(comment
  (log/set-level! :debug)
  (evaluate "q38-di-surv")

  )
