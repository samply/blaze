(ns blaze.interaction.history.type-test
  "Specifications relevant for the FHIR history interaction:

  https://www.hl7.org/fhir/http.html#history
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.interaction.history.test-util :as history-test-util]
    [blaze.interaction.history.type :refer [handler]]
    [blaze.interaction.test-util :as test-util]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [datomic-spec.test :as dst]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (dst/instrument)
  (st/instrument
    [`handler]
    {:spec
     {`handler
      (s/fspec
        :args (s/cat :conn #{::conn}))}})
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest handler-test
  (testing "Returns History with one Patient"
    (let [tx {:db/id ::tx-eid}]
      (test-util/stub-t ::query-params nil?)
      (test-util/stub-db ::conn nil? ::db)
      (history-test-util/stub-page-t ::query-params nil?)
      (history-test-util/stub-since-t ::db ::query-params nil?)
      (history-test-util/stub-tx-db ::db nil? nil? ::db)
      (datomic-test-util/stub-type-transaction-history ::db "Patient" [tx])
      (test-util/stub-page-size ::query-params 50)
      (history-test-util/stub-page-eid ::query-params nil?)
      (datomic-test-util/stub-entity-db #{tx} ::db)
      (datomic-test-util/stub-datoms
        ::db :eavt (s/cat :e #{::tx-eid} :a #{:tx/resources} :v nil?)
        (constantly [{:v ::patient-eid}]))
      (datomic-test-util/stub-as-of-t ::db nil?)
      (datomic-test-util/stub-basis-t ::db 152026)
      (datomic-test-util/stub-type-version ::db "Patient" 1)
      (datomic-test-util/stub-resource-type* ::db ::patient-eid "Patient")
      (history-test-util/stub-nav-link
        ::match ::query-params 152026 tx #{::patient-eid}
        (constantly ::self-link-url))
      (history-test-util/stub-build-entry
        ::router ::db #{tx} #{::patient-eid} (constantly ::entry)))

    (let [{:keys [status body]}
          @((handler ::conn)
            {::reitit/router ::router
             ::reitit/match ::match
             :query-params ::query-params
             :path-params {:type "Patient"}})]

      (is (= 200 status))

      (is (= "Bundle" (:resourceType body)))

      (is (= "history" (:type body)))

      (is (= 1 (:total body)))

      (is (= 1 (count (:entry body))))

      (is (= ::entry (-> body :entry first))))))
