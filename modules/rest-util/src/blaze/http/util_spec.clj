(ns blaze.http.util-spec
  (:require
   [blaze.handler.fhir.util.spec]
   [blaze.http.util :as hu]
   [blaze.http.util.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef hu/parse-header-value
  :args (s/cat :s (s/nilable string?))
  :ret (s/nilable (s/coll-of :blaze.http.header/element)))

(s/fdef hu/form-decode
  :args (s/cat :s string? :encoding (s/? string?))
  :ret (s/or :params :ring.request/query-params :anomaly ::anom/anomaly))
