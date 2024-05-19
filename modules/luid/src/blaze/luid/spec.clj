(ns blaze.luid.spec
  (:require
   [blaze.luid :as luid]
   [clojure.spec.alpha :as s]))

(s/def ::luid/generator
  luid/generator?)
