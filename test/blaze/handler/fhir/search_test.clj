(ns blaze.handler.fhir.search-test
  "Specifications relevant for the FHIR search interaction:

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.util :as util]
    [blaze.handler.fhir.search :refer [handler-intern]]
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
    [`d/db]
    {:spec
     {`d/db
      (s/fspec
        :args (s/cat :conn #{::conn})
        :ret #{::db})}
     :stub
     #{`d/db}})
  (f)
  (st/unstrument))


(use-fixtures :each fixture)


(def base-uri "http://localhost:8080")


(deftest handler-test
  (testing "Returns Not Found on Non-Existing Resource Type"
    (st/instrument
      [`util/cached-entity]
      {:spec
       {`util/cached-entity
        (s/fspec
          :args (s/cat :db #{::db} :eid #{:Patient})
          :ret nil?)}
       :stub
       #{`util/cached-entity}})

    (let [{:keys [status body]}
          ((handler-intern base-uri ::conn)
            {:route-params {:type "Patient"}})]

      (is (= 404 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "error" (-> body :issue first :severity)))

      (is (= "not-found" (-> body :issue first :code)))))


  (testing "Returns Existing all Resources of Type"
    (let [patient {"resourceType" "Patient" "id" "0"}]
      (st/instrument
        [`d/datoms
         `util/cached-entity
         `pull/pull-resource]
        {:spec
         {`d/datoms
          (s/fspec
            :args (s/cat :db #{::db} :index #{:aevt} :attr #{:Patient/id})
            :ret #{[{:v "0"}]})
          `util/cached-entity
          (s/fspec
            :args (s/cat :db #{::db} :eid #{:Patient})
            :ret #{::patient-type})
          `pull/pull-resource
          (s/fspec
            :args (s/cat :db #{::db} :type #{"Patient"} :id #{"0"})
            :ret #{patient})}
         :stub
         #{`d/datoms
           `util/cached-entity
           `pull/pull-resource}})

      (let [{:keys [status body]}
            ((handler-intern base-uri ::conn)
              {:route-params {:type "Patient"}})]

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
