(ns blaze.elm.value-set-spec
  (:require
   [blaze.elm.code :as code]
   [blaze.elm.code :refer [code?]]
   [blaze.elm.concept :refer [concept?]]
   [blaze.elm.value-set :as value-set]
   [blaze.elm.value-set.spec]
   [blaze.terminology-service.spec]
   [clojure.spec.alpha :as s]))

(s/fdef value-set/value-set?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef value-set/url
  :args (s/cat :value-set :blaze.elm/value-set)
  :ret string?)

(s/fdef value-set/contains-string?
  :args (s/cat :value-set :blaze.elm/value-set :code string?)
  :ret boolean?)

(s/fdef value-set/contains-code?
  :args (s/cat :value-set :blaze.elm/value-set :code code?)
  :ret boolean?)

(s/fdef value-set/contains-concept?
  :args (s/cat :value-set :blaze.elm/value-set :concept concept?)
  :ret boolean?)

(s/fdef value-set/expand
  :args (s/cat :value-set :blaze.elm/value-set)
  :ret (s/coll-of code/code?))

(s/fdef value-set/value-set
  :args (s/cat :terminology-service :blaze/terminology-service :url string?)
  :ret :blaze.elm/value-set)
