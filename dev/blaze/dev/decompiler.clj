(ns blaze.dev.decompiler)


(comment
  (require '[clj-java-decompiler.core :refer [decompile]])

  (binding [*unchecked-math* :warn-on-boxed
            *compiler-options* {:direct-linking true}]
    (decompile
      ))
  )
