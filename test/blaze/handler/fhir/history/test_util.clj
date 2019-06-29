(ns blaze.handler.fhir.history.test-util
  (:require
    [blaze.handler.fhir.history.util :as util]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]))


(defn stub-build-entry [base-uri db transaction resource-eid result]
  (st/instrument
    [`util/build-entry]
    {:spec
     {`util/build-entry
      (s/fspec
        :args (s/cat :base-uri #{base-uri} :db #{db} :transaction #{transaction}
                     :resource-eid #{resource-eid})
        :ret #{result})}
     :stub
     #{`util/build-entry}}))
