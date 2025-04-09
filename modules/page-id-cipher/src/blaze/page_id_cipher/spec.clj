(ns blaze.page-id-cipher.spec
  (:require
   [clojure.spec.alpha :as s]
   [java-time.api :as time])
  (:import
   [com.google.crypto.tink Aead]))

(s/def :blaze/page-id-cipher
  #(instance? Aead %))

(s/def :blaze.page-id-cipher/key-rotation-period
  time/duration?)
