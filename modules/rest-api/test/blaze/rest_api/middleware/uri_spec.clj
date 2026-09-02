(ns blaze.rest-api.middleware.uri-spec
  (:require
   [blaze.rest-api.middleware.uri :as uri]
   [clojure.spec.alpha :as s]))

(s/fdef uri/wrap-decode-dollar
  :args (s/cat :handler fn?)
  :ret fn?)
