(ns blaze.module.test-util
  (:require
   [integrant.core :as ig]))

(defmacro with-system
  "Runs `body` inside a system that is initialized from `config`, bound to
  `binding-form` and finally halted."
  [[binding-form config] & body]
  `(let [system# (ig/init ~config)]
     (try
       (let [~binding-form system#]
         ~@body)
       (finally
         (ig/halt! system#)))))
