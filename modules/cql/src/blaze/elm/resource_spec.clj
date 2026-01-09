(ns blaze.elm.resource-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.spec]
   [blaze.elm.resource :as cr]
   [clojure.spec.alpha :as s]))

(s/fdef cr/resource?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef cr/handle
  :args (s/cat :resource cr/resource?)
  :ret :blaze.db/resource-handle)

(s/fdef cr/pull
  :args (s/cat :resource cr/resource?)
  :ret ac/completable-future?)

(s/fdef cr/mk-resource
  :args (s/cat :db :blaze.db/db :handle :blaze.db/resource-handle)
  :ret cr/resource?)

(s/fdef cr/resource-mapper
  :args (s/cat :db :blaze.db/db))
