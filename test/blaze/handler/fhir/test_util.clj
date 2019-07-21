(ns blaze.handler.fhir.test-util
  (:require
    [blaze.handler.fhir.util :as util]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]))


(defn stub-upsert-resource [conn db creation-mode resource tx-result]
  (st/instrument
    [`util/upsert-resource]
    {:spec
     {`util/upsert-resource
      (s/fspec
        :args (s/cat :conn #{conn} :db #{db} :creation-mode #{creation-mode}
                     :resource #{resource})
        :ret #{tx-result})}
     :stub
     #{`util/upsert-resource}}))


(defn stub-delete-resource [conn db type id tx-result]
  (st/instrument
    [`util/delete-resource]
    {:spec
     {`util/delete-resource
      (s/fspec
        :args (s/cat :conn #{conn} :db #{db} :type #{type} :id #{id})
        :ret #{tx-result})}
     :stub
     #{`util/delete-resource}}))


(defn stub-page-size [query-params page-size]
  (st/instrument
    [`util/page-size]
    {:spec
     {`util/page-size
      (s/fspec
        :args (s/cat :query-params #{query-params})
        :ret #{page-size})}
     :stub
     #{`util/page-size}}))


(defn stub-t [query-params t-spec]
  (st/instrument
    [`util/t]
    {:spec
     {`util/t
      (s/fspec
        :args (s/cat :query-params #{query-params})
        :ret t-spec)}
     :stub
     #{`util/t}}))


(defn stub-type-url [router type url]
  (st/instrument
    [`util/type-url]
    {:spec
     {`util/type-url
      (s/fspec
        :args (s/cat :router #{router} :type #{type})
        :ret #{url})}
     :stub
     #{`util/type-url}}))


(defn stub-instance-url [router type id url]
  (st/instrument
    [`util/instance-url]
    {:spec
     {`util/instance-url
      (s/fspec
        :args (s/cat :router #{router} :type #{type} :id #{id})
        :ret #{url})}
     :stub
     #{`util/instance-url}}))


(defn stub-versioned-instance-url [router type id vid url]
  (st/instrument
    [`util/versioned-instance-url]
    {:spec
     {`util/versioned-instance-url
      (s/fspec
        :args (s/cat :router #{router} :type #{type} :id #{id} :vid #{vid})
        :ret #{url})}
     :stub
     #{`util/versioned-instance-url}}))
