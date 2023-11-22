(ns blaze.elm.compiler.external-data-spec
  (:require
   [blaze.db.spec]
   [blaze.elm.compiler.external-data :as ed]
   [clojure.spec.alpha :as s]))

(s/fdef ed/resource?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef ed/mk-resource
  :args (s/cat :db :blaze.db/db :handle :blaze.db/resource-handle)
  :ret ed/resource?)

(s/fdef ed/resource-mapper
  :args (s/cat :db :blaze.db/db))
