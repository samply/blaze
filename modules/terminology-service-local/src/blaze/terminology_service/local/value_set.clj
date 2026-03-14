(ns blaze.terminology-service.local.value-set
  (:refer-clojure :exclude [find])
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.value-set.core :as c]
   [blaze.terminology-service.local.value-set.default]
   [blaze.terminology-service.local.value-set.loinc]
   [blaze.terminology-service.local.value-set.vcl]))

(defn- find-in-tx-resources
  ([tx-resources url]
   (some
    (fn [{:fhir/keys [type] :as resource}]
      (when (identical? :fhir/ValueSet type)
        (when (= url (:value (:url resource)))
          (ac/completed-future resource))))
    tx-resources))
  ([tx-resources url version]
   (some
    (fn [{:fhir/keys [type] :as resource}]
      (when (identical? :fhir/ValueSet type)
        (when (= url (:value (:url resource)))
          (when (= version (:value (:version resource)))
            (ac/completed-future resource)))))
    tx-resources)))

(defn find
  "Returns a CompletableFuture that will complete with the first ValueSet
  resource with `url` and optional `version` in `db` according to priority or
  complete exceptionally in case of none found or errors."
  {:arglists '([context url] [context url version])}
  ([{:keys [tx-resources] :as context} url]
   (or (some-> tx-resources (find-in-tx-resources url))
       (c/find context url)))
  ([{:keys [tx-resources] :as context} url version]
   (or (some-> tx-resources (find-in-tx-resources url version))
       (c/find context url version))))

(defn- extension-filter [url]
  (filter #(= url (:url %))))

(defn extension-params
  {:arglists '([value-set])}
  [{{extensions :extension} :compose}]
  {:fhir/type :fhir/Parameters
   :parameter
   (into
    []
    (comp
     (extension-filter "http://hl7.org/fhir/tools/StructureDefinion/valueset-expansion-param")
     (map
      (fn [{extensions :extension}]
        (reduce
         (fn [param {:keys [url value]}]
           (condp = url
             "name" (assoc param :name (type/string (:value value)))
             "value" (assoc param :value value)
             param))
         {:fhir/type :fhir.Parameters/parameter}
         extensions))))
    extensions)})

(defn display-language-param
  {:arglists '([value-set])}
  [{:keys [language]}]
  (when language
    {:display-languages [(:value language)]}))
