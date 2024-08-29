(ns blaze.interaction.search.nav-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.spec]
   [blaze.handler.fhir.util.spec]
   [blaze.interaction.search.nav :as nav]
   [blaze.interaction.search.nav.spec]
   [blaze.interaction.search.nav.token-url :as-alias token-url]
   [blaze.page-store.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/fdef nav/url
  :args (s/cat :base-url string?
               :match some?
               :params (s/nilable map?)
               :clauses (s/nilable :blaze.db.query/clauses))
  :ret string?)

(s/fdef nav/token-url!
  :args (s/cat :context ::token-url/context
               :match fn?
               :params (s/nilable map?)
               :clauses (s/nilable :blaze.db.query/clauses)
               :t :blaze.db/t
               :offset (s/nilable map?))
  :ret ac/completable-future?)

(s/fdef nav/token-url
  :args (s/cat :base-url string?
               :page-id-cipher :blaze/page-id-cipher
               :match fn?
               :params (s/nilable map?)
               :token any?
               :clauses (s/nilable :blaze.db.query/clauses)
               :t :blaze.db/t
               :offset (s/nilable map?))
  :ret string?)
