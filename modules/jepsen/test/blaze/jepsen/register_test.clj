(ns blaze.jepsen.register-test
  (:require
    [blaze.anomaly-spec]
    [blaze.async.comp :as ac]
    [blaze.fhir-client :as fhir-client]
    [blaze.jepsen.register :as register]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is]]))


(st/instrument)


(test/use-fixtures :each tu/fixture)


(def base-uri "base-uri-143457")
(def id "id-144012")
(def vid "vid-144017")
(def multiple-birth 144614)


(defn- fhir-client-read [base-uri type id _]
  (assert (= blaze.jepsen.register-test/base-uri base-uri))
  (assert (= "Patient" type))
  (assert (= blaze.jepsen.register-test/id id))
  (ac/completed-future
    {:fhir/type :Patient
     :multipleBirth multiple-birth}))


(deftest read-test
  (with-redefs [fhir-client/read fhir-client-read]
    (is (= multiple-birth (register/read {:base-uri base-uri} id)))))
