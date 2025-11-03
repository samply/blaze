(ns blaze.db.node.util-spec
  (:require
   [blaze.db.node.util :as node-util]
   [blaze.db.resource-store :as rs]
   [blaze.db.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(s/fdef node-util/rs-key
  :args (s/cat :resource-handle :blaze.db/resource-handle
               :variant :blaze.resource/variant)
  :ret ::rs/key)
