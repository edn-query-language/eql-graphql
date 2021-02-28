# EQL GraphQL

This repository contains helper tools to convert EQL expressions to GraphQL strings.

```clojure
(ns eql-graphql-demo
  (:require
    [edn-query-language.eql-graphql :as eql-gql]))

(eql-gql/query->graphql [{'(:user {:id 123}) [:id :name]}])
; query {
;   user(id: 123) {
;     id
;     name
;   }
; }
```
