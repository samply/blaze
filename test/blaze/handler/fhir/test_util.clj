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


(defn stub-page-size [query-params result]
  (st/instrument
    [`util/page-size]
    {:spec
     {`util/page-size
      (s/fspec
        :args (s/cat :query-params #{query-params})
        :ret #{result})}
     :stub
     #{`util/page-size}}))
