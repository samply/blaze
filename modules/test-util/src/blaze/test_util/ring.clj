(ns blaze.test-util.ring
  (:require
    [blaze.async.comp :as ac]))


(defn call
  "Calls async Ring `handler` with `request`, blocking on the response."
  [handler request]
  (let [future (ac/future)
        respond (partial ac/complete! future)
        raise (partial ac/complete-exceptionally! future)]
    (handler request respond raise)
    @future))
