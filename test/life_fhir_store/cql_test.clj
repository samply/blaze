(ns life-fhir-store.cql-test
  "https://cql.hl7.org/2019May/tests.html"
  (:require
    [clojure.data.xml :as xml]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [life-fhir-store.cql-translator :refer [translate]]
    [life-fhir-store.elm.compiler :refer [compile -eval]]
    [life-fhir-store.elm.type-infer :as type-infer]
    [life-fhir-store.elm.deps-infer :as deps-infer]
    [life-fhir-store.elm.equiv-relationships :as equiv-relationships]
    [life-fhir-store.elm.normalizer :as normalizer])
  (:import
    [java.time OffsetDateTime])
  (:refer-clojure :exclude [compile eval]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (st/instrument
    `compile
    {:spec
     {`compile
      (s/fspec
        :args (s/cat :context any? :expression :elm/expression))}})
  (f)
  (st/unstrument))


(use-fixtures :each fixture)


(defn tests [xml exclusions]
  (for [group (:content xml)]
    {:name (-> group :attrs :name)
     :tests
     (for [test (:content group)
           :let [name (-> test :attrs :name)
                 expression (-> test :content first)
                 invalid? (-> expression :attrs :invalid)
                 output (-> test :content second :content first)]
           :when (not (contains? exclusions name))
           :let [expression-content (-> expression :content first)]]
       {:name name
        :expression expression-content
        :invalid? invalid?
        :output output})}))

(defn to-elm [cql]
  (let [library (translate (str "define x: " cql))]
    (-> library
        normalizer/normalize-library
        equiv-relationships/find-equiv-rels-library
        deps-infer/infer-library-deps
        type-infer/infer-library-types
        :statements :def first :expression)))

(defn eval-elm [elm]
  (-eval (compile {} elm) {:now (OffsetDateTime/now)} nil))

(defn eval [cql]
  (eval-elm (to-elm cql)))

(defn gen-tests [name file exclusions]
  `(deftest ~(symbol name)
     ~@(for [{:keys [name tests]} (tests (xml/parse-str (slurp file)) exclusions)]
         `(testing ~name
            ~@(for [{:keys [name expression invalid? output]} tests]
                `(testing ~name
                   ~(if invalid?
                      `(is (~'thrown? Exception (eval ~expression)))
                      `(is (= (eval ~output) (eval ~expression))))))))))


(defmacro deftests [name file exclusions]
  (gen-tests name file exclusions))


(deftests "arithmetic-functions" "cql-test/CqlArithmeticFunctionsTest.xml"
          #{"DecimalMinValue" "DecimalMaxValue"
            "Ln0" "LnNeg0"
            "Log1Base1"
            "RoundNeg0D5" "RoundNeg1D5"
            "IntegerMinValue"                               ; CQl-To-ELM negates the pos integer which is over Integer/MAX_Value than
            })


(deftests "comparison-operators" "cql-test/CqlComparisonOperatorsTest.xml" #{})


(deftests "logical-operators" "cql-test/CqlLogicalOperatorsTest.xml"
          #{"TrueImpliesTrue"                               ; TODO: CQl-To-ELM error
            "TrueImpliesFalse"                              ; TODO: CQl-To-ELM error
            "TrueImpliesNull"                               ; TODO: CQl-To-ELM error
            "FalseImpliesTrue"                              ; TODO: CQl-To-ELM error
            "FalseImpliesFalse"                             ; TODO: CQl-To-ELM error
            "FalseImpliesNull"                              ; TODO: CQl-To-ELM error
            "NullImpliesTrue"                               ; TODO: CQl-To-ELM error
            "NullImpliesFalse"                              ; TODO: CQl-To-ELM error
            "NullImpliesNull"                               ; TODO: CQl-To-ELM error
            })


(deftests "type-operators" "cql-test/CqlTypeOperatorsTest.xml"
          #{"IntegerToString"                               ; TODO: implement
            "StringToIntegerError"                          ; TODO: implement
            "StringToDateTime"                              ; TODO: implement
            "StringToTime"                                  ; TODO: implement
            "IntegerIsInteger"                              ; TODO: implement
            "StringIsInteger"                               ; TODO: implement
            "StringNoToBoolean"                             ; TODO: implement
            "CodeToConcept1"                                ; TODO: implement
            "ToDateTime1"                                   ; TODO: implement
            "ToDateTime2"                                   ; TODO: implement
            "ToDateTime3"                                   ; TODO: implement
            "ToDateTime4"                                   ; TODO: implement
            "ToDateTime5"                                   ; TODO: implement
            "ToDateTime6"                                   ; TODO: implement
            "String5D5CMToQuantity"                         ; TODO: implement
            "IntegerNeg5ToString"                           ; TODO: implement
            "Decimal18D55ToString"                          ; TODO: implement
            "Quantity5D5CMToString"                         ; TODO: implement
            "BooleanTrueToString"                           ; TODO: implement
            "ToTime1"                                       ; TODO: implement
            "ToTime2"                                       ; TODO: implement
            "ToTime3"                                       ; TODO: implement
            "ToTime4"                                       ; TODO: implement
            })


(deftests "types-test" "cql-test/CqlTypesTest.xml"
          #{"QuantityTest"                                  ; unit `lbs` unknown
            "QuantityTest2"                                 ; unit `eskimo kisses` unknown
            "DateTimeUncertain"                             ; TODO: implement
            })


(deftests "value-literals-and-selectors" "cql-test/ValueLiteralsAndSelectors.xml"
          #{"IntegerNeg2Pow31IntegerMinValue"               ; CQl-To-ELM negates the pos integer which is over Integer/MAX_Value than
            "DecimalTenthStep"                              ; we round here
            "DecimalPosTenthStep"                           ; we round here
            "DecimalNegTenthStep"                           ; we round here
            "Decimal10Pow28ToZeroOneStepDecimalMaxValue"    ; don't get it
            "DecimalPos10Pow28ToZeroOneStepDecimalMaxValue" ; don't get it
            "DecimalNeg10Pow28ToZeroOneStepDecimalMinValue" ; don't get it
            "IntegerMinValue"                               ; CQl-To-ELM negates the pos integer which is over Integer/MAX_Value than
            })

(comment
  (to-elm "null implies null")
  (translate (str "define x: true implies true"))
  )
