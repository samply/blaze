(ns deps-prep
  "Single-JVM driver that resolves the dependency basis for every Clojure
  module under modules/, downloading all artifacts into ~/.m2/repository.

  Used to fill the Maven cache for CI so a single cache entry covers every
  module's :test, :kaocha and :coverage aliases without restarting the JVM
  per module."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.tools.deps :as deps]
   [clojure.tools.deps.util.dir :as dir]))

(def ^:private candidate-aliases [:test :kaocha :coverage])

(defn- module-dirs []
  (->> (.listFiles (io/file "modules"))
       (filter #(.isDirectory ^java.io.File %))
       (filter #(.exists (io/file % "deps.edn")))
       sort))

(defn- read-aliases [deps-edn-file]
  (-> deps-edn-file slurp edn/read-string :aliases keys set))

(defn- prep-dir [project-dir]
  (let [deps-edn (io/file project-dir "deps.edn")
        available (read-aliases deps-edn)
        aliases (filterv available candidate-aliases)]
    (println (format "Prepping %s %s" (.getPath project-dir) aliases))
    (dir/with-dir project-dir
      (deps/create-basis {:project "deps.edn"
                          :aliases aliases}))))

(defn run [_]
  (prep-dir (io/file "."))
  (run! prep-dir (module-dirs))
  (println "Done."))
