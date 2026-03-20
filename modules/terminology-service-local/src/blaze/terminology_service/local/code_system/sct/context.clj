(ns blaze.terminology-service.local.code-system.sct.context
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.terminology-service.local.code-system.sct.util :as sct-u]
   [blaze.util :refer [str]]
   [clojure.string :as str])
  (:import
   [com.google.common.base CaseFormat]
   [java.nio.file Files Path]
   [java.util UUID]
   [java.util.stream Stream]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:private ^:const ^long fully-specified-name 900000000000003001)
(def ^:private ^:const ^long synonym 900000000000013009)
(def ^:private ^:const ^long is-a 116680003)
(def ^:private ^:const ^long model-component-module 900000000000012004)
(def ^:private ^:const ^long core-module 900000000000207008)
(def ^:const ^String core-version-prefix (str sct-u/url "/" core-module))

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

;; https://confluence.ihtsdotools.org/display/DOCRELFMT/5.2.2.1+Language+Reference+Set
(defrecord LanguageRefsetLine
           [^UUID id ^long effective-time ^long active ^long module-id
            ^long refset-id ^long referenced-component-id
            ^long acceptability-id])

(declare language-refset-line)
(defparseline LanguageRefsetLine [UUID long long long long long long])

(def ^:private assoc-time-map
  (fnil assoc (sorted-map-by >)))

(def ^:private update-time-map
  (fnil update (sorted-map-by >)))

