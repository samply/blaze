(ns blaze.jepsen.util
  (:require
   [blaze.fhir.parsing-context]
   [blaze.fhir.writing-context]
   [hato.client :as hc]
   [integrant.core :as ig]
   [jepsen.control.core :as control]))

(def system
  (ig/init
   {:blaze.fhir/parsing-context
    {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}
    :blaze.fhir/writing-context
    {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}
    :blaze.fhir/structure-definition-repo {}}))

(defn client-context
  "Returns the context of a client of the Blaze running on `node`."
  [node]
  {:base-uri (str "http://" node "/fhir")
   :http-client (hc/build-http-client {:connect-timeout 10000})
   :parsing-context (:blaze.fhir/parsing-context system)
   :writing-context (:blaze.fhir/writing-context system)})

(defrecord Remote []
  control/Remote
  (connect [this _]
    this)
  (disconnect! [this]
    this)
  (execute! [_ _ _]
    {})
  (upload! [_ _ _ _ _])
  (download! [_ _ _ _ _]))
