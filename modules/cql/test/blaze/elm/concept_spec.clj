(ns blaze.elm.concept-spec
  (:require
   [blaze.elm.code-spec :as code-spec]
   [blaze.elm.concept :as concept]
   [clojure.spec.alpha :as s])
  (:import
   [blaze.elm.concept Concept]))

(defn concept? [x]
  (instance? Concept x))

(s/fdef concept/to-concept
  :args (s/cat :codes (s/coll-of code-spec/code?))
  :ret concept?)
