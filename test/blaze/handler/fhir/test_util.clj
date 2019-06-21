(ns blaze.handler.fhir.test-util
  (:require
    [blaze.handler.fhir.util :as handler-fhir-util]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]))


(defn stub-upsert-resource [conn db creation-mode resource tx-result]
  (st/instrument
    [`handler-fhir-util/upsert-resource]
    {:spec
     {`handler-fhir-util/upsert-resource
      (s/fspec
        :args (s/cat :conn #{conn} :db #{db} :creation-mode #{creation-mode}
                     :resource #{resource})
        :ret #{tx-result})}
     :stub
     #{`handler-fhir-util/upsert-resource}}))
