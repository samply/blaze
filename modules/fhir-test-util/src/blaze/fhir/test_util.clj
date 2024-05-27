(ns blaze.fhir.test-util
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-buffer :as bb]
   [blaze.executors :as ex]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.structure-definition-repo]
   [clojure.test :refer [is]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]])
  (:import
   [java.time Clock Instant]
   [java.util Random]
   [java.util.concurrent Executors TimeUnit]))

(set! *warn-on-reflection* true)

(defmacro given-failed-future [future & body]
  `(given (try (deref ~future) (is false) (catch Exception e# (ba/anomaly e#)))
     ~@body))

(defmethod ig/init-key :blaze.test/fixed-clock
  [_ _]
  (time/fixed-clock Instant/EPOCH "UTC"))

(defmethod ig/init-key :blaze.test/offset-clock
  [_ {:keys [clock offset-seconds]}]
  (time/offset-clock clock (time/seconds offset-seconds)))

(defmethod ig/init-key :blaze.test/system-clock
  [_ _]
  (Clock/systemUTC))

(defmethod ig/init-key :blaze.test/fixed-rng-fn
  [_ {:keys [n] :or {n 0}}]
  #(proxy [Random] []
     (nextLong [] n)))

(defmethod ig/init-key :blaze.test/fixed-rng
  [_ _]
  (let [state (atom 0)]
    (proxy [Random] []
      (nextBytes [byte-array]
        (assert (= 20 (count byte-array)))
        (let [bb (bb/wrap byte-array)]
          (bb/set-position! bb 12)
          (bb/put-long! bb (swap! state inc)))))))

(defmethod ig/init-key :blaze.test/executor
  [_ _]
  (Executors/newFixedThreadPool 4))

(defmethod ig/halt-key! :blaze.test/executor
  [_ executor]
  (ex/shutdown! executor)
  (ex/await-termination executor 10 TimeUnit/SECONDS))

(defonce structure-definition-repo
  (:blaze.fhir/structure-definition-repo
   (ig/init {:blaze.fhir/structure-definition-repo {}})))

(defn link-url [body link-relation]
  (->> body :link (filter (comp #{link-relation} :relation)) first :url type/value))
