(ns blaze.db.node.util-spec
  (:require
   [blaze.db.node.util :as node-util]
   [blaze.db.resource-store :as rs]
   [blaze.db.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(s/def ::key
  (s/or :simple qualified-keyword?
        :composite (s/tuple qualified-keyword? qualified-keyword?)))

(s/fdef node-util/node-name
  :args (s/cat :key ::key)
  :ret string?)

(s/fdef node-util/rs-key
  :args (s/cat :resource-handle :blaze.db/resource-handle
               :variant :blaze.resource/variant)
  :ret ::rs/key)
