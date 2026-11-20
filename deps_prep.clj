(ns deps-prep
  "Single-JVM driver that resolves the dependency basis for every Clojure
  module under modules/, downloading all artifacts into ~/.m2/repository and
  checking out all git libs into ~/.gitlibs.

  Used to fill the Maven and GitLibs caches for CI so a single cache entry
  covers every module's :test, :kaocha and :coverage aliases and its :build
  alias without restarting the JVM per module."
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

(defn- create-basis
  "Creates the basis of the project in `project-dir` with `aliases`.

  Resolves deps using a single thread, because concurrent threads procuring
  the same git lib race on cloning and checking it out in ~/.gitlibs."
  [project-dir aliases]
  (println (format "Prepping %s %s" (.getPath ^java.io.File project-dir) aliases))
  (dir/with-dir project-dir
    (deps/create-basis {:project "deps.edn"
                        :aliases aliases
                        :args {:threads 1}})))

(defn- prep-dir
  "Creates the basis of the project in `project-dir` with all available
  candidate aliases and, separately, with the :build alias, because the :deps
  of the :build alias replace the project deps."
  [project-dir]
  (let [available (read-aliases (io/file project-dir "deps.edn"))]
    (create-basis project-dir (filterv available candidate-aliases))
    (when (available :build)
      (create-basis project-dir [:build]))))

(defn run [_]
  (prep-dir (io/file "."))
  (run! prep-dir (module-dirs))
  (println "Done."))
