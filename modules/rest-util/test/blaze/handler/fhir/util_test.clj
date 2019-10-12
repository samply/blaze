(ns blaze.handler.fhir.util-test
  (:require
    [blaze.datomic.transaction :as tx]
    [blaze.handler.fhir.util
     :refer [upsert-resource type-url instance-url versioned-instance-url]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]
    [manifold.deferred :as md]
    [reitit.core :as reitit]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn- stub-annotate-codes [term-service db resource result]
  (st/instrument
    `tx/annotate-codes
    {:spec
     {`tx/annotate-codes
      (s/fspec
        :args
        (s/cat :term-service #{term-service} :db #{db} :resource #{resource})
        :ret #{result})}
     :stub
     #{`tx/annotate-codes}}))


(defn- stub-resource-codes-creation [db resource result]
  (st/instrument
    `tx/resource-codes-creation
    {:spec
     {`tx/resource-codes-creation
      (s/fspec
        :args (s/cat :db #{db} :resource #{resource})
        :ret #{result})}
     :stub
     #{`tx/resource-codes-creation}}))


(defn- stub-resource-tempid [db resource result-spec]
  (st/instrument
    `tx/resource-tempid
    {:spec
     {`tx/resource-tempid
      (s/fspec
        :args (s/cat :db #{db} :resource #{resource})
        :ret result-spec)}
     :stub
     #{`tx/resource-tempid}}))


(defn- stub-resource-upsert [db tempids-spec creation-mode resource result]
  (st/instrument
    `tx/resource-upsert
    {:spec
     {`tx/resource-upsert
      (s/fspec
        :args
        (s/cat
          :db #{db}
          :tempids tempids-spec
          :creation-mode #{creation-mode}
          :resource #{resource})
        :ret #{result})}
     :stub
     #{`tx/resource-upsert}}))


(deftest upsert-resource-test
  (st/unstrument `upsert-resource)

  (testing "Returns :db-after even when nothing has changed"
    (let [resource
          {"resourceType" "Patient"
           "id" "0"}]
      (stub-annotate-codes
        ::term-service ::db resource (md/success-deferred resource))
      (stub-resource-codes-creation ::db resource [])
      (stub-resource-tempid ::db resource nil?)
      (stub-resource-upsert ::db nil? ::creation-mode resource [])

      (given
        @(upsert-resource
           ::transaction-executor
           ::conn
           ::term-service
           ::db
           ::creation-mode
           resource)
        :db-after := ::db))))


(defn- stub-match-by-name [router name params match]
  (st/instrument
    [`reitit/match-by-name]
    {:spec
     {`reitit/match-by-name
      (s/fspec
        :args (s/cat :router #{router} :name #{name}
                     :params #{params})
        :ret #{match})}
     :stub
     #{`reitit/match-by-name}}))


(deftest type-url-test
  (st/instrument
    [`type-url]
    {:spec
     {`type-url
      (s/fspec
        :args (s/cat :router #{::router} :type #{::type}))}})
  (stub-match-by-name
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
  (stub-match-by-name
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
  (stub-match-by-name
    ::router :fhir/versioned-instance {:type ::type :id ::id :vid ::vid}
    {:data {:blaze/base-url "base-url"} :path "path"})

  (is (= "base-url/path" (versioned-instance-url ::router ::type ::id ::vid))))
