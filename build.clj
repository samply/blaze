(ns build
  (:require
   [clojure.string :as str]
   [clojure.tools.build.api :as b])
  (:import
   [java.time LocalDate]
   [java.util Properties]
   [java.util.zip ZipEntry ZipFile]))

(set! *warn-on-reflection* true)

(def lib 'samply/blaze)
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s-standalone.jar" (name lib)))

(defn clean [_]
  (b/delete {:path "target"}))

(defn- git
  "Returns the trimmed output of the Git command with `args` or nil if it
  produces no output or Git isn't available at all."
  [& args]
  (try
    (b/git-process {:git-args (vec args)})
    (catch Exception _ nil)))

(defn- write-version-file []
  ;; the only tags used are release tags like `v1.11.0`, so the version is the
  ;; tag HEAD points at without its leading `v`
  (let [version (or (some-> (git "tag" "--points-at" "HEAD") (subs 1))
                    (git "rev-parse" "--short" "HEAD")
                    "unknown")]
    (println "Build version:" version)
    (b/write-file {:path (str class-dir "/blaze/version.edn")
                   :content {:blaze/version version
                             :blaze/release-date (str (LocalDate/now))}})))

(def ^:private maven-libs
  (filter (comp :mvn/version val) (:libs basis)))

(defn- artifact-id
  "Returns the Maven artifact ID of `lib`, whose name can end in `$<classifier>`."
  [lib]
  (first (str/split (name lib) #"\$")))

(defn- pom-properties-path [lib]
  (format "META-INF/maven/%s/%s/pom.properties" (namespace lib) (artifact-id lib)))

(defn- jar-pom-properties-paths
  "Returns the paths of all pom.properties files in the jar at `path`."
  [^String path]
  (with-open [jar (ZipFile. path)]
    (into
     #{}
     (comp (map #(.getName ^ZipEntry %))
           (filter #(re-matches #"META-INF/maven/.+/pom\.properties" %)))
     (enumeration-seq (.entries jar)))))

(defn- write-pom-properties
  "Writes a pom.properties file for every Maven library that no jar brings one
  for.

  The uberjar merges the jars of all libraries, so image scanners can identify
  the libraries only through those files."
  []
  (let [existing (into #{} (comp (mapcat (comp :paths val))
                                 (mapcat jar-pom-properties-paths))
                       maven-libs)]
    (doseq [[lib {:mvn/keys [version]}] maven-libs
            :let [path (pom-properties-path lib)]
            :when (not (existing path))]
      (b/write-file
       {:path (str class-dir "/" path)
        :string (format "groupId=%s\nartifactId=%s\nversion=%s\n"
                        (namespace lib) (artifact-id lib) version)}))))

(defn- pom-properties-version
  "Returns the version in the pom.properties file at `path` of `jar` or nil if
  there is no such file."
  [^ZipFile jar ^String path]
  (when-let [entry (.getEntry jar path)]
    (with-open [in (.getInputStream jar entry)]
      (.getProperty (doto (Properties.) (.load in)) "version"))))

(defn- pom-properties-problem
  "Returns a description of the problem with the pom.properties file of the
  Maven library `lib` in `jar` or nil if it holds the resolved `version`."
  [jar [lib {:mvn/keys [version]}]]
  (let [actual (pom-properties-version jar (pom-properties-path lib))]
    (when (not= version actual)
      (format "%s: expected version %s but was %s" lib version actual))))

(defn check-pom-properties
  "Fails if not every Maven library has a pom.properties file holding its resolved
  version in the uberjar."
  [_]
  (with-open [jar (ZipFile. ^String uber-file)]
    (when-let [problems (seq (keep #(pom-properties-problem jar %) maven-libs))]
      (throw (ex-info (str "Invalid pom.properties files in " uber-file ":\n"
                           (str/join "\n" (sort problems)))
                      {})))))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (write-version-file)
  (write-pom-properties)
  (b/compile-clj {:basis basis
                  :class-dir class-dir
                  :ns-compile
                  '[blaze.admin-api
                    blaze.cache-collector
                    blaze.core
                    blaze.cql
                    blaze.db.kv.mem
                    blaze.db.kv.rocksdb
                    blaze.db.node
                    blaze.db.node.resource-indexer
                    blaze.db.resource-cache
                    blaze.db.resource-store.cassandra
                    blaze.db.resource-store.kv
                    blaze.db.search-param-registry
                    blaze.db.tx-cache
                    blaze.db.tx-log.kafka
                    blaze.db.tx-log.local
                    blaze.elm.expression.cache
                    blaze.fhir.operation.code-system.lookup
                    blaze.fhir.operation.code-system.validate-code
                    blaze.fhir.operation.compact
                    blaze.fhir.operation.cql
                    blaze.fhir.operation.evaluate-measure
                    blaze.fhir.operation.graphql
                    blaze.fhir.operation.graphql.middleware
                    blaze.fhir.operation.totals
                    blaze.fhir.operation.value-set.expand
                    blaze.fhir.operation.value-set.validate-code
                    blaze.fhir.parsing-context
                    blaze.fhir.structure-definition-repo
                    blaze.fhir.writing-context
                    blaze.handler.app
                    blaze.handler.health
                    blaze.http-client
                    blaze.interaction.conditional-delete-type
                    blaze.interaction.create
                    blaze.interaction.delete
                    blaze.interaction.delete-history
                    blaze.interaction.history.instance
                    blaze.interaction.history.system
                    blaze.interaction.history.type
                    blaze.interaction.read
                    blaze.interaction.search-compartment
                    blaze.interaction.search-system
                    blaze.interaction.search-type
                    blaze.interaction.transaction
                    blaze.interaction.update
                    blaze.interaction.vread
                    blaze.job-scheduler
                    blaze.job.async-interaction
                    blaze.job.compact
                    blaze.job.re-index
                    blaze.metrics.handler
                    blaze.metrics.registry
                    blaze.openid-auth
                    blaze.operation.graph
                    blaze.operation.patient.everything
                    blaze.operation.patient.purge
                    blaze.page-id-cipher
                    blaze.page-store.backing-store.cassandra
                    blaze.page-store.caching
                    blaze.page-store.local
                    blaze.rest-api
                    blaze.rest-api.async-status-cancel-handler
                    blaze.rest-api.async-status-handler
                    blaze.rest-api.batch-handler
                    blaze.rest-api.capabilities-handler
                    blaze.scheduler
                    blaze.server
                    blaze.terminology-service.extern
                    blaze.terminology-service.local
                    blaze.terminology-service.local.code-system.loinc
                    blaze.terminology-service.local.code-system.sct
                    blaze.terminology-service.not-available
                    blaze.thread-pool-executor-collector]
                  :compile-opts
                  {:direct-linking true
                   :elide-meta [:doc :file :line :added]}
                  :java-opts
                  ["--enable-native-access=ALL-UNNAMED"]})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'blaze.core
           :exclude
           ["^about.html"
            "^META-INF/versions/\\d+/module-info.class"
            "^HISTORY-JAVA.md"
            "^dse_protocol_v\\d.spec"
            "^native_protocol_v\\d.spec"
            ".*-musl.so$"
            ".*-ppc64le.so$"
            ".*-s390x.so$"
            ".*-linux32.so$"
            ".*-linux-riscv64.so$"
            ".*.dll$"
            ".*.jnilib$"]
           :conflict-handlers
           {"META-INF/io.netty.versions.properties" :append
            :default :warn}}))
