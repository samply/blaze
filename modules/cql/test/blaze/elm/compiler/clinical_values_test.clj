(ns blaze.elm.compiler.clinical-values-test
  "3. Clinical Values

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.anomaly :as ba]
   [blaze.elm.code-spec]
   [blaze.elm.code-system-spec]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.clinical-values]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.core-spec]
   [blaze.elm.compiler.test-util :as ctu :refer [has-form]]
   [blaze.elm.concept-spec]
   [blaze.elm.date-time :as date-time]
   [blaze.elm.literal]
   [blaze.elm.literal-spec]
   [blaze.elm.quantity :refer [quantity]]
   [blaze.elm.ratio :refer [ratio]]
   [blaze.elm.value-set-spec]
   [blaze.fhir.test-util]
   [blaze.terminology-service]
   [blaze.terminology-service-spec]
   [blaze.terminology-service.protocols :as p]
   [blaze.test-util :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.properties :as prop]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]])
  (:import
   [blaze.elm.code Code]
   [blaze.elm.concept Concept]
   [blaze.elm.date_time Period]))

(st/instrument)
(ctu/instrument-compile)

(defn- fixture [f]
  (st/instrument)
  (ctu/instrument-compile)
  (f)
  (st/unstrument))

(test/use-fixtures :each fixture)

