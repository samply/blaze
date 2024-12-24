(ns blaze.path-spec
  (:require
   [blaze.path :refer [path? path dir?]]
   [blaze.path.spec]
   [clojure.spec.alpha :as s]))

(s/fdef path?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef path
  :args (s/cat :first string? :more (s/* string?))
  :ret :blaze/path)

(s/fdef dir?
  :args (s/cat :path path?)
  :ret boolean?)
