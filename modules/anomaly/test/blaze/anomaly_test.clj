(ns blaze.anomaly-test
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.anomaly-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]])
  (:import
   [java.util.concurrent CancellationException ExecutionException TimeoutException]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest anomaly?-test
  (testing "a map with the right category key is an anomaly"
    (is (ba/anomaly? {::anom/category ::anom/fault})))

  (testing "other maps are no anomalies"
    (is (not (ba/anomaly? {})))
    (is (not (ba/anomaly? {::foo ::bar}))))

  (testing "nil is no anomaly"
    (is (not (ba/anomaly? nil)))))

(deftest unavailable?-test
  (testing "a unavailable anomaly has to have the right category"
    (is (ba/unavailable? {::anom/category ::anom/unavailable})))

  (testing "anomalies with other categories are no unavailable anomalies"
    (is (not (ba/unavailable? {::anom/category ::anom/fault}))))

  (testing "nil is no unavailable anomaly"
    (is (not (ba/unavailable? nil)))))

(deftest interrupted?-test
  (testing "a interrupted anomaly has to have the right category"
    (is (ba/interrupted? {::anom/category ::anom/interrupted})))

  (testing "anomalies with other categories are no interrupted anomalies"
    (is (not (ba/interrupted? {::anom/category ::anom/fault}))))

  (testing "nil is no interrupted anomaly"
    (is (not (ba/interrupted? nil)))))

(deftest incorrect?-test
  (testing "a incorrect anomaly has to have the right category"
    (is (ba/incorrect? {::anom/category ::anom/incorrect})))

  (testing "anomalies with other categories are no incorrect anomalies"
    (is (not (ba/incorrect? {::anom/category ::anom/fault}))))

  (testing "nil is no incorrect anomaly"
    (is (not (ba/incorrect? nil)))))

(deftest forbidden?-test
  (testing "a forbidden anomaly has to have the right category"
    (is (ba/forbidden? {::anom/category ::anom/forbidden})))

  (testing "anomalies with other categories are no forbidden anomalies"
    (is (not (ba/forbidden? {::anom/category ::anom/fault}))))

  (testing "nil is no forbidden anomaly"
    (is (not (ba/forbidden? nil)))))

(deftest unsupported?-test
  (testing "a unsupported anomaly has to have the right category"
    (is (ba/unsupported? {::anom/category ::anom/unsupported})))

  (testing "anomalies with other categories are no unsupported anomalies"
    (is (not (ba/unsupported? {::anom/category ::anom/fault}))))

  (testing "nil is no unsupported anomaly"
    (is (not (ba/unsupported? nil)))))

(deftest not-found?-test
  (testing "a not-found anomaly has to have the right category"
    (is (ba/not-found? {::anom/category ::anom/not-found})))

  (testing "anomalies with other categories are no not-found anomalies"
    (is (not (ba/not-found? {::anom/category ::anom/fault}))))

  (testing "nil is no not-found anomaly"
    (is (not (ba/not-found? nil)))))

(deftest conflict?-test
  (testing "a conflict anomaly has to have the right category"
    (is (ba/conflict? {::anom/category ::anom/conflict})))

  (testing "anomalies with other categories are no conflict anomalies"
    (is (not (ba/conflict? {::anom/category ::anom/fault}))))

  (testing "nil is no conflict anomaly"
    (is (not (ba/conflict? nil)))))

(deftest fault?-test
  (testing "a fault anomaly has to have the right category"
    (is (ba/fault? {::anom/category ::anom/fault})))

  (testing "anomalies with other categories are no fault anomalies"
    (is (not (ba/fault? {::anom/category ::anom/not-found}))))

  (testing "nil is no fault anomaly"
    (is (not (ba/fault? nil)))))

(deftest busy?-test
  (testing "a busy anomaly has to have the right category"
    (is (ba/busy? {::anom/category ::anom/busy})))

  (testing "anomalies with other categories are no busy anomalies"
    (is (not (ba/busy? {::anom/category ::anom/fault}))))

  (testing "nil is no busy anomaly"
    (is (not (ba/busy? nil)))))

