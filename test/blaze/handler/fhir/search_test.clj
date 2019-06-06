(ns blaze.handler.fhir.search-test
  "Specifications relevant for the FHIR search interaction:

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.handler.fhir.search :refer [handler]]
    [blaze.handler.fhir.test-util :as test-util]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [datomic.api :as d]
    [datomic-spec.test :as dst]))


(st/instrument)
(dst/instrument)


(defn fixture [f]
  (st/instrument)
  (dst/instrument)
  (st/instrument
    [`handler]
    {:spec
     {`handler
      (s/fspec
        :args (s/cat :base-uri string? :conn #{::conn}))}})
  (test-util/stub-db ::conn ::db)
  (f)
  (st/unstrument))


(use-fixtures :each fixture)


(def base-uri "http://localhost:8080")


(deftest handler-test
  (testing "Returns Not Found on Non-Existing Resource Type"
    (test-util/stub-cached-entity ::db #{:Patient} nil?)

    (let [{:keys [status body]}
          @((handler base-uri ::conn)
            {:path-params {:type "Patient"}})]

      (is (= 404 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "error" (-> body :issue first :severity)))

      (is (= "not-found" (-> body :issue first :code)))))


  (testing "Returns Existing all Resources of Type"
    (let [patient {"resourceType" "Patient" "id" "0"}]
      (test-util/stub-cached-entity ::db #{:Patient} some?)
      (st/instrument
        [`d/datoms]
        {:spec
         {`d/datoms
          (s/fspec
            :args (s/cat :db #{::db} :index #{:aevt} :attr #{:Patient/id})
            :ret #{[{:e 143757}]})}
         :stub
         #{`d/datoms}})
      (test-util/stub-entity ::db #{143757} #{::patient})
      (test-util/stub-pull-resource* ::db "Patient" ::patient #{patient})

      (let [{:keys [status body]}
            @((handler base-uri ::conn)
              {:path-params {:type "Patient"}})]

        (is (= 200 status))

        (testing "Body contains a bundle"
          (is (= "Bundle" (:resourceType body))))

        (testing "Bundle type is searchset"
          (is (= "searchset" (:type body))))

        (testing "contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "The entry has the right fullUrl"
          (is (= (str base-uri "/fhir/Patient/0") (-> body :entry first :fullUrl))))

        (testing "The entry has the right resource"
          (is (= patient (-> body :entry first :resource)))))))


  (testing "Identifier search"
    (let [patient {"resourceType" "Patient" "id" "0"}]
      (test-util/stub-cached-entity ::db #{:Patient} some?)
      (st/instrument
        [`d/datoms]
        {:spec
         {`d/datoms
          (s/fspec
            :args (s/cat :db #{::db} :index #{:aevt} :attr #{:Patient/id})
            :ret #{[{:e 143757}]})}
         :stub
         #{`d/datoms}})
      (test-util/stub-entity ::db #{143757} #{::patient})
      (test-util/stub-pull-resource* ::db "Patient" ::patient #{patient})

      (let [{:keys [status body]}
            @((handler base-uri ::conn)
              {:path-params {:type "Patient"}})]

        (is (= 200 status))

        (testing "Body contains a bundle"
          (is (= "Bundle" (:resourceType body))))

        (testing "Bundle type is searchset"
          (is (= "searchset" (:type body))))

        (testing "contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "The entry has the right fullUrl"
          (is (= (str base-uri "/fhir/Patient/0") (-> body :entry first :fullUrl))))

        (testing "The entry has the right resource"
          (is (= patient (-> body :entry first :resource))))))))
