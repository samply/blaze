(ns blaze.interaction.search.nav-spec
  (:require
    [blaze.db.spec]
    [blaze.interaction.search.nav :as nav]
    [blaze.interaction.search.params-spec :as params-spec]
    [clojure.spec.alpha :as s]))


(s/fdef nav/url
  :args (s/cat :match some? :params ::params-spec/params :t :blaze.db/t
               :offset map?)
  :ret string?)
