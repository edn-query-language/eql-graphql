(defproject edn-query-language/eql-graphql "2021.03.01"
  :description ""
  :url "https://github.com/wilkerlucio/"
  :min-lein-version "2.7.0"
  :license {:name "Eclipse Public License - v 2.0"
            :url  "https://opensource.org/licenses/EPL-2.0"}
  :dependencies [[com.fulcrologic/guardrails "1.1.3"]
                 [edn-query-language/eql "2021.02.28"]
                 [org.clojure/data.json "0.2.6"]]

  :source-paths ["src/main" "resources"]

  :jar-exclusions [])
