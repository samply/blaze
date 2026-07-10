(ns blaze.validator.extern
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.fhir-client.impl :as fhir-client]
   [blaze.fhir.parsing-context.spec]
   [blaze.fhir.writing-context.spec]
   [blaze.http-client.spec]
   [blaze.module :as m :refer [reg-collector]]
   [blaze.validator.extern.impl :as impl]
   [blaze.validator.extern.semaphore :as sem]
   [blaze.validator.extern.spec]
   [blaze.validator.protocols :as p]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [prometheus.alpha :as prom :refer [defhistogram]]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defhistogram request-duration-seconds
  "Extern validator request latencies."
  {:namespace "blaze"
   :subsystem "validator_extern"}
  (take 14 (iterate #(* 2 %) 0.001)))

(defn- interpret [resource failure-mode operation-outcome]
  (if (impl/invalid? operation-outcome)
    (case failure-mode
      :reject (impl/reject-anomaly operation-outcome)
      :tag-only (impl/tag-invalid resource operation-outcome false)
      ;; default is :tag-outcome
      (impl/tag-invalid resource operation-outcome true))
    resource))

(defn- validate [base-uri http-opts failure-mode resource]
  (let [timer (prom/timer request-duration-seconds)]
    (-> (fhir-client/post (str base-uri "/validateResource") resource http-opts)
        (ac/exceptionally
         (fn [_] (ba/busy "The external validator is currently unavailable.")))
        (ac/then-apply (partial interpret resource failure-mode))
        (ac/when-complete
         (fn [_ _]
           (let [duration (prom/observe-duration! timer)]
             (log/trace
              (format "Validated resource `%s` in %.0f ms."
                      (str (name (:fhir/type resource)) "/" (:id resource))
                      (* duration 1e3)))))))))

(defn- bounded
  "Wraps `validate-fn` so that at most `max-concurrency` requests run
  concurrently. Excess requests are queued without blocking a thread until a
  permit becomes available."
  [validate-fn max-concurrency]
  (let [semaphore (sem/semaphore max-concurrency)]
    (fn [resource]
      (-> (sem/acquire! semaphore)
          (ac/then-compose (fn [_] (validate-fn resource)))
          (ac/when-complete (fn [_ _] (sem/release! semaphore)))))))

(defmethod m/pre-init-spec :blaze.validator/extern [_]
  (s/keys :req-un [::base-uri :blaze/http-client :blaze.fhir/parsing-context
                   :blaze.fhir/writing-context]
          :opt-un [::failure-mode ::max-concurrency]))

(defmethod ig/init-key :blaze.validator/extern
  [_ {:keys [base-uri http-client parsing-context writing-context failure-mode
             max-concurrency]
      :or {failure-mode :tag-outcome max-concurrency 4}}]
  (log/info "Init external validator connection:" base-uri "with failure mode"
            (name failure-mode) "and a max concurrency of" max-concurrency)
  (let [http-opts {:http-client http-client
                   :parsing-context parsing-context
                   :writing-context writing-context}
        validate-fn (-> (partial validate base-uri http-opts failure-mode)
                        (bounded max-concurrency))]
    (reify p/Validator
      (-validate [_ resource]
        (validate-fn resource)))))

(reg-collector ::request-duration-seconds
  request-duration-seconds)
