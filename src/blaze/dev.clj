(ns blaze.dev
  (:require
    [blaze.system :as system]
    [clojure.tools.reader.edn :as edn]
    [integrant.core :as ig]))


(defn- read-config []
  (edn/read-string {:readers {'ig/ref ig/ref}} (slurp "blaze.edn")))


(comment
  (def config (read-config))
  (ig/load-namespaces (:config config))
  (ig/init (:config config))

  (def system (system/init! {:log/level "debug"}))
  (system/shutdown! system)

  (keys system)
  ((:blaze/rest-api system)
   {:uri "Patient"
    :request-method :get})
  )
