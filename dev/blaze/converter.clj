(ns blaze.converter
  (:require
   [clojure.string :as str])
  (:import
   [ca.uhn.fhir.context FhirContext]
   [java.io File]
   [org.hl7.fhir.convertors.factory VersionConvertorFactory_40_50]
   [org.hl7.fhir.r4.model Bundle Bundle$BundleEntryComponent Resource ResourceType]))

(def r4 (FhirContext/forR4))
(def r5 (FhirContext/forR5))

(defn parse ^Resource [file]
  (let [parser (.newJsonParser ^FhirContext r4)]
    (.parseResource parser (slurp file))))

(defn encode [resource]
  (let [parser (.newJsonParser ^FhirContext r5)]
    (.encodeResourceToString parser resource)))

(defn remove-types [types]
  (remove
   (fn [^Bundle$BundleEntryComponent entry]
     (types (.getResourceType (.getResource entry))))))

(def invalid-types
  #{ResourceType/Device ResourceType/MedicationAdministration ResourceType/ImagingStudy})

(defn convert [file]
  (let [bundle (parse file)
        entries (into [] (remove-types invalid-types) (.getEntry ^Bundle bundle))]
    (->> (.setEntry ^Bundle bundle entries)
         (VersionConvertorFactory_40_50/convertResource)
         (encode)
         (spit (str/replace file "synthea" "synthea/r5")))))

(def synthea ".github/test-data/synthea")

(comment
  (doseq [file (->> (map str (file-seq (File. synthea)))
                    (remove #(str/includes? % "/r5"))
                    (filter #(str/ends-with? % ".json")))]
    (convert file)))
