(ns blaze.test-util-test
  (:require
   [blaze.test-util :as tu :refer [given-thrown satisfies-prop]]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log :refer [with-merged-config]])
  (:import
   [java.nio ByteBuffer]
   [java.util Locale]))

(set! *warn-on-reflection* true)

(test/use-fixtures :each tu/fixture)

(deftest all-ex-data-test
  (testing "exception without data"
    (given (tu/all-ex-data (Exception. "msg-155956"))
      :message := "msg-155956"))

  (testing "exception with data"
    (given (tu/all-ex-data (ex-info "msg-160743" {::x ::y}))
      :message := "msg-160743"
      ::x := ::y))

  (testing "exception with cause"
    (given (tu/all-ex-data (ex-info "msg-160925" {::x ::y}
                                    (ex-info "msg-160932" {::a ::b})))
      :message := "msg-160925"
      ::x := ::y
      [:cause-data ::a] := ::b)))

(deftest given-thrown-test
  (given-thrown (throw (ex-info "msg-161011" {::x ::y}))
    :message := "msg-161011"
    ::x := ::y))

(defn- reports
  "Runs `f`, collecting all clojure.test report events it generates."
  [f]
  (let [reports (atom [])]
    (binding [test/report (partial swap! reports conj)]
      (f))
    @reports))

(deftest satisfies-prop-test
  (testing "a passing property reports a pass"
    (given (reports #(satisfies-prop 10 (prop/for-all [_ gen/small-integer] true)))
      count := 1
      [0 :type] := :pass))

  (testing "a failing property reports a fail"
    (given (reports #(satisfies-prop 10 (prop/for-all [_ gen/small-integer] false)))
      count := 1
      [0 :type] := :fail))

  (testing "a throwing property reports an error"
    (given (reports #(satisfies-prop 10 (prop/for-all [_ gen/small-integer]
                                          (throw (Exception. "msg-162213")))))
      count := 1
      [0 :type] := :error
      [0 :actual ex-message] := "msg-162213")))

(deftest ba-test
  (is (tu/bytes= (byte-array [1 2 3]) (tu/ba 1 2 3))))

(deftest bb-test
  (is (= (ByteBuffer/wrap (byte-array [1 2 3])) (tu/bb 1 2 3))))

(deftest bytes=-test
  (testing "equal byte arrays"
    (is (tu/bytes= (tu/ba 1 2) (tu/ba 1 2))))

  (testing "unequal byte arrays"
    (is (not (tu/bytes= (tu/ba 1 2) (tu/ba 1 3))))))

(deftest set-default-locale-english!-test
  (tu/set-default-locale-english!)
  (is (= Locale/ENGLISH (Locale/getDefault))))

(defn- log-output [f]
  (let [output (atom nil)]
    (with-merged-config
      {:appenders {:test {:enabled? true
                          :fn (fn [data] (reset! output (force (:output_ data))))}}}
      (f))
    @output))

(deftest log-output-test
  (testing "message only"
    (let [output (log-output #(log/info "msg-171357"))]
      (is (str/includes? output "INFO"))
      (is (str/includes? output "msg-171357"))))

  (testing "message with exception"
    (let [output (log-output #(log/error (Exception. "msg-171432") "msg-171406"))]
      (is (str/includes? output "ERROR"))
      (is (str/includes? output "msg-171406"))
      (is (str/includes? output "msg-171432")))))

(deftest permutations-test
  (testing "empty coll"
    (is (= [[]] (tu/permutations []))))

  (testing "one element"
    (is (= [[1]] (tu/permutations [1]))))

  (testing "two elements"
    (is (= [[1 2] [2 1]] (tu/permutations [1 2])))))
