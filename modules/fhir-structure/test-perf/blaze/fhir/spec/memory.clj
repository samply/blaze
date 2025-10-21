(ns blaze.fhir.spec.memory
  (:import
   [blaze.fhir.spec.type ExtensionData]
   [clojure.lang PersistentVector]
   [java.time ZoneOffset]
   [org.openjdk.jol.info ClassLayout GraphLayout]))

(defn graph-layout ^GraphLayout [& roots]
  (GraphLayout/parseInstance (object-array roots)))

(defn class-layout ^ClassLayout [x]
  (ClassLayout/parseInstance x))

(defn total-size [& roots]
  (-> (apply graph-layout roots)
      (.subtract (graph-layout ExtensionData/EMPTY))
      (.subtract (graph-layout PersistentVector/EMPTY_NODE))
      (.subtract (graph-layout PersistentVector/EMPTY))
      (.subtract (graph-layout ZoneOffset/UTC))
      (.totalSize)))

(defn total-size-exclude [x & excludes]
  (-> (graph-layout x)
      (.subtract (graph-layout ExtensionData/EMPTY))
      (.subtract (graph-layout PersistentVector/EMPTY_NODE))
      (.subtract (graph-layout PersistentVector/EMPTY))
      (.subtract (graph-layout ZoneOffset/UTC))
      (.subtract (apply graph-layout excludes))
      (.totalSize)))

(defn print-layout [& roots]
  (-> (apply graph-layout roots)
      (.subtract (graph-layout ExtensionData/EMPTY))
      (.subtract (graph-layout PersistentVector/EMPTY_NODE))
      (.subtract (graph-layout PersistentVector/EMPTY))
      (.subtract (graph-layout ZoneOffset/UTC))
      (.toPrintable)))

(defn print-footprint [x & excludes]
  (-> (graph-layout x)
      (.subtract (graph-layout ExtensionData/EMPTY))
      (.subtract (graph-layout PersistentVector/EMPTY_NODE))
      (.subtract (graph-layout PersistentVector/EMPTY))
      (.subtract (graph-layout ZoneOffset/UTC))
      (.subtract (apply graph-layout excludes))
      (.toFootprint)))

(defn print-total-layout [& roots]
  (-> (apply graph-layout roots)
      (.toPrintable)))

(defn print-class-layout [x]
  (-> (class-layout x)
      (.toPrintable)))
