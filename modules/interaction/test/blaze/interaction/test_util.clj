(ns blaze.interaction.test-util
  (:require
    [blaze.handler.util :as util]
    [blaze.handler.fhir.util :as fhir-util]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]))


(defn stub-instance-url [router type id url]
  (st/instrument
    [`fhir-util/instance-url]
    {:spec
     {`fhir-util/instance-url
      (s/fspec
        :args (s/cat :router #{router} :type #{type} :id #{id})
        :ret #{url})}
     :stub
     #{`fhir-util/instance-url}}))


(defn stub-instance-url-fn [router type id-spec replace-fn]
  (st/instrument
    [`fhir-util/instance-url]
    {:spec
     {`fhir-util/instance-url
      (s/fspec
        :args (s/cat :router #{router} :type #{type} :id id-spec))}
     :replace
     {`fhir-util/instance-url replace-fn}}))


(defn stub-page-size [query-params page-size]
  (st/instrument
    [`fhir-util/page-size]
    {:spec
     {`fhir-util/page-size
      (s/fspec
        :args (s/cat :query-params #{query-params})
        :ret #{page-size})}
     :stub
     #{`fhir-util/page-size}}))


(defn stub-t [query-params t-spec]
  (st/instrument
    [`fhir-util/t]
    {:spec
     {`fhir-util/t
      (s/fspec
        :args (s/cat :query-params #{query-params})
        :ret t-spec)}
     :stub
     #{`fhir-util/t}}))


(defn stub-db [conn t-spec db]
  (st/instrument
    [`util/db]
    {:spec
     {`util/db
      (s/fspec
        :args (s/cat :conn #{conn} :t t-spec)
        :ret #{db})}
     :stub
     #{`util/db}}))


(defn stub-upsert-resource
  [transaction-executor conn term-service db creation-mode resource tx-result]
  (st/instrument
    [`fhir-util/upsert-resource]
    {:spec
     {`fhir-util/upsert-resource
      (s/fspec
        :args
        (s/cat
          :transaction-executor #{transaction-executor}
          :conn #{conn}
          :term-service #{term-service}
          :db #{db}
          :creation-mode #{creation-mode}
          :resource #{resource})
        :ret #{tx-result})}
     :stub
     #{`fhir-util/upsert-resource}}))


(defn stub-versioned-instance-url [router type id vid url]
  (st/instrument
    [`fhir-util/versioned-instance-url]
    {:spec
     {`fhir-util/versioned-instance-url
      (s/fspec
        :args (s/cat :router #{router} :type #{type} :id #{id} :vid #{vid})
        :ret #{url})}
     :stub
     #{`fhir-util/versioned-instance-url}}))
