(ns blaze.interaction.search.nav-spec
  (:require
    [blaze.db.spec]
    [blaze.interaction.search.nav :as nav]
    [blaze.interaction.search.params-spec :as params-spec]
    [clojure.spec.alpha :as s]))


(s/fdef nav/url
  :args (s/cat :match some?
               :params ::params-spec/params
               :clauses (s/nilable (s/coll-of :blaze.db.query/clause))
               :t :blaze.db/t
               :offset (s/nilable map?))
  :ret string?)
