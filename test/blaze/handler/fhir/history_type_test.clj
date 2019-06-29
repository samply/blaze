(ns blaze.handler.fhir.history-type-test
  "Specifications relevant for the FHIR history interaction:

  https://www.hl7.org/fhir/http.html#history
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.handler.fhir.history.test-util :as history-test-util]
    [blaze.handler.fhir.history-type :refer [handler]]
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
  (testing "Returns History with one Patient"
    (let [patient {:db/id 0}
          tx {:tx/resources [patient]}]
      (datomic-test-util/stub-db ::conn ::db)
      (datomic-test-util/stub-type-transaction-history ::db "Patient" [tx])
      (datomic-test-util/stub-entity ::db #{:Patient} #{{:type/version -1}})
      (datomic-test-util/stub-resource-type patient "Patient")
      (history-test-util/stub-build-entry base-uri ::db tx 0 ::entry))

    (let [{:keys [status body]}
          @((handler base-uri ::conn)
            {:path-params {:type "Patient"}})]

      (is (= 200 status))

      (is (= "Bundle" (:resourceType body)))

      (is (= "history" (:type body)))

      (is (= 1 (:total body)))

      (is (= 1 (count (:entry body))))

      (is (= ::entry (-> body :entry first))))))
