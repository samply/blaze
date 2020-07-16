(ns blaze.handler.util-spec
  (:require
    [blaze.db.spec]
    [blaze.handler.util :as util]
    [clojure.spec.alpha :as s]
    [manifold.deferred :refer [deferred?]]))


(s/fdef util/preference
  :args (s/cat :headers (s/nilable map?) :name string?)
  :ret (s/nilable string?))


(s/fdef util/db
  :args (s/cat :node :blaze.db/node :t (s/nilable :blaze.db/t))
  :ret (s/or :deferred deferred? :db :blaze.db/db))

