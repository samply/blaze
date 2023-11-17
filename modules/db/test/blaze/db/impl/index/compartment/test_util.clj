(ns blaze.db.impl.index.compartment.test-util
  (:require
   [blaze.db.impl.codec :as codec]))

(def co-c-hash->code
  (into
   {}
   (map (fn [code] [(codec/c-hash code) code]))
   ["Patient"]))
