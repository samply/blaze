(ns blaze.interaction.search-type-test
  "Specifications relevant for the FHIR search interaction:

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.interaction.search-type :refer [handler]]
    [blaze.interaction.test-util :as test-util]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [datomic.api :as d]
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
  (datomic-test-util/stub-db ::conn ::db)
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest handler-test
  (testing "Returns Existing all Resources of Type"
    (let [patient {"resourceType" "Patient" "id" "0"}]
      (st/instrument
        [`d/datoms]
        {:spec
         {`d/datoms
          (s/fspec
            :args (s/cat :db #{::db} :index #{:aevt} :attr #{:Patient/id})
            :ret #{[{:e 143757}]})}
         :stub
         #{`d/datoms}})
      (datomic-test-util/stub-entity ::db #{143757} #{::patient})
      (datomic-test-util/stub-pull-resource* ::db "Patient" ::patient #{patient})
      (datomic-test-util/stub-type-total ::db "Patient" 1)
      (test-util/stub-instance-url ::router "Patient" "0" ::patient-url)

      (let [{:keys [status body]}
            @((handler ::conn)
              {::reitit/router ::router
               :path-params {:type "Patient"}})]

        (is (= 200 status))

        (testing "Body contains a bundle"
          (is (= "Bundle" (:resourceType body))))

        (testing "Bundle type is searchset"
          (is (= "searchset" (:type body))))

        (testing "total is 1"
          (is (= 1 (:total body))))

        (testing "contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "The entry has the right fullUrl"
          (is (= ::patient-url (-> body :entry first :fullUrl))))

        (testing "The entry has the right resource"
          (is (= patient (-> body :entry first :resource)))))))

  (testing "Summary Count"
    (datomic-test-util/stub-type-total ::db "Patient" 42)

    (let [{:keys [status body]}
          @((handler ::conn)
            {:path-params {:type "Patient"}
             :params {"_summary" "count"}})]

      (is (= 200 status))

      (testing "Body contains a bundle"
        (is (= "Bundle" (:resourceType body))))

      (testing "Bundle type is searchset"
        (is (= "searchset" (:type body))))

      (testing "total is 42"
        (is (= 42 (:total body))))))

  (testing "Count Zero (equal to Summary Count)"
    (datomic-test-util/stub-type-total ::db "Patient" 23)

    (let [{:keys [status body]}
          @((handler ::conn)
            {:path-params {:type "Patient"}
             :params {"_count" "0"}})]

      (is (= 200 status))

      (testing "Body contains a bundle"
        (is (= "Bundle" (:resourceType body))))

      (testing "Bundle type is searchset"
        (is (= "searchset" (:type body))))

      (testing "total is 42"
        (is (= 23 (:total body))))))


  (testing "Identifier search"
    (let [patient {"resourceType" "Patient" "id" "0"}]
      (st/instrument
        [`d/datoms]
        {:spec
         {`d/datoms
          (s/fspec
            :args (s/cat :db #{::db} :index #{:aevt} :attr #{:Patient/id})
            :ret #{[{:e 143757}]})}
         :stub
         #{`d/datoms}})
      (datomic-test-util/stub-entity ::db #{143757} #{::patient})
      (datomic-test-util/stub-pull-resource* ::db "Patient" ::patient #{patient})
      (test-util/stub-instance-url ::router "Patient" "0" ::patient-url)

      (let [{:keys [status body]}
            @((handler ::conn)
              {::reitit/router ::router
               :path-params {:type "Patient"}})]

        (is (= 200 status))

        (testing "Body contains a bundle"
          (is (= "Bundle" (:resourceType body))))

        (testing "Bundle type is searchset"
          (is (= "searchset" (:type body))))

        (testing "contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "The entry has the right fullUrl"
          (is (= ::patient-url (-> body :entry first :fullUrl))))

        (testing "The entry has the right resource"
          (is (= patient (-> body :entry first :resource))))))))
