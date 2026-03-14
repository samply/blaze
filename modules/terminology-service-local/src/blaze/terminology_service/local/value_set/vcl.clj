(ns blaze.terminology-service.local.value-set.vcl
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.value-set.core :as c]
   [clojure.core.protocols :as p]
   [clojure.datafy :as datafy]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [ring.util.codec :as ring-codec])
  (:import
   [org.hl7.fhir.r5.model
    ValueSet ValueSet$ConceptReferenceComponent ValueSet$ConceptSetComponent
    ValueSet$ConceptSetFilterComponent ValueSet$ValueSetComposeComponent]
   [org.hl7.fhir.r5.terminologies.utilities VCLParser VCLParser$VCLParseException]))

(set! *warn-on-reflection* true)

(defn- extract-expr [url]
  ((ring-codec/form-decode-map (subs url (inc (str/index-of url \?)))) "v1"))

(extend-protocol p/Datafiable
  ValueSet
  (datafy [value-set]
    (cond-> {:fhir/type :fhir/ValueSet}
      (.hasCompose value-set)
      (assoc :compose (datafy/datafy (.getCompose value-set)))))
  ValueSet$ValueSetComposeComponent
  (datafy [compose]
    (cond-> {:fhir/type :fhir.ValueSet/compose
             :include (mapv datafy/datafy (.getInclude compose))}
      (.hasExclude compose)
      (assoc :exclude (mapv datafy/datafy (.getExclude compose)))))
  ValueSet$ConceptSetComponent
  (datafy [component]
    (cond-> {:fhir/type :fhir.ValueSet.compose/include}
      (.hasSystem component)
      (assoc :system (type/uri (.getSystem component)))
      (.hasConcept component)
      (assoc :concept (mapv datafy/datafy (.getConcept component)))
      (.hasFilter component)
      (assoc :filter (mapv datafy/datafy (.getFilter component)))))
  ValueSet$ConceptReferenceComponent
  (datafy [component]
    {:fhir/type :fhir.ValueSet.compose.include/concept
     :code (type/code (.getCode component))})
  ValueSet$ConceptSetFilterComponent
  (datafy [component]
    {:fhir/type :fhir.ValueSet.compose.include/filter
     :property (type/code (.getProperty component))
     :op (type/code (.toCode (.getOp component)))
     :value (type/string (.getValue component))}))

(defn- parse-expr* [expr]
  (ba/try-one VCLParser$VCLParseException ::anom/incorrect (VCLParser/parse expr)))

(defn parse-expr [expr]
  (if-ok [value-set (parse-expr* expr)]
    (datafy/datafy value-set)
    (fn [e]
      (update e ::anom/message (partial str (format "Invalid VCL expression `%s`. " expr))))))

(defmethod c/find :vcl
  [_ url & [_version]]
  (ac/completed-future
   (parse-expr (extract-expr url))))
