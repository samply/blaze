(ns blaze.middleware.fhir.id-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.middleware.fhir.id :refer [wrap-invalid-id]]
   [blaze.middleware.fhir.id-spec]
   [blaze.test-util :as tu :refer [given-failed-future]]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def handler
  (wrap-invalid-id ac/completed-future))

(deftest wrap-invalid-id-test
  (testing "valid ids are passed through"
    (doseq [id ["0" "a-Z.9" (str/join (repeat 64 "a"))]]
      (let [request {:path-params {:id id}}]
        (is (= request @(handler request))))))

  (testing "id with invalid character"
    (given-failed-future (handler {:path-params {:id "A_B"}})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Resource id `A_B` is invalid."
      :fhir/issue := "value"
      :fhir/operation-outcome := "MSG_ID_INVALID"))

  (testing "too long id"
    (let [id (str/join (repeat 65 "a"))]
      (given-failed-future (handler {:path-params {:id id}})
        ::anom/category := ::anom/incorrect
        ::anom/message := (format "Resource id `%s` is invalid." id)
        :fhir/issue := "value"
        :fhir/operation-outcome := "MSG_ID_INVALID")))

  (testing "empty id"
    (given-failed-future (handler {:path-params {:id ""}})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Resource id `` is invalid."
      :fhir/operation-outcome := "MSG_ID_INVALID")))
