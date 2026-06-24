(ns blaze.fhir.canonical-spec
  (:require
   [blaze.fhir.canonical :as canonical]
   [clojure.spec.alpha :as s]))

(s/fdef canonical/url
  :args (s/cat :path string?)
  :ret string?)

(s/fdef canonical/old-url
  :args (s/cat :path string?)
  :ret string?)

(s/fdef canonical/urls
  :args (s/cat :path string?)
  :ret (s/tuple string? string?))

(s/fdef canonical/legacy-url
  :args (s/cat :url string?)
  :ret (s/nilable string?))

(s/fdef canonical/both-urls
  :args (s/cat :url string?)
  :ret (s/coll-of string? :min-count 1 :max-count 2))

(s/fdef canonical/both-codings
  :args (s/cat :coding some?)
  :ret (s/coll-of some? :min-count 1 :max-count 2))

(s/fdef canonical/matches?
  :args (s/cat :url string? :s (s/nilable string?))
  :ret boolean?)

(s/fdef canonical/extensions
  :args (s/cat :path string? :value some?)
  :ret (s/coll-of some? :count 2))

(s/fdef canonical/codings
  :args (s/cat :path string? :code string?)
  :ret (s/coll-of some? :count 2))

(s/fdef canonical/system-codings
  :args (s/cat :system string? :code string?)
  :ret (s/coll-of some? :min-count 1 :max-count 2))
