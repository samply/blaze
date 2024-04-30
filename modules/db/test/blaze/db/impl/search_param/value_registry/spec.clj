(ns blaze.db.impl.search-param.value-registry.spec
  (:require
   [blaze.byte-string :as bs :refer [byte-string?]]
   [clojure.spec.alpha :as s]))

(s/def :blaze.db/three-byte-id
  (s/and byte-string? #(= 3 (bs/size %))))

(s/def :blaze.db/four-byte-id
  (s/and byte-string? #(= 4 (bs/size %))))
