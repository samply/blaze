(ns blaze.fhir.spec.memory
  (:import
   [org.openjdk.jol.info ClassLayout GraphLayout]))

(set! *warn-on-reflection* true)

(defn graph-layout ^GraphLayout [& roots]
  (GraphLayout/parseInstance (object-array roots)))

(defn class-layout ^ClassLayout [x]
  (ClassLayout/parseInstance x))

(defn total-size
  "Returns the total size of all objects reachable from `y` but not from `x`.

  Uses the arithmetic difference of the two graph sizes in order to allow GC
  moves of objects."
  [x y]
  (- (.totalSize (graph-layout x y))
     (.totalSize (graph-layout x))))

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
