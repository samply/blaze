(ns blaze.handler.fhir.util-test
  (:require
    [blaze.handler.fhir.util :refer :all]
    [blaze.handler.test-util :as test-util]
    [clojure.test :refer :all]))


(deftest versioned-instance-url-test
  (test-util/stub-match-by-name
    ::router :fhir/versioned-instance {:type ::type :id ::id :vid ::vid}
    {:data {:blaze/base-url "base-url"} :path "/path"})

  (is (= "base-url/path" (versioned-instance-url ::router ::type ::id ::vid))))
