(ns blaze.terminology-service.local.code-system.sct.context
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.fhir.spec.type :as type]
   [clojure.string :as str])
  (:import
   [com.google.common.base CaseFormat]
   [java.nio.file Files Path]
   [java.time LocalDate]
   [java.time.format DateTimeFormatter]
   [java.util UUID]
   [java.util.stream Stream]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:private ^:const ^long fully-specified-name 900000000000003001)
(def ^:private ^:const ^long is-a 116680003)
(def ^:private ^:const ^long model-component-module 900000000000012004)
(def ^:private ^:const ^long core-module 900000000000207008)
(def ^:private ^:const ^long german-module 11000274103)

(def ^:const ^String url "http://snomed.info/sct")
(def ^:const ^String core-version-prefix (str url "/" core-module))

(defn- pascal->kebab [s]
  (.to CaseFormat/UPPER_CAMEL CaseFormat/LOWER_HYPHEN s))

(defn- string-arg [i]
  `(.substring ~'line ~(if (pos? (long i)) `(inc ~(symbol (str "t" (dec (long i))))) 0) ~(symbol (str "t" i))))

(defmacro defparseline [constructor types]
  `(defn- ~(symbol (pascal->kebab (name constructor))) [~(with-meta 'line {:tag `String})]
     (let
      ~(into
        []
        (mapcat
         (fn [i]
           [(symbol (str "t" i))
            (conj (when (pos? (long i)) `((inc ~(symbol (str "t" (dec (long i))))))) 9 'line '.indexOf)]))
        (range (dec (count types))))
       (new ~constructor
            ~@(map-indexed
               (fn [i type]
                 (condp = type
                   'long
                   `(Long/parseLong ~'line ~(if (pos? (long i)) `(inc ~(symbol (str "t" (dec (long i))))) 0) ~(symbol (str "t" i)) 10)
                   'String
                   (string-arg i)
                   'UUID
                   `(UUID/fromString ~(string-arg i))))
               (butlast types))
            (Long/parseLong ~'line (inc ~(symbol (str "t" (- (count types) 2)))) (.length ~'line) 10)))))

;; https://confluence.ihtsdotools.org/display/DOCRELFMT/4.2.1+Concept+File+Specification
(defrecord ConceptLine
           [^long id ^long effective-time ^long active ^long module-id
            ^long definition-status-id])

(declare concept-line)
(defparseline ConceptLine [long long long long long])

;; https://confluence.ihtsdotools.org/display/DOCRELFMT/4.2.2.+Description+File+Specification
(defrecord DescriptionLine
           [^long id ^long effective-time ^long active ^long module-id
            ^long concept-id ^String language-code ^long type-id ^String term
            ^long case-significance-id])

(declare description-line)
(defparseline DescriptionLine [long long long long long String long String long])

;; https://confluence.ihtsdotools.org/display/DOCRELFMT/4.2.3+Relationship+File+Specification
(defrecord RelationshipLine
           [^long id ^long effective-time ^long active ^long module-id
            ^long source-id ^long destination-id ^long relationship-group
            ^long type-id ^long characteristic-type-id ^long modifier-id])

(declare relationship-line)
(defparseline RelationshipLine [long long long long long long long long long long])

;; https://confluence.ihtsdotools.org/display/DOCRELFMT/5.2.4.2+Module+Dependency+Reference+Set
(defrecord ModuleDependencyRefsetLine
           [^UUID id ^long effective-time ^long active ^long module-id
            ^long refset-id ^long referenced-component-id
            ^long source-effective-time ^long target-effective-time])

(declare module-dependency-refset-line)
(defparseline ModuleDependencyRefsetLine [UUID long long long long long long long])

(def ^:private assoc-time-map
  (fnil assoc (sorted-map-by >)))

(def ^:private update-time-map
  (fnil update (sorted-map-by >)))

(defn build-description-index [lines]
  (-> ^Stream lines
      (.map description-line)
      (.filter #(= fully-specified-name (:type-id %)))
      (.reduce
       {}
       (fn [index {:keys [concept-id effective-time active term]}]
         (update index concept-id update-time-map effective-time assoc (= 1 active) term))
       (partial merge-with (partial merge-with merge)))))

(defn find-description [description-index concept-id version]
  (when-let [versions (get description-index concept-id)]
    (some-> (first (subseq versions >= version)) val (get true))))

(defn build-child-index
  "module -> concept -> sorted map of version info

  sorted map of version info:
   * keyed by days-since-epoch (higher days come first)
   * values are maps of added and removed children

  example:
  {module-a {concept-a {days-1 {true (child-a) false (child-b)}
                        days-2 {true (child-b)}}}"
  [lines]
  (-> ^Stream lines
      (.map relationship-line)
      (.filter #(= is-a (:type-id %)))
      (.reduce
       {}
       (fn [index {:keys [module-id destination-id effective-time active source-id]}]
         (update-in index [module-id destination-id]
                    update-time-map effective-time
                    update (= 1 active) (fnil conj []) source-id))
       (partial merge-with (partial merge-with (partial merge-with (partial merge-with into)))))))

(defn neighbors
  "Returns a set of neighbors (parents or children) of concept in a module of a
  certain version."
  [index module-id version concept-id]
  (when-let [versions (get-in index [module-id concept-id])]
    (reduce
     (fn [parents {added true removed false}]
       (reduce conj (reduce disj parents removed) added))
     #{}
     (vals (rsubseq versions >= version)))))

(defn transitive-neighbors
  "Returns a set of transitive neighbors (parents or children) of concept
  excluding itself in a module of a certain version."
  [index module-id version concept-id]
  (loop [to-visit #{concept-id}
         visited #{}
         result #{}]
    (if (empty? to-visit)
      result
      (let [current (first to-visit)
            neighbors (neighbors index module-id version current)]
        (recur (into (disj to-visit current) (remove visited) neighbors)
               (conj visited current)
               (into result neighbors))))))

(defn transitive-neighbors-including
  "Returns a set of transitive neighbors (parents or children) of concept
  including itself in a module of a certain version."
  [index module-id version concept-id]
  (loop [to-visit #{concept-id}
         visited #{}
         result #{concept-id}]
    (if (empty? to-visit)
      result
      (let [current (first to-visit)
            neighbors (neighbors index module-id version current)
            new-neighbors (remove visited neighbors)]
        (recur (into (disj to-visit current) new-neighbors)
               (conj visited current)
               (into result neighbors))))))

(defn build-concept-index
  "module -> concept -> list versions ordered by time with active"
  [lines]
  (-> ^Stream lines
      (.map concept-line)
      (.reduce
       {}
       (fn [index {:keys [module-id id effective-time active]}]
         (update-in index [module-id id] assoc-time-map effective-time (= 1 active)))
       (partial merge-with (partial merge-with merge)))))

(defn find-concept
  "Searches a concept in a module of a certain version.

  Returns `true` if the concept is active, `false` if it is inactive and `nil`
  if it doesn't exist."
  [concept-index module-id version concept-id]
  (when-let [versions (get-in concept-index [module-id concept-id])]
    (some-> (first (subseq versions >= version)) val)))

(defn- version-url [module-id date]
  (format "http://snomed.info/sct/%s/version/%s" module-id date))

(defn- build-code-systems
  "Generates a list of CodeSystem resources based on the module dependency
  refset file in `path`.

  Here only the international (900000000000207008) and the German (11000274103)
  edition."
  [description-index lines]
  (-> ^Stream lines
      (.map module-dependency-refset-line)
      (.filter #(= 1 (:active %)))
      (.filter #(= model-component-module (:referenced-component-id %)))
      (.filter #(contains? #{core-module german-module} (:module-id %)))
      (.map
       (fn [{:keys [module-id source-effective-time]}]
         {:fhir/type :fhir/CodeSystem
          :url #fhir/uri"http://snomed.info/sct"
          :version (type/string (version-url module-id source-effective-time))
          :title (type/string (find-description description-index module-id source-effective-time))
          :status #fhir/code"active"
          :experimental #fhir/boolean false
          :date (type/dateTime (str (LocalDate/parse (str source-effective-time) DateTimeFormatter/BASIC_ISO_DATE)))
          :caseSensitive #fhir/boolean true
          :hierarchyMeaning #fhir/code"is-a"
          :versionNeeded #fhir/boolean false
          :content #fhir/code"not-present"
          :filter
          [{:fhir/type :fhir.CodeSystem/filter
            :code #fhir/code"concept"
            :description #fhir/string"Includes all concept ids that have a transitive is-a relationship with the code provided as the value."
            :operator [#fhir/code"is-a"]
            :value #fhir/string"A SNOMED CT code"}
           {:fhir/type :fhir.CodeSystem/filter
            :code #fhir/code"concept"
            :description #fhir/string"Includes all concept ids that have a transitive is-a relationship with the code provided as the value, excluding the code itself."
            :operator [#fhir/code"descendent-of"]
            :value #fhir/string"A SNOMED CT code"}]}))
      (.toList)))

(defn- find-file [path prefix]
  (with-open [stream (Files/newDirectoryStream ^Path path (str prefix "*"))]
    (or (first stream)
        (ba/not-found (format "Can't find a file starting with `%s` in `%s`." prefix path)))))

(defn- stream-file [f path & args]
  (with-open [lines (-> (Files/lines ^Path path) (.skip 1) (.parallel))]
    (apply f lines args)))

(defn- find-current-int-system [code-systems]
  (last (sort-by :date (filter #(str/starts-with? (type/value (:version %)) (str "http://snomed.info/sct/" core-module)) code-systems))))

(defn build [release-path]
  (when-ok [full-path (find-file release-path "Full")
            refset-path (find-file full-path "Refset")
            metadata-path (find-file refset-path "Metadata")
            module-dependency-file (find-file metadata-path "der2_ssRefset_ModuleDependencyFull")
            term-path (find-file full-path "Terminology")
            concept-file (find-file term-path "sct2_Concept_Full")
            relationship-file (find-file term-path "sct2_Relationship_Full")
            description-file (find-file term-path "sct2_Description_Full")
            description-index (stream-file build-description-index description-file)
            code-systems (stream-file (partial build-code-systems description-index) module-dependency-file)]
    {:code-systems code-systems
     :current-int-system (find-current-int-system code-systems)
     :concept-index (stream-file build-concept-index concept-file)
     :child-index (stream-file build-child-index relationship-file)
     :description-index description-index}))