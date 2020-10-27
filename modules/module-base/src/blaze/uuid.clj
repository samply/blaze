(ns blaze.uuid
  (:import
    [java.util UUID]))


(defn random-uuid
  "Creates a random UUID especially as ID for resources."
  []
  (UUID/randomUUID))