(deftest unavailable-test
  (testing "without message"
    (is (= (ba/unavailable) {::anom/category ::anom/unavailable})))

  (testing "with nil message"
    (is (= (ba/unavailable nil) {::anom/category ::anom/unavailable})))

  (testing "with message only"
    (given (ba/unavailable "msg-183005")
      ::anom/category := ::anom/unavailable
      ::anom/message := "msg-183005"))

  (testing "with additional kvs"
    (given (ba/unavailable "msg-183005" ::foo ::bar)
      ::anom/category := ::anom/unavailable
      ::anom/message := "msg-183005"
      ::foo := ::bar)))

(deftest interrupted-test
  (testing "without message"
    (is (= (ba/interrupted) {::anom/category ::anom/interrupted})))

  (testing "with nil message"
    (is (= (ba/interrupted nil) {::anom/category ::anom/interrupted})))

  (testing "with message only"
    (given (ba/interrupted "msg-183005")
      ::anom/category := ::anom/interrupted
      ::anom/message := "msg-183005"))

  (testing "with additional kvs"
    (given (ba/interrupted "msg-183005" ::foo ::bar)
      ::anom/category := ::anom/interrupted
      ::anom/message := "msg-183005"
      ::foo := ::bar)))

(deftest incorrect-test
  (testing "without message"
    (is (= (ba/incorrect) {::anom/category ::anom/incorrect})))

  (testing "with nil message"
    (is (= (ba/incorrect nil) {::anom/category ::anom/incorrect})))

  (testing "with message only"
    (given (ba/incorrect "msg-183005")
      ::anom/category := ::anom/incorrect
      ::anom/message := "msg-183005"))

  (testing "with additional kvs"
    (given (ba/incorrect "msg-183005" ::foo ::bar)
      ::anom/category := ::anom/incorrect
      ::anom/message := "msg-183005"
      ::foo := ::bar)))

(deftest forbidden-test
  (testing "without message"
    (is (= (ba/forbidden) {::anom/category ::anom/forbidden})))

  (testing "with nil message"
    (is (= (ba/forbidden nil) {::anom/category ::anom/forbidden})))

  (testing "with message only"
    (given (ba/forbidden "msg-183005")
      ::anom/category := ::anom/forbidden
      ::anom/message := "msg-183005"))

  (testing "with additional kvs"
    (given (ba/forbidden "msg-183005" ::foo ::bar)
      ::anom/category := ::anom/forbidden
      ::anom/message := "msg-183005"
      ::foo := ::bar)))

(deftest unsupported-test
  (testing "without message"
    (is (= (ba/unsupported) {::anom/category ::anom/unsupported})))

  (testing "with nil message"
    (is (= (ba/unsupported nil) {::anom/category ::anom/unsupported})))

  (testing "with message only"
    (given (ba/unsupported "msg-183005")
      ::anom/category := ::anom/unsupported
      ::anom/message := "msg-183005"))

  (testing "with additional kvs"
    (given (ba/unsupported "msg-183005" ::foo ::bar)
      ::anom/category := ::anom/unsupported
      ::anom/message := "msg-183005"
      ::foo := ::bar)))

(deftest not-found-test
  (testing "without message"
    (is (= (ba/not-found) {::anom/category ::anom/not-found})))

  (testing "with nil message"
    (is (= (ba/not-found nil) {::anom/category ::anom/not-found})))

  (testing "with message only"
    (given (ba/not-found "msg-183005")
      ::anom/category := ::anom/not-found
      ::anom/message := "msg-183005"))

  (testing "with additional kvs"
    (given (ba/not-found "msg-183005" ::foo ::bar)
      ::anom/category := ::anom/not-found
      ::anom/message := "msg-183005"
      ::foo := ::bar)))

(deftest conflict-test
  (testing "without message"
    (is (= (ba/conflict) {::anom/category ::anom/conflict})))

  (testing "with nil message"
    (is (= (ba/conflict nil) {::anom/category ::anom/conflict})))

  (testing "with message only"
    (given (ba/conflict "msg-183005")
      ::anom/category := ::anom/conflict
      ::anom/message := "msg-183005"))

  (testing "with additional kvs"
    (given (ba/conflict "msg-183005" ::foo ::bar)
      ::anom/category := ::anom/conflict
      ::anom/message := "msg-183005"
      ::foo := ::bar)))

