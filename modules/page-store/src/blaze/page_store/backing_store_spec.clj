(ns blaze.page-store.backing-store-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.spec]
   [blaze.page-store.backing-store :as backing-store]
   [blaze.page-store.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/fdef backing-store/get
  :args (s/cat :store :blaze.page-store/backing-store
               :token :blaze.page-store/token)
  :ret ac/completable-future?)

(s/fdef backing-store/put!
  :args (s/cat :store :blaze.page-store/backing-store
               :token :blaze.page-store/token
               :clauses :blaze.db.query/clauses)
  :ret ac/completable-future?)
