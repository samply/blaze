(ns blaze.middleware.non-fhir.output-test
  (:require
   [blaze.fhir.spec-spec]
   [blaze.fhir.test-util]
   [blaze.middleware.non-fhir.output :refer [wrap-output]]
   [blaze.module.test-util.ring :refer [call]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [juxt.iota :refer [given]]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [com.google.common.base CaseFormat]))

(set! *warn-on-reflection* true)

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn- json-handler [opts]
  ((wrap-output opts)
   (fn [_ respond _]
     (respond (ring/response {:foo-bar 42})))))

(defn- camel [s]
  (.to CaseFormat/LOWER_HYPHEN CaseFormat/LOWER_CAMEL s))

(deftest wrap-output-test
  (testing "without options"
    (given (call (json-handler nil) {})
      :status := 200
      [:headers "Content-Type"] := "application/json;charset=utf-8"
      [:body #(String. ^bytes %)] := "{\"foo-bar\":42}"))

  (testing "with options"
    (given (call (json-handler {:encode-key-fn (comp camel name)}) {})
      :status := 200
      [:headers "Content-Type"] := "application/json;charset=utf-8"
      [:body #(String. ^bytes %)] := "{\"fooBar\":42}")))
