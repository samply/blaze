(ns blaze.fhir.parsing-context
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.resource :as res]
   [blaze.fhir.structure-definition-repo :as sdr]
   [blaze.fhir.structure-definition-repo.spec]
   [blaze.module :as m]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

(def ^:private create-handler-xf
  (comp
   (map
    (fn [{{elements :element} :snapshot}]
      (res/create-handlers elements)))
   (halt-when ba/anomaly?)))

(defn- context [complex-types resources]
  (-> (transduce create-handler-xf merge {:Resource res/resource-handler}
                 complex-types)
      (ba/map #(transduce create-handler-xf merge % resources))))

(defmethod m/pre-init-spec :blaze.fhir/parsing-context [_]
  (s/keys :req-un [:blaze.fhir/structure-definition-repo]))

(defmethod ig/init-key :blaze.fhir/parsing-context
  [_ {:keys [structure-definition-repo]}]
  (log/info "Init parsing context")
  (context (sdr/complex-types structure-definition-repo)
    (sdr/resources structure-definition-repo)))
