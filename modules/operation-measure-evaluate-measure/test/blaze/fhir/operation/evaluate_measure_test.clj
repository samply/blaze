(ns blaze.fhir.operation.evaluate-measure-test
  (:require
    [blaze.fhir.operation.evaluate-measure :as evaluate-measure]
    [blaze.fhir.operation.evaluate-measure-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is]]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [java.util.concurrent ExecutorService]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest executor-test
  (let [system (ig/init {::evaluate-measure/executor {}})]
    (is (instance? ExecutorService (::evaluate-measure/executor system)))
    (ig/halt! system)))
