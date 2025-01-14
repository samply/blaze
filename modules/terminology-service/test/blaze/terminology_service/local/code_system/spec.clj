(ns blaze.terminology-service.local.code-system.spec
  (:require
   [blaze.db.spec]
   [blaze.terminology-service.local :as-alias local]
   [blaze.terminology-service.local.code-system :as-alias cs]
   [blaze.terminology-service.local.code-system.sct.spec]
   [blaze.terminology-service.local.spec]
   [clojure.spec.alpha :as s]))

(s/def ::cs/find-context
  (s/keys :req-un [:blaze.db/db] :opt-un [::local/tx-resources] :opt [:sct/context]))

(s/def ::active-only
  boolean?)

(s/def ::cs/expand-params
  (s/keys :opt-un [::active-only]))

(s/def ::code
  string?)

(s/def ::system
  string?)

(s/def ::version
  string?)

(s/def ::display
  string?)

(s/def ::clause
  (s/keys :req-un [::code] :opt-un [::system ::version ::display]))

(s/def ::cs/validate-code-params
  (s/keys :req-un [::clause] :opt-un [::active-only]))
