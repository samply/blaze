(ns blaze.elm.concept-spec
  (:require
   [blaze.elm.code :as code]
   [blaze.elm.concept :as concept]
   [clojure.spec.alpha :as s])
  (:import
   [blaze.elm.concept Concept]))

(defn concept? [x]
  (instance? Concept x))

(s/fdef concept/concept
  :args (s/cat :codes (s/coll-of code/code?))
  :ret concept?)
