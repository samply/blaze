(ns blaze.fhir.spec.type.string-util-spec
  (:require
   [blaze.fhir.spec.type.string-util :as su]
   [clojure.spec.alpha :as s]))

(s/fdef su/capital
  :args (s/cat :s string?)
  :ret string?)

(s/fdef su/pascal->kebab
  :args (s/cat :s string?)
  :ret string?)
