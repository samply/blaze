(ns blaze.page-id-cipher.spec
  (:require
   [clojure.spec.alpha :as s]
   [java-time.api :as time])
  (:import
   [com.google.crypto.tink Aead]))

(defn page-id-cipher? [x]
  (instance? Aead x))

(s/def :blaze/page-id-cipher
  page-id-cipher?)

(s/def :blaze.page-id-cipher/key-rotation-period
  time/duration?)
