(ns blaze.fhir.spec.memory
  (:import
   [org.openjdk.jol.info ClassLayout GraphLayout]))

(set! *warn-on-reflection* true)

(defn graph-layout ^GraphLayout [& roots]
  (GraphLayout/parseInstance (object-array roots)))

(defn class-layout ^ClassLayout [x]
  (ClassLayout/parseInstance x))

(defn total-size [x y]
  (-> (graph-layout x y)
      (.subtract (graph-layout x))
      (.totalSize)))

(defn print-footprint [x y]
  (-> (graph-layout x y)
      (.subtract (graph-layout x))
      (.toFootprint)))

(defn print-layout [x y]
  (-> (graph-layout x y)
      (.subtract (graph-layout x))
      (.toPrintable)))

(defn print-total-layout [& roots]
  (-> ^GraphLayout (apply graph-layout roots)
      (.toPrintable)))

(defn print-class-layout [x]
  (-> (class-layout x)
      (.toPrintable)))
