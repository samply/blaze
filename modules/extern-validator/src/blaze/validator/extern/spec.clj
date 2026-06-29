(ns blaze.validator.extern.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def :blaze.validator.extern/base-uri
  string?)

(s/def :blaze.validator.extern/failure-mode
  #{:tag-only :tag-outcome :reject})
