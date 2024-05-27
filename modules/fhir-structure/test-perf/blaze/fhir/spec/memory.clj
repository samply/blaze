(ns blaze.fhir.spec.memory
  (:import
   [clojure.lang PersistentVector]
   [org.openjdk.jol.info GraphLayout]))

(defn graph-layout ^GraphLayout [& roots]
  (GraphLayout/parseInstance (object-array roots)))

(defn total-size [& roots]
  (-> (apply graph-layout roots)
      (.subtract (graph-layout (PersistentVector/EMPTY_NODE)))
      (.totalSize)))

(defn print-layout [& roots]
  (-> (apply graph-layout roots)
      (.subtract (graph-layout (PersistentVector/EMPTY_NODE)))
      (.toPrintable)))

(defn print-total-layout [& roots]
  (-> (apply graph-layout roots)
      (.toPrintable)))
