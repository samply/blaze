(ns blaze.http.util-spec
  (:require
    [blaze.http.util :as hu]
    [blaze.http.util.spec]
    [clojure.spec.alpha :as s]))


(s/fdef hu/parse-header-value
  :args (s/cat :s (s/nilable string?))
  :ret (s/nilable (s/coll-of :blaze.http.header/element)))
