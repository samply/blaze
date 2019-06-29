(ns blaze.handler.fhir.history-instance-test
  "Specifications relevant for the FHIR history interaction:

  https://www.hl7.org/fhir/http.html#history
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.handler.fhir.history.test-util :as history-test-util]
    [blaze.handler.fhir.history-instance :refer [handler]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [datomic-spec.test :as dst]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (dst/instrument)
  (st/instrument
    [`handler]
    {:spec
     {`handler
      (s/fspec
        :args (s/cat :base-uri string? :conn #{::conn}))}})
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(use-fixtures :each fixture)


(def base-uri "http://localhost:8080")


(deftest handler-test
  (testing "Returns Not Found on Non-Existing Resource"
    (datomic-test-util/stub-db ::conn ::db)
    (datomic-test-util/stub-resource ::db #{"Patient"} #{"0"} nil?)

    (let [{:keys [status body]}
          @((handler base-uri ::conn)
            {:path-params {:type "Patient" :id "0"}})]

      (is (= 404 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "error" (-> body :issue first :severity)))

      (is (= "not-found" (-> body :issue first :code)))))

  (testing "Returns History with one Patient"
    (let [patient {:instance/version ::foo}]
      (datomic-test-util/stub-db ::conn ::db)
      (datomic-test-util/stub-resource ::db #{"Patient"} #{"0"} #{{:db/id 0}})
      (datomic-test-util/stub-instance-transaction-history ::db 0 [::tx])
      (datomic-test-util/stub-entity ::db #{0} #{patient})
      (datomic-test-util/stub-ordinal-version patient 1)
      (history-test-util/stub-build-entry base-uri ::db ::tx 0 ::entry))

    (let [{:keys [status body]}
          @((handler base-uri ::conn)
            {:path-params {:type "Patient" :id "0"}})]

      (is (= 200 status))

      (is (= "Bundle" (:resourceType body)))

      (is (= "history" (:type body)))

      (is (= 1 (:total body)))

      (is (= 1 (count (:entry body))))

      (is (= ::entry (-> body :entry first))))))