;; 3.1. Code
;;
;; The Code type represents a literal code selector.
(deftest compile-code-test
  (testing "without version"
    (let [context
          {:library
           {:codeSystems
            {:def [{:name "sys-def-115852" :id "system-115910"}]}}
           :terminology-service (reify p/TerminologyService)}
          expr (c/compile context #elm/code ["sys-def-115852" "code-115927"])]

      (testing "record"
        (given expr
          type := Code
          :system := "system-115910"
          :code := "code-115927"))

      (ctu/testing-constant expr)

      (testing "form"
        (has-form expr '(code "system-115910" nil "code-115927")))))

  (testing "with version"
    (let [context
          {:library
           {:codeSystems
            {:def
             [{:name "sys-def-120434"
               :id "system-120411"
               :version "version-120408"}]}}
           :terminology-service (reify p/TerminologyService)}
          expr (c/compile context #elm/code ["sys-def-120434" "code-120416"])]

      (testing "record"
        (given expr
          type := Code
          :system := "system-120411"
          :version := "version-120408"
          :code := "code-120416"))

      (ctu/testing-constant expr)

      (testing "form"
        (has-form expr '(code "system-120411" "version-120408" "code-120416")))))

  (testing "missing code system"
    (let [context {:library {:codeSystems {:def []}}}]
      (given (ba/try-anomaly (c/compile context #elm/code ["sys-def-112249" "code-112253"]))
        ::anom/category := ::anom/not-found
        ::anom/message := "Can't find the code system `sys-def-112249`."))))

;; 3.2. CodeDef
;;
;; Only use indirectly through CodeRef.

;; 3.3. CodeRef
;;
;; The CodeRef expression allows a previously defined code to be referenced
;; within an expression.
(deftest compile-code-ref-test
  (testing "without version"
    (let [context
          {:library
           {:codeSystems
            {:def
             [{:name "sys-def-125149"
               :id "system-name-125213"}]}
            :codes
            {:def
             [{:name "code-def-125054"
               :id "code-125340"
               :codeSystem {:name "sys-def-125149"}}]}}
           :terminology-service (reify p/TerminologyService)}]
      (given (c/compile context #elm/code-ref "code-def-125054")
        type := Code
        :system := "system-name-125213"
        :code := "code-125340")))

  (testing "with version"
    (let [context
          {:library
           {:codeSystems
            {:def
             [{:name "sys-def-125149"
               :id "system-name-125213"
               :version "version-125222"}]}
            :codes
            {:def
             [{:name "code-def-125054"
               :id "code-125354"
               :codeSystem {:name "sys-def-125149"}}]}}
           :terminology-service (reify p/TerminologyService)}]
      (given (c/compile context #elm/code-ref "code-def-125054")
        type := Code
        :system := "system-name-125213"
        :version := "version-125222"
        :code := "code-125354"))))

;; 3.4. CodeSystemDef
;;
;; Only used indirectly through Code and CodeDef.

;; 3.5. CodeSystemRef
;;
;; The CodeSystemRef expression allows a previously defined named code system to
;; be referenced within an expression. Conceptually, referencing a code system
;; returns the set of codes in the code system. Note that this operation should
;; almost never be performed in practice. Code system references are allowed in
;; order to allow for testing of code membership in a particular code system.
(deftest compile-code-system-ref-test
  (let [context
        {:library
         {:codeSystems
          {:def
           [{:name "code-system-def-182612"
             :id "code-system-085452"}]}}
         :terminology-service (reify p/TerminologyService)}
        expr (c/compile context #elm/code-system-ref "code-system-def-182612")]

    (testing "eval"
      (is (s/valid? :blaze.elm/code-system (core/-eval expr {} nil nil))))

    (ctu/testing-constant expr)

    (testing "form"
      (has-form expr
        '(code-system "code-system-085452")))

    (testing "missing code system"
      (given (ba/try-anomaly (c/compile context #elm/code-system-ref "code-system-def-153757"))
        ::anom/category := ::anom/not-found
        ::anom/message := "Can't find the code system `code-system-def-153757`."))))

;; 3.6. Concept
;;
;; The Concept type represents a literal concept selector.
(deftest compile-concept-test
  (testing "without version and one code"
    (let [context
          {:library
           {:codeSystems
            {:def [{:name "sys-def-115852" :id "system-115910"}]}}}
          elm #elm/concept [[#elm/code ["sys-def-115852" "code-115927"]]]
          expr (c/compile context elm)]

      (testing "record"
        (given expr
          type := Concept
          [:codes count] := 1
          [:codes 0 type] := Code
          [:codes 0 :system] := "system-115910"
          [:codes 0 :version] := nil
          [:codes 0 :code] := "code-115927"))

      (ctu/testing-constant expr)

      (testing "form"
        (has-form expr '(concept (code "system-115910" nil "code-115927"))))))

  (testing "without version and two codes"
    (let [context
          {:library
           {:codeSystems
            {:def [{:name "sys-def-115852" :id "system-115910"}
                   {:name "sys-def-115853" :id "system-115911"}]}}}
          elm #elm/concept [[#elm/code ["sys-def-115852" "code-115927"]
                             #elm/code ["sys-def-115853" "code-115928"]]]
          expr (c/compile context elm)]

      (testing "record"
        (given expr
          type := Concept
          [:codes count] := 2
          [:codes 0 type] := Code
          [:codes 0 :system] := "system-115910"
          [:codes 0 :version] := nil
          [:codes 0 :code] := "code-115927"
          [:codes 1 type] := Code
          [:codes 1 :system] := "system-115911"
          [:codes 1 :version] := nil
          [:codes 1 :code] := "code-115928"))

      (ctu/testing-constant expr)

      (testing "form"
        (has-form expr '(concept (code "system-115910" nil "code-115927")
                                 (code "system-115911" nil "code-115928"))))))

  (testing "with version and one code"
    (let [context
          {:library
           {:codeSystems
            {:def
             [{:name "sys-def-120434"
               :id "system-120411"
               :version "version-120408"}]}}}
          elm #elm/concept [[#elm/code ["sys-def-120434" "code-115927"]]]
          expr (c/compile context elm)]

      (testing "record"
        (given expr
          type := Concept
          [:codes count] := 1
          [:codes 0 type] := Code
          [:codes 0 :system] := "system-120411"
          [:codes 0 :version] := "version-120408"
          [:codes 0 :code] := "code-115927"))

      (ctu/testing-constant expr)

      (testing "form"
        (has-form expr '(concept (code "system-120411" "version-120408"
                                       "code-115927"))))))

  (testing "with version and two codes"
    (let [context
          {:library
           {:codeSystems
            {:def
             [{:name "sys-def-120434"
               :id "system-120411"
               :version "version-120408"}
              {:name "sys-def-115853"
               :id "system-115911"
               :version "version-115909"}]}}}
          elm #elm/concept [[#elm/code ["sys-def-120434" "code-115927"]
                             #elm/code ["sys-def-115853" "code-115928"]]]
          expr (c/compile context elm)]

      (testing "record"
        (given expr
          type := Concept
          [:codes count] := 2
          [:codes 0 type] := Code
          [:codes 0 :system] := "system-120411"
          [:codes 0 :version] := "version-120408"
          [:codes 0 :code] := "code-115927"
          [:codes 1 type] := Code
          [:codes 1 :system] := "system-115911"
          [:codes 1 :version] := "version-115909"
          [:codes 1 :code] := "code-115928"))

      (ctu/testing-constant expr)

      (testing "form"
        (has-form expr '(concept (code "system-120411" "version-120408"
                                       "code-115927")
                                 (code "system-115911" "version-115909"
                                       "code-115928")))))))

;; 3.8. ConceptRef
;;
;; The ConceptRef expression allows a previously defined concept to be
;; referenced within an expression.
(deftest compile-concept-ref-test
  (testing "with one code"
    (let [context
          {:library
           {:codeSystems
            {:def
             [{:name "sys-def-125149"
               :id "system-name-125213"}]}
            :codes
            {:def
             [{:name "code-def-125054"
               :id "code-125354"
               :codeSystem {:name "sys-def-125149"}}]}
            :concepts
            {:def
             [{:name "concept-def-125054"
               :code
               [{:name "code-def-125054"}]}]}}
           :terminology-service (reify p/TerminologyService)}]
      (given (c/compile context #elm/concept-ref "concept-def-125054")
        type := Concept
        [:codes 0 type] := Code
        [:codes 0 :system] := "system-name-125213"
        [:codes 0 :code] := "code-125354")))

  (testing "with two codes"
    (let [context
          {:library
           {:codeSystems
            {:def
             [{:name "sys-def-125149"
               :id "system-name-125213"}
              {:name "sys-def-162523"
               :id "system-name-125214"}]}
            :codes
            {:def
             [{:name "code-def-125054"
               :id "code-125354"
               :codeSystem {:name "sys-def-125149"}}
              {:name "code-def-125055"
               :id "code-125355"
               :codeSystem {:name "sys-def-162523"}}]}
            :concepts
            {:def
             [{:name "concept-def-125055"
               :code
               [{:name "code-def-125054"}
                {:name "code-def-125055"}]}]}}
           :terminology-service (reify p/TerminologyService)}]
      (given (c/compile context #elm/concept-ref "concept-def-125055")
        type := Concept
        [:codes 0 type] := Code
        [:codes 0 :system] := "system-name-125213"
        [:codes 0 :code] := "code-125354"
        [:codes 1 type] := Code
        [:codes 1 :system] := "system-name-125214"
        [:codes 1 :code] := "code-125355"))))

;; 3.9. Quantity
;;
;; The Quantity type defines a clinical quantity. For example, the quantity 10
;; days or 30 mmHg. The value is a decimal, while the unit is expected to be a
;; valid UCUM unit.
(deftest compile-quantity-test
  (testing "Examples"
    (are [elm res] (= res (c/compile {} elm))
      {:type "Quantity"} nil
      #elm/quantity [1] (quantity 1 "")
      #elm/quantity [1 "year"] (date-time/period 1 0 0)
      #elm/quantity [2 "years"] (date-time/period 2 0 0)
      #elm/quantity [1 "month"] (date-time/period 0 1 0)
      #elm/quantity [2 "months"] (date-time/period 0 2 0)
      #elm/quantity [1 "week"] (date-time/period 0 0 (* 7 24 60 60 1000))
      #elm/quantity [2 "weeks"] (date-time/period 0 0 (* 2 7 24 60 60 1000))
      #elm/quantity [1 "day"] (date-time/period 0 0 (* 24 60 60 1000))
      #elm/quantity [2 "days"] (date-time/period 0 0 (* 2 24 60 60 1000))
      #elm/quantity [1 "hour"] (date-time/period 0 0 (* 60 60 1000))
      #elm/quantity [2 "hours"] (date-time/period 0 0 (* 2 60 60 1000))
      #elm/quantity [1 "minute"] (date-time/period 0 0 (* 60 1000))
      #elm/quantity [2 "minutes"] (date-time/period 0 0 (* 2 60 1000))
      #elm/quantity [1 "second"] (date-time/period 0 0 1000)
      #elm/quantity [2 "seconds"] (date-time/period 0 0 2000)
      #elm/quantity [1 "millisecond"] (date-time/period 0 0 1)
      #elm/quantity [2 "milliseconds"] (date-time/period 0 0 2)
      #elm/quantity [1 "s"] (quantity 1 "s")
      #elm/quantity [1 "cm2"] (quantity 1 "cm2")))

  (let [expr (c/compile {} #elm/quantity [1 "s"])]

    (ctu/testing-constant expr)

    (testing "form"
      (has-form expr '(quantity 1M "s"))))

  (testing "Periods"
    (satisfies-prop 100
      (prop/for-all [period (s/gen :elm/period)]
        (#{BigDecimal Period} (type (core/-eval (c/compile {} period) {} nil nil)))))))

;; 3.10. Ratio
;;
;; The Ratio type defines a ratio between two quantities. For example, the
;; titre 1:128, or the concentration ratio 5 mg/10 mL. The numerator and
;; denominator are both quantities.
(deftest compile-ratio-test
  (testing "Examples"
    (are [elm res] (= res (c/compile {} elm))
      #elm/ratio [[1 "s"] [1 "s"]] (ratio (quantity 1 "s") (quantity 1 "s"))
      #elm/ratio [[1 ""] [128 ""]] (ratio (quantity 1 "") (quantity 128 ""))
      #elm/ratio [[1 "s"] [1 ""]] (ratio (quantity 1 "s") (quantity 1 ""))
      #elm/ratio [[1 ""] [1 "s"]] (ratio (quantity 1 "") (quantity 1 "s"))
      #elm/ratio [[1 "cm2"] [1 "s"]] (ratio (quantity 1 "cm2") (quantity 1 "s"))
      #elm/ratio [[1] [1]] (ratio (quantity 1 "") (quantity 1 ""))
      #elm/ratio [[1] [1 "s"]] (ratio (quantity 1 "") (quantity 1 "s"))
      #elm/ratio [[1 "s"] [1]] (ratio (quantity 1 "s") (quantity 1 ""))
      #elm/ratio [[5 "mg"] [10 "g"]] (ratio (quantity 5 "mg") (quantity 10 "g"))
      #elm/ratio [[3 "m"] [1 "s"]] (ratio (quantity 3 "m") (quantity 1 "s"))))

  (let [expr (c/compile {} #elm/ratio [[3 "m"] [1 "s"]])]

    (ctu/testing-constant expr)

    (testing "form"
      (has-form expr '(ratio (quantity 3M "m") (quantity 1M "s"))))))

;; 3.12. ValueSetRef
;;
;; The ValueSetRef expression allows a previously defined named value set to be
;; referenced within an expression. Conceptually, referencing a value set
;; returns the expansion set for the value set as a list of codes.
;;
;; The preserve attribute is trial-use in CQL 1.5.2 and was introduced to allow
;; engines to determine whether or not to expand a ValueSetRef (the 1.4
;; behavior), ensuring that 1.5 engines can run 1.4 ELM.
(deftest compile-value-set-ref-test
  (let [context
        {:library
         {:valueSets
          {:def
           [{:name "value-set-def-135520"
             :id "value-set-135750"}]}}
         :terminology-service (reify p/TerminologyService)}
        expr (c/compile context #elm/value-set-ref "value-set-def-135520")]

    (testing "eval"
      (is (s/valid? :blaze.elm/value-set (core/-eval expr {} nil nil))))

    (ctu/testing-constant expr)

    (testing "form"
      (has-form expr
        '(value-set "value-set-135750")))

    (testing "missing value set"
      (given (ba/try-anomaly (c/compile context #elm/value-set-ref "value-set-def-153903"))
        ::anom/category := ::anom/not-found
        ::anom/message := "Can't find the value set `value-set-def-153903`."))))
