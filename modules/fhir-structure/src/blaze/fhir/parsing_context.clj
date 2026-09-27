(ns blaze.fhir.parsing-context
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.parsing-context.spec]
   [blaze.fhir.spec.resource :as res]
   [blaze.fhir.structure-definition-repo :as sdr]
   [blaze.fhir.structure-definition-repo.spec]
   [blaze.module :as m]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

(defmethod m/pre-init-spec :blaze.fhir/parsing-context [_]
  (s/keys :req-un [:blaze.fhir/structure-definition-repo]
          :opt-un [::fail-on-unknown-property ::include-summary-only
                   ::use-regex]))

(defmethod ig/init-key :blaze.fhir/parsing-context
  [_ {:keys [structure-definition-repo fail-on-unknown-property
             include-summary-only use-regex]
      :or {fail-on-unknown-property true include-summary-only false
           use-regex true}}]
  (log/info "Init parsing context")
  (ba/throw-when
   (res/create-type-handlers
    (into (sdr/complex-types structure-definition-repo)
          (sdr/resources structure-definition-repo))
    {:fail-on-unknown-property fail-on-unknown-property
     :include-summary-only include-summary-only
     :use-regex use-regex})))
