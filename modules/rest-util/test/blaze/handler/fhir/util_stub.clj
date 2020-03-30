(ns blaze.handler.fhir.util-stub
  (:require
    [blaze.handler.fhir.util :as util]
    [blaze.handler.fhir.util-spec]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]))


(defn versioned-instance-url [router type id vid url]
  (st/instrument
    [`util/versioned-instance-url]
    {:spec
     {`util/versioned-instance-url
      (s/fspec
        :args (s/cat :router #{router} :type #{type} :id #{id} :vid #{vid})
        :ret #{url})}
     :stub
     #{`util/versioned-instance-url}}))
