(ns blaze.path-spec
  (:require
   [blaze.path :as path :refer [path path?]]
   [blaze.path.spec]
   [clojure.spec.alpha :as s]))

(s/fdef path?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef path
  :args (s/cat :first string? :more (s/* string?))
  :ret :blaze/path)

(s/fdef path/resolve
  :args (s/cat :path path? :more (s/* string?))
  :ret :blaze/path)

(s/fdef path/dir?
  :args (s/cat :path path?)
  :ret boolean?)
