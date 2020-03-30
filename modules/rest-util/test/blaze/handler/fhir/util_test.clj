(ns blaze.handler.fhir.util-test
  (:require
    [blaze.handler.fhir.util :refer [type-url instance-url versioned-instance-url]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is]]
    [reitit.core :as reitit]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn- stub-match-by-name
  ([router name match]
   (st/instrument
     [`reitit/match-by-name]
     {:spec
      {`reitit/match-by-name
       (s/fspec
         :args (s/cat :router #{router} :name #{name})
         :ret #{match})}
      :stub
      #{`reitit/match-by-name}}))
  ([router name params match]
   (st/instrument
     [`reitit/match-by-name]
     {:spec
      {`reitit/match-by-name
       (s/fspec
         :args (s/cat :router #{router} :name #{name}
                      :params #{params})
         :ret #{match})}
      :stub
      #{`reitit/match-by-name}})))


(deftest type-url-test
  (st/unstrument `type-url)
  (stub-match-by-name
    ::router :type-105536/type
    {:data {:blaze/base-url "base-url"} :path "/path"})

  (is (= "base-url/path" (type-url ::router "type-105536"))))


(deftest instance-url-test
  (st/unstrument `instance-url)
  (stub-match-by-name
    ::router :type-105349/instance {:id ::id}
    {:data {:blaze/base-url "base-url"} :path "/path"})

  (is (= "base-url/path" (instance-url ::router "type-105349" ::id))))


(deftest versioned-instance-url-test
  (st/unstrument `versioned-instance-url)
  (stub-match-by-name
    ::router :type-105253/versioned-instance {:id ::id :vid ::vid}
    {:data {:blaze/base-url "base-url"} :path "/path"})

  (is
    (= "base-url/path"
       (versioned-instance-url ::router "type-105253" ::id ::vid))))
