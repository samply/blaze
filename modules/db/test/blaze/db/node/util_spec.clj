(ns blaze.db.node.util-spec
  (:require
   [blaze.db.node.util :as node-util]
   [blaze.db.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(s/fdef node-util/rs-key
  :args (s/cat :resource-handle :blaze.db/resource-handle
               :variant :blaze.resource/variant))
