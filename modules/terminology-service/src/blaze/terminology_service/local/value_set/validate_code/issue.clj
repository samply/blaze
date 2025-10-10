(ns blaze.terminology-service.local.value-set.validate-code.issue
  (:refer-clojure :exclude [str])
  (:require
   [blaze.fhir.spec.type :as type]
   [blaze.util :refer [str]]
   [cognitect.anomalies :as anom]))

(defn- tx-issue-type-coding [code]
  (type/coding
   {:system #fhir/uri "http://hl7.org/fhir/tools/CodeSystem/tx-issue-type"
    :code (type/code code)}))

(def ^:private not-found-coding
  (tx-issue-type-coding "not-found"))

(def ^:private not-in-vs-coding
  (tx-issue-type-coding "not-in-vs"))

(def ^:private invalid-data-coding
  (tx-issue-type-coding "invalid-data"))

(def ^:private invalid-code-coding
  (tx-issue-type-coding "invalid-code"))

(def ^:private invalid-display-coding
  (tx-issue-type-coding "invalid-display"))

(def ^:private cannot-infer-coding
  (tx-issue-type-coding "cannot-infer"))

(def ^:private code-rule-coding
  (tx-issue-type-coding "code-rule"))

(def ^:private vs-invalid-coding
  (tx-issue-type-coding "vs-invalid"))

(defn- code [{:keys [code system]}]
  (cond->> code system (str system "#")))

(defn- value-set-canonical [{:keys [url version]}]
  (when-let [url (type/value url)]
    (let [version (type/value version)]
      (cond-> url version (str "|" version)))))

(defn- value-set-msg [value-set]
  (if-let [c (value-set-canonical value-set)]
    (format "value set `%s`" c)
    "provided value set"))

(defn- not-in-vs-msg [value-set clause]
  (format "The provided code `%s` was not found in the %s." (code clause)
          (value-set-msg value-set)))

(defn not-in-vs
  {:arglists '([value-set clause])}
  [value-set {:keys [origin] :as clause}]
  {:fhir/type :fhir.OperationOutcome/issue
   :severity #fhir/code "error"
   :code #fhir/code "code-invalid"
   :details
   (type/codeable-concept
    {:coding [not-in-vs-coding]
     :text (type/string (not-in-vs-msg value-set clause))})
   :expression [(type/string (cond->> "code" origin (str origin ".")))]})

(defn missing-system
  {:arglists '([clause])}
  [{:keys [origin]}]
  {:fhir/type :fhir.OperationOutcome/issue
   :severity #fhir/code "warning"
   :code #fhir/code "invalid"
   :details
   (type/codeable-concept
    {:coding [invalid-data-coding]
     :text (type/string "Coding has no system. A code with no system has no defined meaning, and it cannot be validated. A system should be provided.")})
   :expression [(type/string origin)]})

(defn- code-system-msg [system version]
  (if-let [c (cond-> system version (str "|" version))]
    (format "code system `%s`" c)
    "provided code system"))

(defn invalid-code
  {:arglists '([clause])}
  [{:keys [code system version origin]}]
  {:fhir/type :fhir.OperationOutcome/issue
   :severity #fhir/code "error"
   :code #fhir/code "code-invalid"
   :details
   (type/codeable-concept
    {:coding [invalid-code-coding]
     :text (type/string (format "Unknown code `%s` was not found in the %s." code (code-system-msg system version)))})
   :expression [(type/string (cond->> "code" origin (str origin ".")))]})

(defn invalid-display
  {:arglists '([clause concept lenient-display-validation])}
  [{:keys [code system origin] expected-display :display} {actual-display :display} lenient-display-validation]
  {:fhir/type :fhir.OperationOutcome/issue
   :severity (if lenient-display-validation #fhir/code "warning" #fhir/code "error")
   :code #fhir/code "invalid"
   :details
   (type/codeable-concept
    {:coding [invalid-display-coding]
     :text
     (type/string
      (cond-> (format "Invalid display `%s` for code `%s`." expected-display (str system "#" code))
        actual-display (str (format " A valid display is `%s`." (type/value actual-display)))))})
   :expression [(type/string (cond->> "display" origin (str origin ".")))]})

(defn cannot-infer
  {:arglists '([code-system clause])}
  [{:keys [url]} {:keys [code]}]
  {:fhir/type :fhir.OperationOutcome/issue
   :severity #fhir/code "error"
   :code #fhir/code "not-found"
   :details
   (type/codeable-concept
    {:coding [cannot-infer-coding]
     :text (type/string (format "The provided code `%s` is not known to belong to the inferred code system `%s`." code (type/value url)))})
   :expression [#fhir/string "code"]})

(defn inactive-code
  {:arglists '([clause])}
  [{:keys [code origin]}]
  {:fhir/type :fhir.OperationOutcome/issue
   :severity #fhir/code "error"
   :code #fhir/code "business-rule"
   :details
   (type/codeable-concept
    {:coding [code-rule-coding]
     :text (type/string (format "The code `%s` is valid but is not active." code))})
   :expression [(type/string (cond->> "code" origin (str origin ".")))]})

(defn value-set-not-found [url]
  {:fhir/type :fhir.OperationOutcome/issue
   :severity #fhir/code "error"
   :code #fhir/code "not-found"
   :details
   (type/codeable-concept
    {:coding [not-found-coding]
     :text (type/string (format "A definition for the value Set `%s` could not be found." url))})})

(defn code-system-not-found
  {:arglists '([clause])}
  [{:keys [system version origin]}]
  {:fhir/type :fhir.OperationOutcome/issue
   :severity #fhir/code "error"
   :code #fhir/code "not-found"
   :details
   (type/codeable-concept
    {:coding [not-found-coding]
     :text (type/string (format "A definition for the code system `%s` could not be found, so the code cannot be validated." (cond-> system version (str "|" version))))})
   :expression [(type/string (cond->> "system" origin (str origin ".")))]})

(defn unknown-value-set [value-set unknown-value-set-url]
  {:fhir/type :fhir.OperationOutcome/issue
   :severity #fhir/code "warning"
   :code #fhir/code "not-found"
   :details
   (type/codeable-concept
    {:coding [vs-invalid-coding]
     :text (type/string (format "Unable to check whether the code is in the %s because the value set `%s` was not found."
                                (value-set-msg value-set) unknown-value-set-url))})})

(defn unknown-code-system
  {:arglists '([value-set clause])}
  [value-set {:keys [system version]}]
  {:fhir/type :fhir.OperationOutcome/issue
   :severity #fhir/code "warning"
   :code #fhir/code "not-found"
   :details
   (type/codeable-concept
    {:coding [vs-invalid-coding]
     :text (type/string (format "Unable to check whether the code is in the %s because the code system `%s` was not found."
                                (value-set-msg value-set) (cond-> system version (str "|" version))))})})

(defn invalid-value-set
  {:arglists '([value-set anomaly])}
  [value-set {::anom/keys [message]}]
  {:fhir/type :fhir.OperationOutcome/issue
   :severity #fhir/code "error"
   :code #fhir/code "invalid"
   :details
   (type/codeable-concept
    {:coding [vs-invalid-coding]
     :text (type/string (format "Unable to check whether the code is in the %s because the value set was invalid. %s"
                                (value-set-msg value-set) message))})})
