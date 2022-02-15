(ns blaze.rest-api.routes-spec
  (:require
    [blaze.rest-api.routes :as routes]
    [blaze.rest-api.spec]
    [clojure.spec.alpha :as s]))


(s/fdef routes/resource-route
  :args (s/cat :context map?
               :resource-patterns :blaze.rest-api/resource-patterns
               :structure-definition map?))


(s/fdef routes/compartment-route
  :args (s/cat :context map? :compartment :blaze.rest-api/compartment))
