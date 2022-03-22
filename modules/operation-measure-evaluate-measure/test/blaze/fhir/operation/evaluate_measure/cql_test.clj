(ns blaze.fhir.operation.evaluate-measure.cql-test
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.anomaly-spec]
    [blaze.cql-translator :as cql-translator]
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-system with-system-data]]
    [blaze.elm.compiler.library :as library]
    [blaze.elm.expression :as expr]
    [blaze.fhir.operation.evaluate-measure.cql :as cql]
    [blaze.fhir.operation.evaluate-measure.cql-spec]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.time Clock OffsetDateTime]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn- now [clock]
  (OffsetDateTime/now ^Clock clock))


(def cql
  "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'

  context Patient

  define InInitialPopulation:
    Patient.gender = 'male'")


(defn- compile-library [node]
  (when-ok [library (cql-translator/translate cql)]
    (library/compile-library node library {})))


(defn- failing-eval [msg]
  (fn [_ _ _] (throw (Exception. ^String msg))))


(deftest evaluate-expression-test
  (with-system-data [{:blaze.db/keys [node] :blaze.test/keys [clock]}
                     mem-node-system]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]
      [:put {:fhir/type :fhir/Patient :id "1" :gender #fhir/code "male"}]
      [:put {:fhir/type :fhir/Patient :id "2" :gender #fhir/code "female"}]]]
    (let [context {:db (d/db node)
                   :now (now clock)
                   :library (compile-library node)
                   :subject-type "Patient"
                   :report-type "population"}]
      (is (= 1 (cql/evaluate-expression context "InInitialPopulation")))))

  (testing "failing eval"
    (with-system-data [{:blaze.db/keys [node] :blaze.test/keys [clock]}
                       mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]
      (let [context {:db (d/db node)
                     :now (now clock)
                     :library (compile-library node)
                     :subject-type "Patient"
                     :report-type "population"}]
        (with-redefs [expr/eval (failing-eval "msg-222453")]
          (given (cql/evaluate-expression context "InInitialPopulation")
            ::anom/category := ::anom/fault
            ::anom/message := "msg-222453"))))))


(deftest evaluate-individual-expression-test
  (testing "match"
    (with-system-data [{:blaze.db/keys [node] :blaze.test/keys [clock]}
                       mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "male"}]]]
      (let [db (d/db node)
            patient (d/resource-handle db "Patient" "0")
            context {:db db
                     :now (now clock)
                     :library (compile-library node)}]
        (is (true? (cql/evaluate-individual-expression context patient "InInitialPopulation"))))))

  (testing "no match"
    (with-system-data [{:blaze.db/keys [node] :blaze.test/keys [clock]}
                       mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]
      (let [db (d/db node)
            patient (d/resource-handle db "Patient" "0")
            context {:db db
                     :now (now clock)
                     :library (compile-library node)}]
        (is (false? (cql/evaluate-individual-expression context patient "InInitialPopulation")))))))


(def two-value-eval
  (fn [_ _ _] ["1" "2"]))


(deftest calc-strata-test
  (testing "failing eval"
    (with-system-data [{:blaze.db/keys [node] :blaze.test/keys [clock]}
                       mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]
      (let [context {:db (d/db node)
                     :now (now clock)
                     :library (compile-library node)
                     :subject-type "Patient"
                     :report-type "population"}]
        (with-redefs [expr/eval (failing-eval "msg-221825")]
          (given (cql/calc-strata context "" "")
            ::anom/category := ::anom/fault
            ::anom/message := "msg-221825")))))

  (testing "multiple values"
    (with-system-data [{:blaze.db/keys [node] :blaze.test/keys [clock]}
                       mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]
      (let [context {:db (d/db node)
                     :now (now clock)
                     :library (compile-library node)
                     :subject-type "Patient"
                     :report-type "population"}]
        (with-redefs [expr/eval two-value-eval]
          (given (cql/calc-strata context "" "expr-133506")
            ::anom/category := ::anom/incorrect
            ::anom/message := "CQL expression `expr-133506` returned more than one value for resource `Patient/0`."))))))


(deftest calc-individual-strata-test
  (testing "failing eval"
    (st/instrument
      `cql/calc-individual-strata
      {:spec
       {`cql/calc-individual-strata
        (s/fspec
          :args (s/cat :context nil?
                       :subject-handle nil?
                       :population-expression-name nil?
                       :stratum-expression-name nil?))}})
    (with-redefs [expr/eval (failing-eval "msg-221154")]
      (given (cql/calc-individual-strata nil nil nil nil)
        ::anom/category := ::anom/fault
        ::anom/message := "msg-221154"))))
