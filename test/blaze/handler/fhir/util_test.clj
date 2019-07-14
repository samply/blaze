(ns blaze.handler.fhir.util-test
  (:require
    [blaze.handler.fhir.util :refer :all]
    [blaze.handler.test-util :as test-util]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]))


(deftest type-url-test
  (st/instrument
    [`type-url]
    {:spec
     {`type-url
      (s/fspec
        :args (s/cat :router #{::router} :type #{::type}))}})
  (test-util/stub-match-by-name
    ::router :fhir/type {:type ::type}
    {:data {:blaze/base-url "base-url"} :path "path"})

  (is (= "base-url/path" (type-url ::router ::type))))


(deftest instance-url-test
  (st/instrument
    [`instance-url]
    {:spec
     {`instance-url
      (s/fspec
        :args (s/cat :router #{::router} :type #{::type} :id #{::id}))}})
  (test-util/stub-match-by-name
    ::router :fhir/instance {:type ::type :id ::id}
    {:data {:blaze/base-url "base-url"} :path "path"})

  (is (= "base-url/path" (instance-url ::router ::type ::id))))


(deftest versioned-instance-url-test
  (st/instrument
    [`versioned-instance-url]
    {:spec
     {`versioned-instance-url
      (s/fspec
        :args (s/cat :router #{::router} :type #{::type} :id #{::id} :vid #{::vid}))}})
  (test-util/stub-match-by-name
    ::router :fhir/versioned-instance {:type ::type :id ::id :vid ::vid}
    {:data {:blaze/base-url "base-url"} :path "path"})

  (is (= "base-url/path" (versioned-instance-url ::router ::type ::id ::vid))))
