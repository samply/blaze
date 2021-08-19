(ns blaze.jepsen.util
  (:require
    [jepsen.control.core :as control]))


(defrecord Remote []
  control/Remote
  (connect [this _]
    this)
  (disconnect! [this]
    this)
  (execute! [_ _ _]
    {})
  (upload! [_ _ _ _ _]
    )
  (download! [_ _ _ _ _]
    ))
