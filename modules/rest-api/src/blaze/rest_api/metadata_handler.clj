(ns blaze.rest-api.metadata-handler
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.structure-definition-repo :as sdr]
   [blaze.fhir.structure-definition-repo.spec]
   [blaze.module :as m]
   [blaze.rest-api :as-alias rest-api]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- index-structure-defs [structure-definitions]
  (into
   {}
   (map (fn [{:keys [url] :as structure-def}] [url structure-def]))
   structure-definitions))

(defn- handler [structure-definitions]
  (let [index (index-structure-defs structure-definitions)]
    (fn [{{:strs [url]} :params}]
      (if-let [structure-def (get index url)]
        (ac/completed-future (ring/response structure-def))
        (ac/completed-future (ring/not-found {}))))))

(defmethod m/pre-init-spec ::rest-api/metadata-handler [_]
  (s/keys :req-un [:blaze.fhir/structure-definition-repo]))

(defmethod ig/init-key ::rest-api/metadata-handler
  [_ {:keys [structure-definition-repo]}]
  (log/info "Init Metadata endpoint")
  (handler (into (sdr/resources structure-definition-repo)
                 (sdr/complex-types structure-definition-repo))))
