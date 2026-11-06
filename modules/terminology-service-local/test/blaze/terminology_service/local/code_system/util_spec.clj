(ns blaze.terminology-service.local.code-system.util-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.spec]
   [blaze.terminology-service.local.code-system.util :as cs-u]
   [clojure.spec.alpha :as s]))

(s/fdef cs-u/create-code-systems
  :args (s/cat :node :blaze.db/node :tx-ops :blaze.db/tx-ops)
  :ret ac/completable-future?)
