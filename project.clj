(defproject uno-kata "1.0.0-SNAPSHOT"
  :description "a code kata"
  :url "https://github.com/luontola/uno-kata"

  :dependencies [[com.attendify/schema-refined "0.3.0-alpha4"]
                 [medley "1.3.0"]
                 [org.clojure/clojure "1.10.1"]
                 [prismatic/schema "1.1.12"]]
  :pedantic? :warn
  :min-lein-version "2.9.0"

  :source-paths ["src"]
  :java-source-paths ["src-java"]
  :javac-options ["--release" "11"]
  :test-paths ["test"]
  :resource-paths ["resources"]
  :target-path "target/%s/"
  :global-vars {*warn-on-reflection* true
                *print-namespace-maps* false}
  :jvm-opts ["-XX:-OmitStackTraceInFastThrow"
             "--illegal-access=deny"]

  :plugins [[lein-ancient "0.6.15"]]

  :aliases {"autotest" ["kaocha" "--watch"]
            "kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]}

  :profiles {:dev {:dependencies [[lambdaisland/kaocha "1.0-612"]
                                  [org.clojure/test.check "1.0.0"]
                                  [prismatic/schema-generators "0.1.3"]]
                   :repl-options {:init-ns uno.game}}})
