(ns blaze.rest-api.util-spec
  (:require
   [blaze.rest-api.spec]
   [blaze.rest-api.util :as u]
   [clojure.spec.alpha :as s]))

(s/fdef u/supports-resource-type?
  :args (s/cat :operation :blaze.rest-api/operation :type string?)
  :ret boolean?)

(s/fdef u/all-resource-types?
  :args (s/cat :operation :blaze.rest-api/operation)
  :ret boolean?)
