(ns blaze.rest-api.middleware.uri-test
  (:require
   [blaze.module.test-util.ring :refer [call]]
   [blaze.rest-api.middleware.uri :refer [wrap-decode-dollar]]
   [blaze.rest-api.middleware.uri-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- echo-uri-handler [{:keys [uri]} respond _]
  (respond uri))

(def ^:private handler
  (wrap-decode-dollar echo-uri-handler))

(deftest wrap-decode-dollar-test
  (testing "decodes the operation marker"
    (are [uri decoded-uri] (= decoded-uri (call handler {:uri uri}))
      "/%24totals" "/$totals"
      "/Measure/%24evaluate-measure" "/Measure/$evaluate-measure"
      "/Measure/0/%24evaluate-measure" "/Measure/0/$evaluate-measure"
      "/Patient/ABC/%24everything" "/Patient/ABC/$everything"
      "/fhir/Patient/ABC/%24everything" "/fhir/Patient/ABC/$everything"))

  (testing "leaves other URIs unchanged"
    (are [uri] (= uri (call handler {:uri uri}))
      ""
      "/"
      "/metadata"
      "/Patient/0/$everything"
      "/Patient/0/__everything-page/AAAA")

    (testing "with %24 not at the start of a path segment"
      (is (= "/Patient/0/__page/a%24b"
             (call handler {:uri "/Patient/0/__page/a%24b"}))))

    (testing "with other percent encodings"
      (is (= "/Patient/a%2Fb" (call handler {:uri "/Patient/a%2Fb"}))))))
