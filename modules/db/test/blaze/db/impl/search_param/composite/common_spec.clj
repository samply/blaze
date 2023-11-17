(ns blaze.db.impl.search-param.composite.common-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.byte-string-spec]
   [blaze.coll.core-spec]
   [blaze.db.impl.search-param.composite.common :as cc]
   [blaze.fhir-path-spec]
   [clojure.spec.alpha :as s]))

(s/fdef cc/split-value
  :args (s/cat :value string?)
  :ret vector?)
