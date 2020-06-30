(defproject api "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/test.check "0.9.0"]
                 [ring "1.8.1"]
                 [metosin/reitit "0.5.2"]
                 [metosin/muuntaja "0.6.7"]
                 ]
  :main ^:skip-aot api.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})