(ns blaze.middleware.params-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.middleware.params :as params]
   [blaze.middleware.params-spec]
   [blaze.test-util :as tu :refer [given-failed-future]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private handler
  (params/wrap-params identity))

(def ^:private form-headers
  {"content-type" "application/x-www-form-urlencoded"})

(deftest wrap-params-test
  (testing "without query string and body"
    (given (handler {})
      :query-params := {}
      :form-params := {}
      :params := {}))

  (testing "with query string"
    (given (handler {:query-string "a=b&c=d%20e"})
      :query-params := {"a" "b" "c" "d e"}
      :form-params := {}
      :params := {"a" "b" "c" "d e"}))

  (testing "with form body"
    (given (handler {:headers form-headers :body (.getBytes "a=b")})
      :query-params := {}
      :form-params := {"a" "b"}
      :params := {"a" "b"}))

  (testing "with form body in ISO-8859-1"
    (given (handler {:headers {"content-type" "application/x-www-form-urlencoded; charset=ISO-8859-1"}
                     :body (.getBytes "a=%E4")})
      :form-params := {"a" "ä"}))

  (testing "a body that isn't a form is ignored"
    (given (handler {:headers {"content-type" "application/fhir+json"}
                     :body (.getBytes "a=b")})
      :form-params := {}
      :params := {}))

  (testing "query params take precedence over form params"
    (given (handler {:query-string "a=b" :headers form-headers
                     :body (.getBytes "a=c&d=e")})
      :query-params := {"a" "b"}
      :form-params := {"a" "c" "d" "e"}
      :params := {"a" "b" "d" "e"}))

  (testing "with invalid percent-encoding"
    (testing "in the query string"
      (given (handler {:query-string "a=b&identifier=%ZZ"})
        [::params/anomaly ::anom/category] := ::anom/incorrect
        [::params/anomaly ::anom/message] := "Invalid percent-encoding in parameter `identifier=%ZZ`."
        [::params/anomaly :fhir/issue] := "invalid"
        :query-params := nil
        :form-params := nil
        :params := nil))

    (testing "in the form body"
      (given (handler {:headers form-headers :body (.getBytes "identifier=%ZZ")})
        [::params/anomaly ::anom/category] := ::anom/incorrect
        [::params/anomaly ::anom/message] := "Invalid percent-encoding in parameter `identifier=%ZZ`."
        :query-params := nil
        :form-params := nil
        :params := nil)))

  (testing "async handler"
    (let [handler (params/wrap-params (fn [request respond _] (respond request)))
          response (promise)]
      (handler {:query-string "a=b"} response identity)
      (given @response
        :query-params := {"a" "b"}
        :params := {"a" "b"}))))

(deftest wrap-invalid-params-test
  (let [handler (params/wrap-invalid-params ac/completed-future)]
    (testing "a request without anomaly is passed through"
      (given @(handler {:query-params {"a" "b"}})
        :query-params := {"a" "b"}))

    (testing "a request with anomaly completes with that anomaly"
      (given-failed-future (handler {::params/anomaly (ba/incorrect "msg-104412")})
        ::anom/category := ::anom/incorrect
        ::anom/message := "msg-104412")))

  (testing "together with wrap-params"
    (let [handler (-> ac/completed-future params/wrap-invalid-params
                      params/wrap-params)]
      (testing "valid params"
        (given @(handler {:query-string "a=b"})
          :query-params := {"a" "b"}))

      (testing "invalid percent-encoding"
        (given-failed-future (handler {:query-string "identifier=%ZZ"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid percent-encoding in parameter `identifier=%ZZ`."
          :fhir/issue := "invalid")))))
