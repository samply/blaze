(ns blaze.interaction.search.nav-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.spec]
   [blaze.interaction.search.nav :as nav]
   [blaze.page-store.spec]
   [clojure.spec.alpha :as s]))

(s/fdef nav/url
  :args (s/cat :base-url string?
               :match some?
               :params (s/nilable map?)
               :clauses (s/nilable :blaze.db.query/clauses)
               :t :blaze.db/t
               :offset (s/nilable map?))
  :ret string?)

(s/fdef nav/token-url!
  :args (s/cat :page-store :blaze/page-store
               :base-url string?
               :match some?
               :params (s/nilable map?)
               :clauses (s/nilable :blaze.db.query/clauses)
               :t :blaze.db/t
               :offset (s/nilable map?))
  :ret ac/completable-future?)
