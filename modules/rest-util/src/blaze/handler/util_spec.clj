(ns blaze.handler.util-spec
  (:require
    [blaze.async.comp :as ac]
    [blaze.async.comp-spec]
    [blaze.db.spec]
    [blaze.handler.util :as util]
    [clojure.spec.alpha :as s]))


(s/fdef util/preference
  :args (s/cat :headers (s/nilable map?) :name string?)
  :ret (s/nilable string?))


(s/fdef util/db
  :args (s/cat :node :blaze.db/node :t (s/nilable :blaze.db/t))
  :ret (s/or :future ac/completable-future? :db :blaze.db/db))