(deftest fault-test
  (testing "without message"
    (is (= (ba/fault) {::anom/category ::anom/fault})))

  (testing "with nil message"
    (is (= (ba/fault nil) {::anom/category ::anom/fault})))

  (testing "with message only"
    (given (ba/fault "msg-183005")
      ::anom/category := ::anom/fault
      ::anom/message := "msg-183005"))

  (testing "with additional kvs"
    (given (ba/fault "msg-183005" ::foo ::bar)
      ::anom/category := ::anom/fault
      ::anom/message := "msg-183005"
      ::foo := ::bar)))

(deftest busy-test
  (testing "without message"
    (is (= (ba/busy) {::anom/category ::anom/busy})))

  (testing "with nil message"
    (is (= (ba/busy nil) {::anom/category ::anom/busy})))

  (testing "with message only"
    (given (ba/busy "msg-183005")
      ::anom/category := ::anom/busy
      ::anom/message := "msg-183005"))

  (testing "with additional kvs"
    (given (ba/busy "msg-183005" ::foo ::bar)
      ::anom/category := ::anom/busy
      ::anom/message := "msg-183005"
      ::foo := ::bar)))

(deftest anomaly-test
  (testing "ExecutionException"
    (given (ba/anomaly (ExecutionException. (Exception. "msg-184245")))
      ::anom/category := ::anom/fault
      ::anom/message := "msg-184245"))

  (testing "TimeoutException"
    (given (ba/anomaly (TimeoutException. "msg-122233"))
      ::anom/category := ::anom/busy
      ::anom/message := "msg-122233"))

  (testing "CancellationException"
    (given (ba/anomaly (CancellationException. "msg-184321"))
      ::anom/category := ::anom/interrupted
      ::anom/message := "msg-184321"))

  (testing "ExceptionInfo"
    (testing "without an anomaly in data"
      (given (ba/anomaly (ex-info "msg-184349" {::foo ::bar}))
        ::anom/category := ::anom/fault
        ::anom/message := "msg-184349"
        ::foo := ::bar))

    (testing "with an anomaly in data"
      (given (ba/anomaly (ex-info "msg-184349" (ba/incorrect "msg-184433")))
        ::anom/category := ::anom/incorrect
        ::anom/message := "msg-184433"))

    (testing "without message"
      (is (= ::none ((ba/anomaly (ex-info nil {})) ::anom/message ::none))))

    (testing "with cause"
      (given (ba/anomaly (ex-info "msg-181247" {} (Exception. "msg-181120")))
        ::anom/message := "msg-181247"
        [:blaze.anomaly/cause ::anom/message] := "msg-181120")

      (testing "and nil message"
        (given (ba/anomaly (ex-info "msg-181247" {} (Exception.)))
          ::anom/message := "msg-181247"
          [:blaze.anomaly/cause #(% ::anom/message ::none)] := ::none))))

  (testing "Exception"
    (given (ba/anomaly (Exception. "msg-120840"))
      ::anom/category := ::anom/fault
      ::anom/message := "msg-120840"))

  (testing "anomaly"
    (given (ba/anomaly (ba/busy "msg-121702"))
      ::anom/category := ::anom/busy
      ::anom/message := "msg-121702")))

(deftest try-one-test
  (testing "without message"
    (is (= (ba/try-one Exception ::anom/fault (throw (Exception.)))
           {::anom/category ::anom/fault})))

  (testing "with message"
    (given (ba/try-one Exception ::anom/fault (throw (Exception. "msg-134156")))
      ::anom/category := ::anom/fault
      ::anom/message := "msg-134156")))

(deftest try-all-test
  (given (ba/try-all ::anom/fault (throw (Exception. "msg-134347")))
    ::anom/category := ::anom/fault
    ::anom/message := "msg-134347"))

(deftest try-anomaly-test
  (testing "an exception leads to a fault"
    (given (ba/try-anomaly (throw (Exception. "msg-134612")))
      ::anom/category := ::anom/fault
      ::anom/message := "msg-134612"))

  (testing "a thrown busy anomaly will be preserved"
    (given (ba/try-anomaly (throw (ba/ex-anom (ba/busy "msg-134737" ::foo ::bar))))
      ::anom/category := ::anom/busy
      ::anom/message := "msg-134737"
      ::foo := ::bar)))

(deftest ex-anom-test
  (testing "the message will be put in the exception"
    (is (= (ex-message (ba/ex-anom (ba/incorrect "msg-135018"))) "msg-135018")))

  (testing "the complete anomaly goes into ex-data"
    (given (ex-data (ba/ex-anom (ba/unsupported "msg-135018" ::foo ::bar)))
      ::anom/category := ::anom/unsupported
      ::anom/message := "msg-135018"
      ::foo := ::bar)))

(deftest throw-anom-test
  (given (ba/try-anomaly (ba/throw-anom (ba/conflict "msg-174935")))
    ::anom/category := ::anom/conflict
    ::anom/message := "msg-174935"))

(deftest throw-when-test
  (testing "anomalies are thrown"
    (given (ba/try-anomaly (ba/throw-when (ba/unsupported "msg-135500")))
      ::anom/category := ::anom/unsupported
      ::anom/message := "msg-135500"))

  (testing "other values are returned"
    (is (= (ba/throw-when {::foo ::bar}) {::foo ::bar}))))

(deftest when-ok-test
  (testing "no anomaly"
    (testing "without a binding"
      (is (= 1 (when-ok [] 1))))

    (testing "with one binding"
      (is (= 2 (when-ok [x 1] (inc x)))))

    (testing "with two bindings"
      (is (= 3 (when-ok [x 1 y 2] (+ x y))))))

  (testing "anomaly on only binding"
    (given (when-ok [x (ba/fault)] (inc x))
      ::anom/category := ::anom/fault))

  (testing "anomaly on first binding"
    (given (when-ok [x (ba/fault) y 2] (+ x y))
      ::anom/category := ::anom/fault))

  (testing "anomaly on second binding"
    (given (when-ok [x 1 y (ba/fault)] (+ x y))
      ::anom/category := ::anom/fault))

  (testing "bodies can also fail"
    (given (when-ok [] (ba/fault))
      ::anom/category := ::anom/fault)))

(deftest if-ok-test
  (testing "no anomaly"
    (testing "without a binding"
      (is (= 1 (if-ok [] 1 identity))))

    (testing "with one binding"
      (is (= 2 (if-ok [x 1] (inc x) identity))))

    (testing "with two bindings"
      (is (= 3 (if-ok [x 1 y 2] (+ x y) identity)))))

  (testing "anomaly on only binding"
    (given (if-ok [x (ba/fault)] (inc x) #(assoc % ::foo ::bar))
      ::anom/category := ::anom/fault
      ::foo := ::bar))

  (testing "anomaly on first binding"
    (given (if-ok [x (ba/fault) y 2] (+ x y) #(assoc % ::foo ::bar))
      ::anom/category := ::anom/fault
      ::foo := ::bar))

  (testing "anomaly on second binding"
    (given (if-ok [x 1 y (ba/fault)] (+ x y) #(assoc % ::foo ::bar))
      ::anom/category := ::anom/fault
      ::foo := ::bar))

  (testing "else doesn't see the failed body"
    (given (if-ok [] (ba/fault) #(assoc % ::anom/category ::anom/busy))
      ::anom/category := ::anom/fault)))

(deftest map-test
  (testing "no anomaly"
    (is (= 2 (ba/map 1 inc))))

  (testing "with anomaly"
    (given (ba/map (ba/fault) inc)
      ::anom/category := ::anom/fault)))

(deftest exceptionally-test
  (testing "no anomaly"
    (is (= 1 (ba/exceptionally 1 #(assoc % ::foo ::bar)))))

  (testing "with anomaly"
    (given (ba/exceptionally (ba/fault) #(assoc % ::foo ::bar))
      ::anom/category := ::anom/fault
      ::foo := ::bar)))

(deftest ignore-test
  (testing "no anomaly"
    (is (= 1 (ba/ignore 1))))

  (testing "with anomaly"
    (is (nil? (ba/ignore (ba/fault))))))

(deftest update-test
  (is (= {:a 2} (ba/update {:a 1} :a inc)))

  (given (ba/update {:a 1} :a (constantly (ba/fault)))
    ::anom/category := ::anom/fault))
