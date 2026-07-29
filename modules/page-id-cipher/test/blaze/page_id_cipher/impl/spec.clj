(ns blaze.page-id-cipher.impl.spec
  (:require
   [blaze.page-id-cipher.impl :as-alias impl]
   [clojure.spec.alpha :as s])
  (:import
   [com.google.crypto.tink KeysetHandle]))

(s/def ::impl/key-set-handle
  #(instance? KeysetHandle %))
