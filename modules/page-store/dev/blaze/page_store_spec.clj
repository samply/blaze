(ns blaze.page-store-spec
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.spec]
    [blaze.page-store :as page-store]
    [blaze.page-store.spec]
    [clojure.spec.alpha :as s]))


(s/fdef page-store/get
  :args (s/cat :store :blaze/page-store :token :blaze.page-store/token)
  :ret ac/completable-future?)


(s/fdef page-store/put!
  :args (s/cat :store :blaze/page-store :clauses :blaze.db.query/clauses)
  :ret ac/completable-future?)