(defn build-fully-specified-name-index [lines]
  (-> ^Stream lines
      (.map description-line)
      (.filter #(= fully-specified-name (:type-id %)))
      (.reduce
       {}
       (fn [index {:keys [module-id concept-id effective-time active term]}]
         (update-in index [module-id concept-id] update-time-map effective-time assoc (= 1 active) term))
       (partial merge-with (partial merge-with (partial merge-with merge))))))

(defn build-synonym-index [lines]
  (-> ^Stream lines
      (.map description-line)
      (.filter #(= synonym (:type-id %)))
      (.reduce
       {}
       (fn [index {:keys [id module-id concept-id effective-time active language-code term]}]
         (update-in index [module-id concept-id] update-time-map effective-time update (= 1 active) (fnil conj []) [id [language-code term]]))
       (partial merge-with (partial merge-with (partial merge-with (partial merge-with into)))))))

(defn build-parent-index
  "module -> concept -> sorted map of version info

  sorted map of version info:
   * keyed by effective-time
   * values are maps of added and removed children

  example:
  {module-a {concept-a {effective-time-2 {true (child-a) false (child-b)}
                        effective-time-1 {true (child-b)}}}"
  [lines]
  (-> ^Stream lines
      (.map relationship-line)
      (.filter #(= is-a (:type-id %)))
      (.reduce
       {}
       (fn [index {:keys [module-id destination-id effective-time active source-id]}]
         (update-in index [module-id source-id]
                    update-time-map effective-time
                    update (= 1 active) (fnil conj []) destination-id))
       (partial merge-with (partial merge-with (partial merge-with (partial merge-with into)))))))

(defn build-child-index
  "module -> concept -> sorted map of version info

  sorted map of version info:
   * keyed by effective-time
   * values are maps of added and removed children

  example:
  {module-a {concept-a {effective-time-2 {true (child-a) false (child-b)}
                        effective-time-1 {true (child-b)}}}"
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

(defn- find-dependencies
  "Returns a list of `[module-id version]` tuples of dependencies from module
  with `module-id` in `version`."
  [module-dependency-index module-id version]
  (when-let [versions (get module-dependency-index module-id)]
    (some-> (first (subseq versions >= version)) val)))

(defn- neighbors* [index module-id version concept-id]
  (when-let [versions (get-in index [module-id concept-id])]
    (reduce
     (fn [parents {added true removed false}]
       (reduce conj (reduce disj parents removed) added))
     #{}
     (vals (rsubseq versions >= version)))))

(defn neighbors
  "Returns a set of neighbors (parents or children depending on `index`) of
  concept in a module of a certain version."
  [module-dependency-index index module-id version concept-id]
  (into
   (or (neighbors* index module-id version concept-id) #{})
   (mapcat
    (fn [[module-id version]]
      (neighbors* index module-id version concept-id)))
   (find-dependencies module-dependency-index module-id version)))

(defn transitive-neighbors
  "Returns a set of transitive neighbors (parents or children depending on
  `index`) of concept excluding itself in a module of a certain version."
  [module-dependency-index index module-id version concept-id]
  (loop [to-visit #{concept-id}
         visited #{}
         result #{}]
    (if (empty? to-visit)
      result
      (let [current (first to-visit)
            neighbors (neighbors module-dependency-index index module-id version
                                 current)]
        (recur (into (disj to-visit current) (remove visited) neighbors)
               (conj visited current)
               (into result neighbors))))))

(defn transitive-neighbors-including
  "Returns a set of transitive neighbors (parents or children depending on
  `index`) of concept including itself in a module of a certain version."
  [module-dependency-index index module-id version concept-id]
  (loop [to-visit #{concept-id}
         visited #{}
         result #{concept-id}]
    (if (empty? to-visit)
      result
      (let [current (first to-visit)
            neighbors (neighbors module-dependency-index index module-id version
                                 current)]
        (recur (into (disj to-visit current) (remove visited) neighbors)
               (conj visited current)
               (into result neighbors))))))

(defn find-transitive-neighbor
  "Returns true if concept is in the set of transitive neighbors (parents or
  children depending on `index`) of start-concept in a module of a certain
  version."
  [module-dependency-index index module-id version start-concept-id concept-id]
  (loop [to-visit #{start-concept-id}
         visited #{}]
    (when (seq to-visit)
      (let [current (first to-visit)
            neighbors (neighbors module-dependency-index index module-id version
                                 current)]
        (if (contains? neighbors concept-id)
          true
          (recur (into (disj to-visit current) (remove visited) neighbors)
                 (conj visited current)))))))

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

(defn- find-concept* [concept-index module-id version concept-id]
  (when-let [versions (get-in concept-index [module-id concept-id])]
    (some-> (first (subseq versions >= version)) val)))

(defn find-multi-module [module-dependency-index index f module-id version concept-id]
  (if-some [res (f index module-id version concept-id)]
    res
    (loop [[[module-id version] & more] (find-dependencies module-dependency-index module-id version)]
      (when module-id
        (if-some [res (find-multi-module module-dependency-index index f module-id version concept-id)]
          res
          (recur more))))))

(defn find-concept
  "Searches a concept in a module of a certain version.

  Returns `true` if the concept is active, `false` if it is inactive and `nil`
  if it doesn't exist."
  [module-dependency-index concept-index module-id version concept-id]
  (find-multi-module module-dependency-index concept-index find-concept*
                     module-id version concept-id))

(defn find-fully-specified-name* [description-index module-id version concept-id]
  (when-let [versions (get-in description-index [module-id concept-id])]
    (some-> (first (subseq versions >= version)) val (get true))))

(defn find-fully-specified-name
  "Returns the fully specified name of a concept in a module of a certain
  version."
  [module-dependency-index fully-specified-name-index module-id version concept-id]
  (find-multi-module module-dependency-index fully-specified-name-index
                     find-fully-specified-name* module-id version concept-id))

(defn- assoc-tuple [m [k v]]
  (assoc m k v))

(defn- dissoc-tuple [m [k]]
  (dissoc m k))

(defn- find-synonyms* [description-index module-id version concept-id]
  (when-let [versions (get-in description-index [module-id concept-id])]
    (vec
     (reduce
      (fn [result {added true removed false}]
        (reduce assoc-tuple (reduce dissoc-tuple result removed) added))
      {}
      (vals (rsubseq versions >= version))))))

(defn find-synonyms
  "Returns the synonyms of a concept in a module of a certain version.

  A synonym is a tuple of language code and term."
  [module-dependency-index synonym-index module-id version concept-id]
  (into
   (or (find-synonyms* synonym-index module-id version concept-id) [])
   (mapcat
    (fn [[module-id version]]
      (find-synonyms* synonym-index module-id version concept-id)))
   (find-dependencies module-dependency-index module-id version)))

(defn find-acceptability [acceptability-index version id]
  (when-let [versions (get acceptability-index id)]
    (some-> (first (subseq versions >= version)) val)))

(defn build-module-dependency-index
  "source-module -> source-version -> target-module -> target-version"
  [lines]
  (-> ^Stream lines
      (.map module-dependency-refset-line)
      (.filter #(= 1 (:active %)))
      (.reduce
       {}
       (fn [index
            {:keys [module-id source-effective-time
                    referenced-component-id target-effective-time]}]
         (update index module-id update-time-map source-effective-time
                 (fnil conj []) [referenced-component-id target-effective-time]))
       (partial merge-with (partial merge-with into)))))

(defn- acceptability [acceptability-id]
  (case acceptability-id
    900000000000548007 :preferred
    900000000000549004 :acceptable))

(defn build-acceptability-index
  "referenced-component-id -> effective-time -> acceptability-id"
  [lines]
  (-> ^Stream lines
      (.map language-refset-line)
      (.filter #(= 1 (:active %)))
      (.reduce
       {}
       (fn [index
            {:keys [referenced-component-id effective-time acceptability-id]}]
         (update index referenced-component-id assoc-time-map effective-time
                 (acceptability acceptability-id)))
       (partial merge-with merge))))

(def ^:private published-release-versions
  "This map contains the known published release versions by module. Only
  CodeSystem resources with that versions are created given that the data from
  the release files is available.

  The versions are taken from: https://mlds.ihtsdotools.org/api/releasePackages
  using the script `sct-release-versions.sh`."
  {11000279109 [20250127],
   11000221109 [20181130
                20190531
                20191130
                20200531
                20201130
                20210531
                20211130
                20220531
                20221130
                20230531
                20231130
                20240531
                20241130
                20241130
                20250531
                20250720
                20250820
                20251120
                20260220],
   784009001 [20230731 20240701 20250701],
   816211006 [20230430 20240430 20250430],
   5631000179106 [20251215],
   11000210104 [20231001 20240401 20241001 20250401 20251001],
   890195008 [20230731 20240701 20250701],
   332351000009108 [20250930],
   715152001 [20220731 20240101 20250101],
   11000318109 [20240725 20250328],
   11000172109 [20231115
                20240515
                20241115
                20241215
                20250215
                20250315
                20250515
                20250615
                20250715
                20250915
                20251015
                20251115
                20251215
                20260215
                20260315],
   827022005 [20230731 20240701 20250701],
   11000234105 [20230815 20240215 20240815 20250215 20250815 20251115 20251215 20260315],
   11000274103 [20231115 20240515 20241115 20250515 20251115],
   11000146104 [20230930
                20240331
                20240930
                20241031
                20241130
                20250131
                20250228
                20250331
                20250430
                20250531
                20250630
                20250831
                20250930
                20251031
                20251130
                20260131
                20260228],
   450829007 [20230430 20230930 20240331 20240930 20250510 20251110 20260210],
   45991000052106 [20231130 20240531 20241130 20250531 20251130],
   1303956008 [20240101 20250101],
   900000000000207008 [20220131
                       20221231
                       20230131
                       20230228
                       20230331
                       20230430
                       20230531
                       20230630
                       20230731
                       20230901
                       20231001
                       20231101
                       20231201
                       20240101
                       20240301
                       20240401
                       20240501
                       20240601
                       20240701
                       20240801
                       20240901
                       20241001
                       20241101
                       20241201
                       20250101
                       20250201
                       20250301
                       20250401
                       20250501
                       20250601
                       20250701
                       20250801
                       20250901
                       20251001
                       20251101
                       20251201
                       20260101
                       20260201
                       20260301],
   2011000195101 [20230607 20231207 20240607 20241207 20250607 20251207],
   51000202101 [20231015
                20240415
                20240515
                20240915
                20241015
                20241115
                20241215
                20250215
                20250315
                20250415
                20250515
                20250615
                20250715
                20250915
                20251015
                20251115
                20251215
                20260215
                20260315],
   1157359004 [20220731 20240101 20250101],
   11000220105 [20231021 20240421 20241021 20250421 20251021 20251121 20251221 20260121 20260221],
   718292005 [20230731 20240701 20250701],
   554471000005108 [20230930 20240331 20240930 20250331 20250930],
   721230008 [20230731 20240701 20250701]})

(defn- module-max-effective-times! [lines]
  (stream-transduce!
   (comp
    (map module-dependency-refset-line)
    (filter (comp #{1} :active))
    (filter (comp #{model-component-module} :referenced-component-id)))
   (completing
    (fn [r {:keys [effective-time module-id]}]
      (update r module-id (fnil max 0) effective-time)))
   {}
   lines))

(defn- module-versions [max-effective-times]
  (reduce-kv
   (fn [r module-id effective-time]
     (if-some [versions (published-release-versions module-id)]
       (if-some [versions (subseq (apply sorted-set versions) <= effective-time)]
         (assoc r module-id versions)
         r)
       r))
   {} max-effective-times))

(defn- create-code-system
  [module-dependency-index fully-specified-name-index module-id version]
  (sct-u/create-code-system module-id version
                            (find-fully-specified-name module-dependency-index
                                                       fully-specified-name-index
                                                       module-id version
                                                       module-id)))

(defn- build-code-systems
  "Generates a list of CodeSystem resources based on the `lines` of the module
  dependency refset file."
  [module-dependency-index fully-specified-name-index lines]
  (into
   []
   (mapcat
    (fn [[module-id versions]]
      (map (partial create-code-system module-dependency-index fully-specified-name-index module-id) versions)))
   (module-versions (module-max-effective-times! lines))))

(defn- find-file [path prefix]
  (with-open [stream (Files/newDirectoryStream ^Path path (str prefix "*"))]
    (or (first stream)
        (ba/not-found (format "Can't find a file starting with `%s` in `%s`." prefix path)))))

(defn stream-file [f path & args]
  (with-open [lines (-> (Files/lines ^Path path) (.skip 1) (.parallel))]
    (apply f lines args)))

(defn- has-core-version? [{:keys [version]}]
  (str/starts-with? (:value version) core-version-prefix))

(defn- find-current-int-system [code-systems]
  (last (sort-by (comp :value :date) (filter has-core-version? code-systems))))

(defn build [release-path]
  (when-ok [full-path (find-file release-path "Full")
            refset-path (find-file full-path "Refset")
            language-path (find-file refset-path "Language")
            metadata-path (find-file refset-path "Metadata")
            language-file (find-file language-path "der2_cRefset_LanguageFull")
            module-dependency-file (find-file metadata-path "der2_ssRefset_ModuleDependencyFull")
            term-path (find-file full-path "Terminology")
            concept-file (find-file term-path "sct2_Concept_Full")
            relationship-file (find-file term-path "sct2_Relationship_Full")
            description-file (find-file term-path "sct2_Description_Full")
            module-dependency-index (stream-file build-module-dependency-index module-dependency-file)
            fully-specified-name-index (stream-file build-fully-specified-name-index description-file)
            synonym-index (stream-file build-synonym-index description-file)
            code-systems (stream-file (partial build-code-systems module-dependency-index fully-specified-name-index) module-dependency-file)]
    {:code-systems code-systems
     :current-int-system (find-current-int-system code-systems)
     :module-dependency-index module-dependency-index
     :concept-index (stream-file build-concept-index concept-file)
     :parent-index (stream-file build-parent-index relationship-file)
     :child-index (stream-file build-child-index relationship-file)
     :fully-specified-name-index fully-specified-name-index
     :synonym-index synonym-index
     :acceptability-index (stream-file build-acceptability-index language-file)}))
