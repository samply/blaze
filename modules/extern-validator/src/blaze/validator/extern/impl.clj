(ns blaze.validator.extern.impl
  "Pure functions interpreting the OperationOutcome of an external validator and
  producing the resource or anomaly to use for persistence."
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.spec.type :as type]))

(def validation-status-system
  "Code system of the tag added to invalid resources."
  "https://blaze-server.org/fhir/CodeSystem/ValidationStatus")

(def outcome-extension-url
  "URL of the meta extension referencing the contained OperationOutcome."
  "https://blaze-server.org/fhir/StructureDefinition/validation-outcome")

(def ^:private invalid-coding
  (type/coding
   {:system (type/uri validation-status-system)
    :code #fhir/code "invalid"}))

(defn- outcome-id
  "Derives the id of the contained OperationOutcome from its content hash.

  The id is deliberately opaque and not a fixed, guessable string, so that
  clients discover the OperationOutcome by following the meta extension rather
  than relying on a well-known id. Being content-derived it is also stable for
  equal outcomes, keeping repeated writes of the same invalid resource
  idempotent."
  [operation-outcome]
  (subs (str (hash/generate operation-outcome)) 0 32))

(defn- outcome-extension [id]
  (type/extension
   {:url outcome-extension-url
    :value (type/reference {:reference (type/string (str "#" id))})}))

(defn invalid?
  "Returns true if `operation-outcome` contains at least one issue with severity
  error or fatal."
  [operation-outcome]
  (boolean
   (some (fn [{:keys [severity]}] (#{"error" "fatal"} (:value severity)))
         (:issue operation-outcome))))

(defn- meta-map
  "Returns a plain map of the present fields of `meta` so that a fresh Meta can
  be created from it. Drops nil fields and the :fhir/type marker."
  [meta]
  (cond-> {}
    (:id meta) (assoc :id (:id meta))
    (:source meta) (assoc :source (:source meta))
    (seq (:profile meta)) (assoc :profile (:profile meta))
    (seq (:security meta)) (assoc :security (:security meta))
    (seq (:tag meta)) (assoc :tag (:tag meta))
    (seq (:extension meta)) (assoc :extension (:extension meta))))

(defn tag-invalid
  "Returns `resource` tagged as invalid.

  When `embed-outcome?` is true, additionally embeds `operation-outcome` as a
  contained resource referenced from a meta extension."
  [resource operation-outcome embed-outcome?]
  (let [meta (update (meta-map (:meta resource)) :tag (fnil conj []) invalid-coding)]
    (if embed-outcome?
      (let [id (outcome-id operation-outcome)]
        (-> resource
            (assoc :meta (type/meta (update meta :extension (fnil conj []) (outcome-extension id))))
            (update :contained (fnil conj []) (assoc operation-outcome :id id))))
      (assoc resource :meta (type/meta meta)))))

(defn- issue->internal [{:keys [severity code diagnostics expression]}]
  (cond-> {}
    severity (assoc :fhir.issues/severity (:value severity))
    code (assoc :fhir.issues/code (:value code))
    diagnostics (assoc :fhir.issues/diagnostics (:value diagnostics))
    (seq expression) (assoc :fhir.issues/expression (mapv :value expression))))

(defn reject-anomaly
  "Returns an anomaly rejecting a resource that failed external validation,
  carrying the issues of `operation-outcome`."
  [operation-outcome]
  (ba/incorrect
   "External validation of the resource failed."
   :fhir/issue "invalid"
   :fhir/issues (mapv issue->internal (:issue operation-outcome))))
