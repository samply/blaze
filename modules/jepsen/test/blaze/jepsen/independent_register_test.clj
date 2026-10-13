(ns blaze.jepsen.independent-register-test
  (:require
   [blaze.anomaly-spec]
   [blaze.async.comp :as ac]
   [blaze.fhir-client :as fhir-client]
   [blaze.fhir.spec.type :as type]
   [blaze.jepsen.independent-register :as independent-register]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest]]
   [jepsen.client :as client]
   [jepsen.independent :as independent]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def base-uri "base-uri-101836")
(def id "id-101845")
(def patient-key 7)
(def patient-id "id-101845-7")
(def multiple-birth 102014)

(def client
  (independent-register/->Client {:base-uri base-uri}))

(defn- fhir-client-read [base-uri type id _]
  (assert (= blaze.jepsen.independent-register-test/base-uri base-uri))
  (assert (= "Patient" type))
  (assert (= patient-id id))
  (ac/completed-future
   {:fhir/type :fhir/Patient
    :multipleBirth (type/integer multiple-birth)}))

(deftest read-test
  (with-redefs [fhir-client/read fhir-client-read]
    (given (client/invoke! client {:id id}
                           {:type :invoke :f :read
                            :value (independent/tuple patient-key nil)})
      :type := :ok
      :f := :read
      :value := (independent/tuple patient-key multiple-birth))))

(defn- fhir-client-update [base-uri resource _]
  (assert (= blaze.jepsen.independent-register-test/base-uri base-uri))
  (assert (= patient-id (:id resource)))
  (assert (= (type/integer multiple-birth) (:multipleBirth resource)))
  (ac/completed-future resource))

(deftest write-test
  (with-redefs [fhir-client/update fhir-client-update]
    (given (client/invoke! client {:id id}
                           {:type :invoke :f :write
                            :value (independent/tuple patient-key multiple-birth)})
      :type := :ok
      :f := :write
      :value := (independent/tuple patient-key multiple-birth))))
