(ns blaze.elm.compiler.clinical-operators-test
  "23. Clinical Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.clinical-operators]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.core-spec]
   [blaze.elm.compiler.test-util :as ctu :refer [has-form]]
   [blaze.elm.expression :as expr]
   [blaze.elm.expression-spec]
   [blaze.elm.literal :as elm]
   [blaze.elm.literal-spec]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service-spec]
   [blaze.terminology-service.local]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [integrant.core :as ig]
   [java-time.api :as time]))

(st/instrument)
(ctu/instrument-compile)

(defn- fixture [f]
  (st/instrument)
  (ctu/instrument-compile)
  (f)
  (st/unstrument))

(test/use-fixtures :each fixture)

;; TODO 23.1. AnyInCodeSystem

;; TODO 23.2. AnyInValueSet

;; 23.3. CalculateAge
;;
;; Normalized to CalculateAgeAt
(deftest compile-calculate-age-test
  (ctu/unsupported-unary-operand "CalculateAge"))

;; 23.4. CalculateAgeAt
;;
;; Calculates the age in the specified precision of a person born on a given
;; date, as of another given date.
;;
;; The CalculateAgeAt operator has two signatures: (Date, Date) (DateTime,
;; DateTime)
;;
;; For the Date overload, precision must be one of year, month, week, or day,
;; and the result is the number of whole calendar periods that have elapsed
;; between the first date and the second date.
;;
;; For the DateTime overload, the result is the number of whole calendar periods
;; that have elapsed between the first datetime and the second datetime.
(deftest compile-calculate-age-at-test
  (testing "Static"
    (are [elm res] (= res (core/-eval (c/compile {} elm) {:now ctu/now} nil nil))
      #elm/calculate-age-at [#elm/date"2018" #elm/date"2019" "year"] 1
      #elm/calculate-age-at [#elm/date"2018" #elm/date"2018" "year"] 0
      #elm/calculate-age-at [#elm/date"2018" #elm/date"2019" "month"] nil

      #elm/calculate-age-at [#elm/date"2018-01" #elm/date"2019-02" "year"] 1
      #elm/calculate-age-at [#elm/date"2018-01" #elm/date"2018-12" "year"] 0
      #elm/calculate-age-at [#elm/date"2018-01" #elm/date"2018-12" "month"] 11
      #elm/calculate-age-at [#elm/date"2018-01" #elm/date"2018-12" "day"] nil

      #elm/calculate-age-at [#elm/date"2018-01-01" #elm/date"2019-02-02" "year"] 1
      #elm/calculate-age-at [#elm/date"2018-01" #elm/date"2018-12-15" "year"] 0
      #elm/calculate-age-at [#elm/date"2018-01-01" #elm/date"2018-12-02" "month"] 11
      #elm/calculate-age-at [#elm/date"2018-01-01" #elm/date"2018-02-01" "day"] 31

      #elm/calculate-age-at [#elm/date-time"2018-01-01" #elm/date-time"2018-02-01" "day"] 31))

  (ctu/testing-binary-null elm/calculate-age-at #elm/date"2018")
  (ctu/testing-binary-null elm/calculate-age-at #elm/date-time"2018-01-01")

  (ctu/testing-binary-precision-dynamic elm/calculate-age-at "year" "month" "day")

  (ctu/testing-binary-precision-attach-cache elm/calculate-age-at "year" "month" "day")

  (ctu/testing-binary-precision-patient-count elm/calculate-age-at "year" "month" "day")

  (ctu/testing-binary-precision-resolve-refs elm/calculate-age-at "year" "month" "day")

  (ctu/testing-binary-precision-resolve-params elm/calculate-age-at "year" "month" "day")

  (ctu/testing-binary-precision-optimize elm/calculate-age-at "year" "month" "day")

  (ctu/testing-binary-precision-equals-hash-code elm/calculate-age-at "year" "month" "day")

  (ctu/testing-binary-precision-form elm/calculate-age-at "year" "month" "day"))

;; 23.5. Equal

;; 23.6. Equivalent

;; TODO 23.7. InCodeSystem

;; 23.8. InValueSet
;;
;; The InValueSet operator returns true if the given code is in the given value
;; set.
;;
;; The first argument is expected to be a String, Code, or Concept.
;;
;; The second argument is statically a ValueSetRef. This allows for both static
;; analysis of the value set references within an artifact, as well as the
;; implementation of value set membership by the target environment as a service
;; call to a terminology server, if desired.
;;
;; The third argument is expected to be a ValueSet, allowing references to value
;; sets to be preserved as references.
(def ^:private config
  (assoc
   mem-node-config
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)}))

(defn- eval-context [db]
  {:db db :now (time/offset-date-time)})

(deftest in-value-set-test
  (testing "Code"
    (with-system-data [{:blaze.db/keys [node] terminology-service ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-105600"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}}]]]

      (let [context
            {:library
             {:codeSystems
              {:def [{:name "sys-def-115852" :id "system-115910"}]}
              :valueSets
              {:def
               [{:name "value-set-def-135520"
                 :id "value-set-135750"}]}}
             :terminology-service terminology-service}
            elm #elm/in-value-set [#elm/code ["sys-def-115852" "code-115927"]
                                   #elm/value-set-ref "value-set-def-135520"]
            expr (c/compile context elm)
            db (d/db node)]

        (testing "eval"
          (is (true? (expr/eval (eval-context db) expr nil))))

        (testing "form"
          (has-form expr
            '(in-value-set
              (code "system-115910" nil "code-115927")
              (value-set "value-set-135750")))))))

  (testing "String"
    (with-system-data [{:blaze.db/keys [node] terminology-service ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-105600"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}}]]]

      (let [context
            {:library
             {:valueSets
              {:def
               [{:name "value-set-def-135520"
                 :id "value-set-135750"}]}}
             :terminology-service terminology-service}
            elm #elm/in-value-set [#elm/string "code-115927"
                                   #elm/value-set-ref "value-set-def-135520"]
            expr (c/compile context elm)
            db (d/db node)]

        (testing "eval"
          (is (true? (expr/eval (eval-context db) expr nil))))

        (testing "form"
          (has-form expr
            '(in-value-set
              "code-115927"
              (value-set "value-set-135750")))))))

  (testing "Concept"
    (with-system-data [{:blaze.db/keys [node] terminology-service ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-105600"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}}]]]

      (let [context
            {:library
             {:codeSystems
              {:def [{:name "sys-def-115852" :id "system-115910" :version "version-132113"}]}
              :valueSets
              {:def
               [{:name "value-set-def-135520"
                 :id "value-set-135750"}]}}
             :terminology-service terminology-service}
            elm #elm/in-value-set [#elm/concept [[#elm/code ["sys-def-115852" "code-115927"]]]
                                   #elm/value-set-ref "value-set-def-135520"]
            expr (c/compile context elm)
            db (d/db node)]

        (testing "eval"
          (is (true? (expr/eval (eval-context db) expr nil))))

        (testing "form"
          (has-form expr
            '(in-value-set
              (concept (code "system-115910" "version-132113" "code-115927"))
              (value-set "value-set-135750"))))))))

;; 23.9. Not Equal

;; TODO 23.10. SubsumedBy

;; TODO 23.11. Subsumes
