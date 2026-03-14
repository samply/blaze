(ns blaze.terminology-service.local.validate-code.spec
  (:require
   [blaze.terminology-service.local.validate-code :as-alias vc]
   [clojure.spec.alpha :as s]))

(s/def ::code
  string?)

(s/def ::system
  string?)

(s/def ::version
  string?)

(s/def ::display
  string?)

(s/def ::vc/clause
  (s/keys :req-un [::code] :opt-un [::system ::version ::display]))

(s/def ::vc/params
  (s/keys :req-un [::vc/clause]))
