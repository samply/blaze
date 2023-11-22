(ns blaze.http.spec
  (:require
   [clojure.spec.alpha :as s]
   [reitit.core :as reitit])
  (:import
   [reitit.core Match]))

(s/def ::reitit/router
  reitit/router?)

(s/def ::reitit/match
  #(instance? Match %))

(s/def :blaze.preference/handling
  (s/and keyword? #(= "blaze.preference.handling" (namespace %))))

(s/def :blaze.preference/return
  (s/and keyword? #(= "blaze.preference.return" (namespace %))))
