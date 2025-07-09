(ns blaze.interaction.search.util.spec
  (:require
   [blaze.http.spec]
   [blaze.interaction.search.util :as search-util]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [reitit.core :as reitit]))

(s/def ::search-util/context
  (s/keys :req [:blaze/base-url ::reitit/router]))
