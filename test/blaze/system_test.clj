(ns blaze.system-test
  (:require
    [blaze.db.api-stub :refer [mem-node-system]]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.interaction.search-type]
    [blaze.interaction.transaction]
    [blaze.page-store.protocols :as pp]
    [blaze.rest-api]
    [blaze.system :as system]
    [blaze.system-spec]
    [blaze.test-util :refer [with-system]]
    [buddy.auth.protocols :as ap]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.io ByteArrayInputStream]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest resolve-config-test
  (are [config env res] (= res (system/resolve-config config env))
    {:a (system/->Cfg "SERVER_PORT" (s/spec nat-int?) 8080)}
    {"SERVER_PORT" "80"}
    {:a 80}

    {:a (system/->Cfg "SERVER_PORT" (s/spec nat-int?) 8080)}
    nil
    {:a 8080}

    {:a (system/->Cfg "SERVER_PORT" (s/spec nat-int?) 8080)}
    {"SERVER_PORT" "a"}
    {:a ::s/invalid})

  (testing "Blank env vars are handled same as missing ones"
    (are [config env res] (= res (system/resolve-config config env))
      {:a (system/->Cfg "PROXY_HOST" (s/spec string?) nil)}
      {"PROXY_HOST" ""}
      {:a nil}

      {:a (system/->Cfg "PROXY_HOST" (s/spec string?) nil)}
      {}
      {:a nil}

      {:a (system/->Cfg "PROXY_HOST" (s/spec string?) "default")}
      {"PROXY_HOST" ""}
      {:a "default"}

      {:a (system/->Cfg "PROXY_HOST" (s/spec string?) "default")}
      {}
      {:a "default"})))


(def system
  (assoc mem-node-system
    :blaze/rest-api
    {:base-url "http://localhost:8080"
     :version "0.1.0"
     :structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)
     :node (ig/ref :blaze.db/node)
     :search-param-registry (ig/ref :blaze.db/search-param-registry)
     :db-sync-timeout 10000
     :blaze.rest-api.json-parse/executor (ig/ref :blaze.rest-api.json-parse/executor)
     :auth-backends [(ig/ref ::auth-backend)]
     :transaction-handler (ig/ref :blaze.interaction/transaction)
     :resource-patterns
     [#:blaze.rest-api.resource-pattern
         {:type :default
          :interactions
          {:search-type
           #:blaze.rest-api.interaction
               {:handler (ig/ref :blaze.interaction/search-type)}}}]}
    :blaze.db/search-param-registry
    {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}
    :blaze.fhir/structure-definition-repo {}
    :blaze.rest-api.json-parse/executor {}
    ::auth-backend {}
    :blaze.interaction/transaction
    {:node (ig/ref :blaze.db/node)
     :executor (ig/ref :blaze.test/executor)
     :clock (ig/ref :blaze.test/clock)
     :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
    :blaze.interaction/search-type
    {:clock (ig/ref :blaze.test/clock)
     :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
     :page-store (ig/ref ::page-store)}
    :blaze.test/executor {}
    :blaze.test/clock {}
    :blaze.test/fixed-rng-fn {}
    ::page-store {}))


(defmethod ig/init-key ::auth-backend
  [_ _]
  (reify ap/IAuthentication
    (-parse [_ _] ::token)
    (-authenticate [_ _ data] (when (= ::token data) ::identity))))


(defmethod ig/init-key ::page-store
  [_ _]
  (reify pp/PageStore
    (-get [_ _])
    (-put [_ _])))


(defn input-stream [bs]
  (ByteArrayInputStream. bs))


(def bundle
  {:fhir/type :fhir/Bundle
   :type #fhir/code "batch"
   :entry
   [{:fhir/type :fhir.Bundle/entry
     :request
     {:fhir/type :fhir.Bundle.entry/request
      :method #fhir/code"GET"
      :url #fhir/uri"/Patient"}}]})


(deftest auth-test
  (log/set-level! :debug)
  (with-system [{:blaze/keys [rest-api]} system]
    (testing "Patient search"
      (given @(rest-api {:request-method :get :uri "/Patient"})
        :status := 200))

    (testing "Patient search as batch request"
      (given @(rest-api {:request-method :post
                         :uri ""
                         :headers {"content-type" "application/fhir+json"}
                         :body (input-stream (fhir-spec/unform-json bundle))})
        :status := 200
        [:body fhir-spec/parse-json :entry 0 :response :status] := "200"))))
