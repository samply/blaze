(ns blaze.elm.code-spec
  (:require
   [blaze.elm.code :as code]
   [clojure.spec.alpha :as s])
  (:import
   [blaze.elm.code Code]))

(defn code? [x]
  (instance? Code x))

(s/fdef code/to-code
  :args (s/cat :system string? :version (s/nilable string?) :code string?)
  :ret code?)
