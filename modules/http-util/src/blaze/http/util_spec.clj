(ns blaze.http.util-spec
  (:require
   [blaze.http.util :as hu]
   [blaze.http.util.spec]
   [clojure.spec.alpha :as s]))

(s/fdef hu/parse-header-value
  :args (s/cat :s (s/nilable string?))
  :ret (s/nilable (s/coll-of :blaze.http.header/element)))

(s/fdef hu/parse-accept
  :args (s/cat :s (s/nilable string?))
  :ret (s/nilable (s/coll-of string?)))

(s/fdef hu/parse-accept-language
  :args (s/cat :s (s/nilable string?))
  :ret (s/nilable (s/coll-of string?)))
