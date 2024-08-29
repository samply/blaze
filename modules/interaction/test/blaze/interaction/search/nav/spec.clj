(ns blaze.interaction.search.nav.spec
  (:require
   [blaze.interaction.search.nav.token-url :as-alias token-url]
   [blaze.page-id-cipher.spec]
   [blaze.page-store.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/def ::token-url/context
  (s/keys :req [:blaze/base-url]
          :req-un [:blaze/page-store :blaze/page-id-cipher]))
