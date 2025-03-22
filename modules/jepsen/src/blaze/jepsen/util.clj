(ns blaze.jepsen.util
  (:require
   [blaze.fhir.parsing-context]
   [blaze.fhir.writing-context]
   [integrant.core :as ig]
   [jepsen.control.core :as control]))

(def system
  (ig/init
   {:blaze.fhir/parsing-context
    {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}
    :blaze.fhir/writing-context
    {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}
    :blaze.fhir/structure-definition-repo {}}))

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
