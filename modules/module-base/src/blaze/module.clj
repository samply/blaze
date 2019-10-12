(ns blaze.module
  (:require
    [integrant.core :as ig]))


(defmacro defcollector
  "Registers a metrics collector to the central registry."
  [name bindings & body]
  (let [key
        (if (simple-symbol? name)
          (keyword (str *ns*) (clojure.core/name name))
          (keyword (namespace name) (clojure.core/name name)))]
    `(do
       (defmethod ig/init-key ~key
         ~(into ['_] bindings)
         ~@body)

       (derive ~key :blaze.metrics/collector))))

