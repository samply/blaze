(ns blaze.elm.code-system-spec
  (:require
   [blaze.elm.code :refer [code?]]
   [blaze.elm.code-system :as code-system]
   [blaze.elm.code-system.spec]
   [blaze.elm.concept :refer [concept?]]
   [blaze.elm.spec]
   [blaze.terminology-service.spec]
   [clojure.spec.alpha :as s]))

(s/fdef code-system/contains-string?
  :args (s/cat :code-system :blaze.elm/code-system :code string?)
  :ret boolean?)

(s/fdef code-system/contains-code?
  :args (s/cat :code-system :blaze.elm/code-system :code code?)
  :ret boolean?)

(s/fdef code-system/contains-concept?
  :args (s/cat :code-system :blaze.elm/code-system :concept concept?)
  :ret boolean?)

(s/fdef code-system/code-system
  :args (s/cat :terminology-service :blaze/terminology-service
               :code-system-def :elm/code-system-def)
  :ret :blaze.elm/code-system)
