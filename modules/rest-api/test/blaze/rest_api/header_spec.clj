(ns blaze.rest-api.header-spec
  (:require
   [blaze.rest-api.header :as header]
   [clojure.spec.alpha :as s]))

(s/fdef header/if-none-match->tags
  :args (s/cat :value (s/nilable string?))
  :ret (s/coll-of string? :kind set?))
