(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'samply/blaze)
(def version "0.19.0")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir
                  :compile-opts
                  {:direct-linking true
                   :elide-meta [:doc :file :line :added]}})
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
            ".*.dll$"
            ".*.jnilib$"]
           :conflict-handlers
           {"META-INF/io.netty.versions.properties" :append
            :default :warn}}))
