(ns blaze.elm.value-set-spec
  (:require
   [blaze.elm.code :refer [code?]]
   [blaze.elm.concept :refer [concept?]]
   [blaze.elm.value-set :as vs]
   [blaze.elm.value-set.spec]
   [blaze.terminology-service.spec]
   [clojure.spec.alpha :as s]))

(s/fdef vs/value-set?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef vs/url
  :args (s/cat :value-set :blaze.elm/value-set)
  :ret string?)

(s/fdef vs/contains-string?
  :args (s/cat :value-set :blaze.elm/value-set :code string?)
  :ret boolean?)

(s/fdef vs/contains-code?
  :args (s/cat :value-set :blaze.elm/value-set :code code?)
  :ret boolean?)

(s/fdef vs/contains-concept?
  :args (s/cat :value-set :blaze.elm/value-set :concept concept?)
  :ret boolean?)

(s/fdef vs/value-set
  :args (s/cat :terminology-service :blaze/terminology-service :url string?)
  :ret :blaze.elm/value-set)
