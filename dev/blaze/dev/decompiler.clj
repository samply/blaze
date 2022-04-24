(ns blaze.dev.decompiler
  (:require [clojure.test :refer :all]))


(comment
  (require '[clj-java-decompiler.core :refer [decompile]])

  (binding [*unchecked-math* :warn-on-boxed
            *compiler-options* {:direct-linking true}]
    (decompile
      ))
  )
