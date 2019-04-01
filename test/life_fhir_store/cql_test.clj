(ns life-fhir-store.cql-test
  "https://cql.hl7.org/2019May/tests.html"
  (:require
    [clojure.data.xml :as xml]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [life-fhir-store.cql-translator :refer [translate]]
    [life-fhir-store.elm.compiler :refer [compile -eval]])
  (:refer-clojure :exclude [compile eval]))


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
    (-> library :statements :def first :expression)))

(defn eval-elm [elm]
  (-eval (compile {} elm) nil nil))

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
            "RoundNeg0D5" "RoundNeg1D5"})
