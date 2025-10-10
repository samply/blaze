(ns blaze.fhir.spec.memory
  (:import
   [blaze.fhir.spec.type ExtensionData]
   [clojure.lang PersistentVector]
   [java.time ZoneOffset]
   [org.openjdk.jol.info ClassLayout GraphLayout]))

(set! *warn-on-reflection* true)

(defn graph-layout ^GraphLayout [& roots]
  (GraphLayout/parseInstance (object-array roots)))

(defn class-layout ^ClassLayout [x]
  (ClassLayout/parseInstance x))

(defn total-size [& roots]
  (-> ^GraphLayout (apply graph-layout roots)
      (.subtract (graph-layout ExtensionData/EMPTY))
      (.subtract (graph-layout PersistentVector/EMPTY_NODE))
      (.subtract (graph-layout PersistentVector/EMPTY))
      (.subtract (graph-layout ZoneOffset/UTC))
      (.totalSize)))

(defn total-size* [x y]
  (-> (graph-layout x y)
      (.subtract (graph-layout x))
      (.totalSize)))

(defn total-size-exclude [x & excludes]
  (-> (graph-layout x)
      (.subtract (graph-layout ExtensionData/EMPTY))
      (.subtract (graph-layout PersistentVector/EMPTY_NODE))
      (.subtract (graph-layout PersistentVector/EMPTY))
      (.subtract (graph-layout ZoneOffset/UTC))
      (.subtract (apply graph-layout excludes))
      (.totalSize)))

(defn print-layout [x & excludes]
  (-> (graph-layout x)
      (.subtract (graph-layout ExtensionData/EMPTY))
      (.subtract (graph-layout PersistentVector/EMPTY_NODE))
      (.subtract (graph-layout PersistentVector/EMPTY))
      (.subtract (graph-layout ZoneOffset/UTC))
      (.subtract (apply graph-layout excludes))
      (.toPrintable)))

(defn print-footprint [x & excludes]
  (-> (graph-layout x)
      (.subtract (graph-layout ExtensionData/EMPTY))
      (.subtract (graph-layout PersistentVector/EMPTY_NODE))
      (.subtract (graph-layout PersistentVector/EMPTY))
      (.subtract (graph-layout ZoneOffset/UTC))
      (.subtract (apply graph-layout excludes))
      (.toFootprint)))

(defn print-footprint* [x y]
  (-> (graph-layout x y)
      (.subtract (graph-layout x))
      (.toFootprint)))

(defn print-total-layout [& roots]
  (-> ^GraphLayout (apply graph-layout roots)
      (.toPrintable)))

(defn print-class-layout [x]
  (-> (class-layout x)
      (.toPrintable)))
