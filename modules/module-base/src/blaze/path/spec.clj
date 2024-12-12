(ns blaze.path.spec
  (:require
   [blaze.path :refer [path? dir?]]
   [clojure.spec.alpha :as s]))

(s/def :blaze/path
  path?)

(s/def :blaze/dir
  (s/and path? dir?))
