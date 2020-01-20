(ns blaze.module
  (:require
    [integrant.core :as ig]))


(defmacro reg-collector
  "Registers a metrics collector to the central registry."
  [key collector]
  `(do
     (defmethod ig/init-key ~key ~'[_ _] ~collector)

     (derive ~key :blaze.metrics/collector)))
