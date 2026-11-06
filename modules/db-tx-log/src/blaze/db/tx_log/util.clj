(ns blaze.db.tx-log.util
  "Functions shared by transaction log implementations."
  (:require
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [taoensso.timbre :as log]))

(defn- invalid-tx-data-msg [t cause]
  (format "Skip transaction with point in time of %s because tx-data is invalid: %s"
          t (str/replace cause #"\s" " ")))

(defn valid-tx-data
  "Returns `tx-data` read from a transaction log if it is valid or nil
  otherwise.

  Logs a warning for invalid transaction data, which the transaction log skips.
  Transaction data with invalid permissions is invalid as a whole, because
  treating the permissions as absent would change the outcome of the
  transaction."
  [tx-data]
  (if (s/valid? :blaze.db/tx-data tx-data)
    tx-data
    (log/warn (invalid-tx-data-msg (:t tx-data) (s/explain-str :blaze.db/tx-data tx-data)))))
