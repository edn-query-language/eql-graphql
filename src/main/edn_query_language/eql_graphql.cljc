(ns edn-query-language.eql-graphql
  "Tools to convert the EQL expressions to GraphQL strings."
  (:require
    #?(:clj [clojure.data.json :as json])
    [clojure.string :as str]
    [edn-query-language.core :as eql]))

(def ^:dynamic *unbounded-recursion-count* 5)

(defn pad-depth [depth]
  (str/join (repeat depth "  ")))

(defn has-call? [children]
  (->> children
       (filter (fn [{:keys [type]}] (= :call type)))
       first boolean))

(defn stringify [x]
  #?(:clj  (json/write-str (cond-> x
                             (uuid? x) str))
     :cljs (js/JSON.stringify (cond-> (clj->js x)
                                (uuid? x) str))))

(defn params->graphql
  ([x js-name] (params->graphql x js-name true))
  ([x js-name root?]
   (cond
     (map? x)
     (let [params (->> (into [] (map (fn [[k v]] (str (js-name k) ": " (params->graphql v js-name false)))) x)
                       (str/join ", "))]
       (if root?
         (str "(" params ")")
         (str "{" params "}")))

     (sequential? x)
     (str "[" (str/join ", " (mapv #(params->graphql % js-name false) x)) "]")

     (symbol? x)
     (name x)

     :else
     (stringify x))))

(defn ident->alias
  "Convert ident like [:Contact/by-id 123] to an usable GraphQL alias (eg: _COLON_Contact_SLASH_by_id_123)."
  [[base value]]
  (let [value (if (vector? value) (str/join "_" value) value)]
    (-> (str base "_" value) (str/replace #"[^a-zA-Z0-9_]" "_"))))

(defn ident-transform [[key value]]
  (let [fields (if-let [field-part (name key)]
                 (str/split field-part #"-and-|And") ["id"])
        value  (if (vector? value) value [value])]
    (if-not (= (count fields) (count value))
      (throw (ex-info "The number of fields on value needs to match the entries" {:key key :value value})))
    {::selector (-> (namespace key) (str/split #"\.") last)
     ::params   (zipmap fields value)}))

(defn group-inline-unions [children]
  (let [{general nil :as groups} (group-by #(get-in % [:params ::on]) children)
        groups (->> (dissoc groups nil)
                    (into [] (map (fn [[k v]] {:type      :union-entry
                                               :union-key k
                                               :children  (mapv #(update % :params dissoc ::on) v)}))))]
    (concat general groups)))

(def special-params #{::on ::alias})

(defn node->graphql [{:keys  [type children key dispatch-key params union-key query]
                      ::keys [js-name depth ident-transform parent-children]
                      :or    {depth 0}}]
  (letfn [(continue
            ([x] (continue x inc))
            ([x depth-iterate]
             (node->graphql (assoc x ::depth (depth-iterate depth)
                              ::parent-children (or (::parent-children x) children)
                              ::js-name js-name
                              ::ident-transform ident-transform))))]
    (let [{::keys [alias]} params
          params (apply dissoc params special-params)]
      (case type
        :root
        (str (if (has-call? children) "mutation " "query ")
             "{\n" (str/join (map continue (group-inline-unions children))) "}\n")

        :join
        (if (= 0 query)
          ""
          (let [header   (if (vector? key)
                           (assoc (ident-transform key)
                             ::index (ident->alias key))
                           {::index    alias
                            ::selector dispatch-key
                            ::params   nil})
                params   (merge (::params header) params)
                children (cond
                           (= '... query)
                           (let [parent (-> (eql/update-child {:children parent-children} key assoc :query (dec *unbounded-recursion-count*))
                                            :children)]
                             (mapv #(assoc % ::parent-children parent) parent))

                           (pos-int? query)
                           (let [parent (-> (eql/update-recursive-depth {:children parent-children} key dec)
                                            :children)]
                             (mapv #(assoc % ::parent-children parent) parent))

                           :else
                           children)]
            (str (pad-depth depth)
                 (if (::index header) (str (::index header) ": "))
                 (js-name (::selector header)) (if (seq params) (params->graphql params js-name)) " {\n"
                 (str/join (map continue (group-inline-unions children)))
                 (pad-depth depth) "}\n")))

        :call
        (let [{::keys [mutate-join]} params
              children (->> (or (some-> mutate-join eql/query->ast :children)
                                children)
                            (remove (comp #{'*} :key)))]
          (str (pad-depth depth) (js-name dispatch-key)
               (if (seq params) (params->graphql (dissoc params ::mutate-join) js-name))
               (if (seq children)
                 (str
                   " {\n"
                   (str/join (map continue (group-inline-unions children)))
                   (pad-depth depth)
                   "}\n"))))

        :union
        (str (pad-depth depth) "__typename\n"
             (str/join (map #(continue % identity) children)))

        :union-entry
        (str (pad-depth depth) "... on " (if (string? union-key) union-key (js-name union-key)) " {\n"
             (str/join (map continue children))
             (pad-depth depth) "}\n")

        :prop
        (str (pad-depth depth)
             (if alias (str alias ": "))
             (js-name dispatch-key)
             (if (seq params) (params->graphql params js-name))
             "\n")))))

(defn ast->graphql
  ([ast] (ast->graphql ast {}))
  ([ast options]
   (node->graphql
     (merge
       ast
       {::js-name         name
        ::ident-transform ident-transform
        ::parent-children (:children ast)}
       options))))

(defn query->graphql
  "Convert query from EDN format to GraphQL string."
  ([query] (query->graphql query {}))
  ([query options]
   (let [ast (eql/query->ast query)]
     (ast->graphql ast options))))

(comment
  (str/join (repeat 1 "  "))
  (println (query->graphql '[({:all [:id :name]}
                              {:last "csaa"})] {}))
  (ident-transform [:Counter/by-id 123])
  (println (query->graphql [{[:Counter/by-id 123] [:a :b]}])))
