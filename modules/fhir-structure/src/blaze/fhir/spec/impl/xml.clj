(ns blaze.fhir.spec.impl.xml
  (:require
   [blaze.fhir.spec.type :as type]
   [clojure.alpha.spec :as s]
   [clojure.data.xml.name :as xml-name])
  (:import
   [clojure.data.xml.node Element]))

(xml-name/alias-uri 'f "http://hl7.org/fhir")

(set! *warn-on-reflection* true)

(defn element? [x]
  (instance? Element x))

(defn value-matches?
  {:arglists '([regex element])}
  [regex {{:keys [value] :as attrs} :attrs content :content}]
  (or (and (string? value) (.matches (re-matcher regex value)))
      (some? (:id attrs))
      (seq content)))

(defn set-extension-tag [element]
  (some-> element (update :content (partial map #(assoc % :tag ::f/extension)))))

(defn remove-character-content [element]
  (update element :content (partial filter element?)))

(defn primitive-xml-form
  ([constructor]
   `(s/and
     element?
     (s/conformer remove-character-content set-extension-tag)
     (s/schema {:content (s/coll-of :fhir.xml/Extension)})
     (s/conformer ~constructor type/to-xml)))
  ([regex constructor]
   `(s/and
     element?
     (fn [~'e] (value-matches? ~regex ~'e))
     (s/conformer remove-character-content set-extension-tag)
     (s/schema {:content (s/coll-of :fhir.xml/Extension)})
     (s/conformer ~constructor type/to-xml))))
