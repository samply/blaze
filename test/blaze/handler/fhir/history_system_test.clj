(ns blaze.handler.fhir.history-system-test
  "Specifications relevant for the FHIR history interaction:

  https://www.hl7.org/fhir/http.html#history
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.handler.fhir.history.test-util :as history-test-util]
    [blaze.handler.fhir.test-util :as fhir-test-util]
    [blaze.handler.fhir.history-system :refer [handler]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
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
        :args (s/cat :base-uri string? :conn #{::conn}))}})
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(use-fixtures :each fixture)


(def base-uri "http://localhost:8080")


(deftest handler-test-1
  (testing "Returns History with one Patient"
    (let [tx {:db/id ::tx-eid}]
      (history-test-util/stub-page-t ::query-params nil?)
      (history-test-util/stub-page-eid ::query-params nil?)
      (fhir-test-util/stub-page-size ::query-params 50)
      (datomic-test-util/stub-db ::conn ::db)
      (datomic-test-util/stub-system-transaction-history ::db [tx])
      (datomic-test-util/stub-entity-db #{tx} ::db)
      (datomic-test-util/stub-datoms
        ::db :eavt (s/cat :a #{::tx-eid} :b #{:tx/resources})
        (constantly [{:v ::patient-eid}]))
      (datomic-test-util/stub-system-version ::db 1)
      (history-test-util/stub-nav-link
        base-uri ::match ::query-params #{"self"} tx #{::patient-eid}
        (constantly ::self-link))
      (history-test-util/stub-build-entry
        base-uri ::db #{tx} #{::patient-eid} (constantly ::entry)))

    (let [{:keys [status body]}
          @((handler base-uri ::conn)
            {:path-params {:type "Patient"}
             :query-params ::query-params
             ::reitit/match ::match})]

      (is (= 200 status))

      (is (= "Bundle" (:resourceType body)))

      (is (= "history" (:type body)))

      (is (= 1 (:total body)))

      (is (= 1 (count (:entry body))))

      (is (= ::self-link (-> body :link first)))

      (is (= ::entry (-> body :entry first))))))


(deftest handler-test-2
  (testing "Returns History with two Patients in one Transaction"
    (let [tx {:db/id ::tx-eid}]
      (history-test-util/stub-page-t ::query-params nil?)
      (history-test-util/stub-page-eid ::query-params nil?)
      (fhir-test-util/stub-page-size ::query-params 50)
      (datomic-test-util/stub-db ::conn ::db)
      (datomic-test-util/stub-system-transaction-history ::db [tx])
      (datomic-test-util/stub-entity-db #{tx} ::db)
      (datomic-test-util/stub-datoms
        ::db :eavt (s/cat :a #{::tx-eid} :b #{:tx/resources})
        (constantly [{:v ::patient-1-eid} {:v ::patient-2-eid}]))
      (datomic-test-util/stub-system-version ::db 2)
      (history-test-util/stub-nav-link
        base-uri ::match ::query-params #{"self"} tx #{::patient-1-eid}
        (constantly ::self-link))
      (history-test-util/stub-build-entry
        base-uri ::db #{tx} #{::patient-1-eid ::patient-2-eid}
        (fn [_ _ _ resource-eid]
          (case resource-eid
            ::patient-1-eid ::entry-1
            ::patient-2-eid ::entry-2))))

    (let [{:keys [status body]}
          @((handler base-uri ::conn)
            {:path-params {:type "Patient"}
             :query-params ::query-params
             ::reitit/match ::match})]

      (is (= 200 status))

      (is (= "Bundle" (:resourceType body)))

      (is (= "history" (:type body)))

      (is (= 2 (:total body)))

      (is (= 2 (count (:entry body))))

      (is (= ::self-link (-> body :link first)))

      (is (= [::entry-1 ::entry-2] (:entry body))))))


(deftest handler-test-3
  (testing "Returns History with two Patients in two Transaction"
    (let [tx-1 {:db/id ::tx-1-eid}
          tx-2 {:db/id ::tx-2-eid}]
      (history-test-util/stub-page-t ::query-params nil?)
      (history-test-util/stub-page-eid ::query-params nil?)
      (fhir-test-util/stub-page-size ::query-params 50)
      (datomic-test-util/stub-db ::conn ::db)
      (datomic-test-util/stub-system-transaction-history ::db [tx-1 tx-2])
      (datomic-test-util/stub-entity-db #{tx-1 tx-2} ::db)
      (datomic-test-util/stub-datoms
        ::db :eavt (s/cat :a #{::tx-1-eid ::tx-2-eid} :b #{:tx/resources})
        (fn [_ _ tx-eid _]
          (case tx-eid
            ::tx-1-eid [{:v ::patient-1-eid}]
            ::tx-2-eid [{:v ::patient-2-eid}])))
      (datomic-test-util/stub-system-version ::db 2)
      (history-test-util/stub-nav-link
        base-uri ::match ::query-params #{"self"} tx-1 #{::patient-1-eid}
        (constantly ::self-link))
      (history-test-util/stub-build-entry
        base-uri ::db #{tx-1 tx-2} #{::patient-1-eid ::patient-2-eid}
        (fn [_ _ _ resource-eid]
          (case resource-eid
            ::patient-1-eid ::entry-1
            ::patient-2-eid ::entry-2))))

    (let [{:keys [status body]}
          @((handler base-uri ::conn)
            {:path-params {:type "Patient"}
             :query-params ::query-params
             ::reitit/match ::match})]

      (is (= 200 status))

      (is (= "Bundle" (:resourceType body)))

      (is (= "history" (:type body)))

      (is (= 2 (:total body)))

      (is (= 2 (count (:entry body))))

      (is (= ::self-link (-> body :link first)))

      (is (= [::entry-1 ::entry-2] (:entry body))))))


(deftest handler-test-4
  (testing "Returns History with next Link"
    (let [tx {:db/id ::tx-eid}]
      (history-test-util/stub-page-t ::query-params nil?)
      (history-test-util/stub-page-eid ::query-params nil?)
      (fhir-test-util/stub-page-size ::query-params 1)
      (datomic-test-util/stub-db ::conn ::db)
      (datomic-test-util/stub-system-transaction-history ::db [tx])
      (datomic-test-util/stub-entity-db #{tx} ::db)
      (datomic-test-util/stub-datoms
        ::db :eavt (s/cat :a #{::tx-eid} :b #{:tx/resources})
        (constantly [{:v ::patient-1-eid} {:v ::patient-2-eid}]))
      (datomic-test-util/stub-system-version ::db 1)
      (history-test-util/stub-nav-link
        base-uri ::match ::query-params #{"self" "next"} tx
        #{::patient-1-eid ::patient-2-eid}
        (fn [_ _ _ _ [_ resource-eid]]
          (case resource-eid
            ::patient-1-eid ::self-link
            ::patient-2-eid ::next-link)))
      (history-test-util/stub-build-entry
        base-uri ::db #{tx} #{::patient-1-eid} (constantly ::entry))

      (let [{:keys [status body]}
            @((handler base-uri ::conn)
              {:path-params {:type "Patient"}
               :query-params ::query-params
               ::reitit/match ::match})]

        (is (= 200 status))

        (is (= "Bundle" (:resourceType body)))

        (is (= "history" (:type body)))

        (is (= 1 (:total body)))

        (is (= 1 (count (:entry body))))

        (is (= ::self-link (-> body :link first)))

        (is (= ::next-link (-> body :link second)))

        (is (= ::entry (-> body :entry first)))))))
