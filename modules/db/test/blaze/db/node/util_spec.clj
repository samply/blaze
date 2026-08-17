(ns blaze.db.node.util-spec
  (:require
   [blaze.db.node.util :as node-util]
   [blaze.db.resource-store :as rs]
   [blaze.db.spec]
   [blaze.fhir.spec.spec]
   [blaze.integrant :as-alias bi]
   [blaze.integrant.spec]
   [clojure.spec.alpha :as s]))

(s/fdef node-util/name-part
  :args (s/cat :key ::bi/composite-key)
  :ret string?)

(s/fdef node-util/node-name
  :args (s/cat :key ::bi/key)
  :ret string?)

(s/fdef node-util/component-name
  :args (s/cat :key ::bi/key :suffix string?)
  :ret string?)

(s/fdef node-util/thread-name-template
  :args (s/cat :key ::bi/key :suffix string?)
  :ret string?)

(s/fdef node-util/rs-key
  :args (s/cat :resource-handle :blaze.db/resource-handle
               :variant :blaze.resource/variant)
  :ret ::rs/key)

(s/fdef node-util/instant
  :args (s/cat :last-updated :blaze.db.tx/instant)
  :ret :fhir/instant)

(s/fdef node-util/start-thread!
  :args (s/cat :f fn? :name string?))
