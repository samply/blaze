(ns blaze.terminology-service.local.lookup
  (:require
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]))

(defn- designation-param [{:keys [language use additionalUse value]}]
  ["language" language
   "use" use
   "additionalUse" additionalUse
   "value" value])

(defn- property-param [{:keys [code value description source]}]
  ["code" code
   "value" value
   "description" description
   "source" source])

(defn parameters-from-concept
  {:arglists '([code-system concept])}
  [{:keys [name version]}
   {:keys [display definition designation property]}]

  (fu/parameters
   "name" name
   "version" version
   "display" (if (nil? display) nil (type/string display))
   "definition" definition
   "designation" (map designation-param designation)
   "property" (map property-param property)))
