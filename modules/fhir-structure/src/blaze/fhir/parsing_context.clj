(ns blaze.fhir.parsing-context
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.fhir.spec.resource :as res]
   [blaze.fhir.structure-definition-repo :as sdr]
   [blaze.fhir.structure-definition-repo.spec]
   [blaze.module :as m]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

(defn- context [complex-types resources]
  (reduce
   (fn [r {{elements :element} :snapshot}]
     (if-ok [handlers (res/create-type-handlers elements)]
       (into r handlers)
       reduced))
   {:Resource res/resource-handler}
   (into complex-types resources)))

(defmethod m/pre-init-spec :blaze.fhir/parsing-context [_]
  (s/keys :req-un [:blaze.fhir/structure-definition-repo]))

(defmethod ig/init-key :blaze.fhir/parsing-context
  [_ {:keys [structure-definition-repo]}]
  (log/info "Init parsing context")
  (ba/throw-when
   (context (sdr/complex-types structure-definition-repo)
     (sdr/resources structure-definition-repo))))
