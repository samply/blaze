(ns blaze.elm.code-spec
  (:require
   [blaze.elm.code :as code]
   [clojure.spec.alpha :as s]))

(s/fdef code/code?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef code/code
  :args (s/cat :system string? :version (s/nilable string?) :code string?)
  :ret code/code?)
