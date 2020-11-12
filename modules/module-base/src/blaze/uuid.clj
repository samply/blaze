(ns blaze.uuid
  (:import
    [java.util UUID]))


(defn random-uuid
  "Creates a random UUID string."
  []
  (str (UUID/randomUUID)))
