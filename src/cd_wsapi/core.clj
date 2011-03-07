(ns cd-wsapi.core
  (:use [aleph core http]
        [net.cgrand.moustache]
        [clojure.contrib.sql]
        [org.danlarkin.json]))

(def *server-port* 8080)
(def *default-page* (slurp "resources/index.html"))

;;JSON Encoders
(add-encoder 
  java.util.Date
  (fn [#^java.util.Date date #^java.io.Writer writer
       #^String pad #^String current-indent
       #^String start-token-indent #^Integer indent-size]
    (.append writer (str start-token-indent \" date \"))))

;; Database

(def db {:classname "com.mysql.jdbc.Driver"
         :subprotocol "mysql"
         :subname "//localhost:3306/clojuredocs_development?user=cd_wsapi&password=cd_wsapi"
;         :subname "//localhost:3306/clojuredocs_production?user=cd_wsapi&password=cd_wsapi"
         :create true
         :username "cd_wsapi"
         :password "cd_wsapi"})

(def clojuredocs-base "http://clojuredocs.org")


(defn default [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body *default-page*})


(defn get-available-versions
  "Given nothing, return all available clojure.core versions clojuredocs knows about.
  Given a namespace, return a list of versions for the namespace.
  Given a namespace and name, return a list of versions for the function."
  ([]
     (get-available-versions "clojure.core"))
  ([ns]
     (with-connection
       db
       (with-query-results rs ["select distinct version from flat_functions_view where ns = ?" ns]
         (remove nil? (map :version (doall rs))))))
  ([ns name]
     (with-connection
       db
       (with-query-results rs ["select distinct version from flat_functions_view where ns = ? and name = ?" ns name]
         (remove nil? (map :version (doall rs)))))))


(defn available-versions
  "Get all available versions for either clojure (no args), or a particular function"
  ([]
     (fn [r]
       {:status 200
        :headers {"Content-Type" "application/json"}
        :body (encode-to-str (get-available-versions))}))
  ([ns]
     (fn [r]
       {:status 200
        :headers {"Content-Type" "application/json"}
        :body (encode-to-str (get-available-versions ns))}))
  ([ns name]
     (fn [r]
       {:status 200
        :headers {"Content-Type" "application/json"}
        :body (encode-to-str (get-available-versions ns name))})))


(defn format-example
  "Given an example, format the example for the API JSON output."
  [example]
  (dissoc (into {} example) :id :function_id :user_id))


;; select * from flat_examples_view where ns = clojure.core and function = map [ and lib_version = 1.2.0 ]

(defn examples
  ([ns name]
     (examples ns name nil))
  ([ns name version]
     (fn [r]
       {:status 200
        :headers {"Content-Type" "application/json"}
        :body (encode-to-str
               (with-connection db
                 (transaction
                  (when-let [examples (with-query-results
                                        rs
                                        (if version
                                          ["select * from flat_examples_view where ns = ? and function = ? and lib_version = ?" ns name version]
                                          ["select * from flat_examples_view where ns = ? and function = ?" ns name])
                                        (doall rs))]
                    ;; TODO: fix the url for versioning
                    {:url (str clojuredocs-base "/v/" (:function_id (first examples)))
                     :examples (map format-example examples)}))))})))


(defn format-search
  "Given a function result set, format the function for the API JSON output."
  [function]
  (let [id (:id function)]
    (assoc function :url (str clojuredocs-base "/v/" id))))


(defn perform-search
  "Given a searching query vector, return a function to perform the search."
  [queryvec]
  (fn [n]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (encode-to-str
             (with-connection db
                              (transaction
                                (when-let [functions (with-query-results rs queryvec (doall rs))]
                                  (map format-search functions)))))}))


(defn search
  "Search for a method by name or namespace & name."
  ([name]
   (search nil name))
  ([ns name]
     (let [qv (if (nil? ns)
              [(str "select id,name,ns from flat_functions_view where name like '%" name "%'")]
              [(str "select id,name,ns from flat_functions_view where ns = ? and name like '%" name "%'") ns])]
     (perform-search qv))))


(defn format-comment
  "Given a comment, format it for json output."
  [c]
  (dissoc (into {} c) :subject :parent_id :lft :rgt :id :commentable_id :commentable_type :title))


(defn get-comments
  "Return the comments for a given namespace and method."
  ([ns name]
     (get-comments ns name nil))
  ([ns name version]
     (fn [n]
       {:status 200
        :headers {"Content-Type" "application/json"}
        :body (encode-to-str
               (with-connection db
                 (transaction
                  (when-let [comments (with-query-results
                                        rs
                                        (if version
                                          ["select * from flat_comments_view where ns = ? and function = ? and version = ?" ns name version]
                                          ["select * from flat_comments_view where ns = ? and function = ?" ns name])
                                        (doall rs))]
                    (map format-comment comments)))))})))


(defn format-see-also-function
  "Given a function, format it for json."
  [function]
  (let [id (:id function)]
    (assoc (dissoc (into {} function) :id :doc :source :shortdoc
                   :library :library_id)
      :url (str clojuredocs-base "/v/" id))))


(defn format-see-also
  "Given an id, format the function to see also."
  [id]
  (with-connection db
                   (transaction
                     (when-let [functions (with-query-results
                                            rs
                                            ["select * from flat_functions_view where id = ?" (:to_id id)]
                                            (doall rs))]
                       (format-see-also-function (first functions)))))) ; should only be 1 function per id


(defn see-also
  "Return the functions to see for a given namespace and method."
  [ns name]
  (fn [n]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (encode-to-str
             (with-connection db
                              (transaction
                               (when-let [see-also-ids (with-query-results
                                                         rs
                                                         ["select to_id from flat_see_alsos_view where ns = ? and function = ?" ns name]
                                                         (doall rs))]
                                 (map format-see-also see-also-ids)))))}))



(defn app-handler [channel request]
  (enqueue-and-close 
   channel
   ((app
     ["examples" ns name] (examples ns name)
     ["examples" version ns name] (examples ns name version)
     ["search" ns name] (search ns name)
     ["search" name] (search name)
     ["comments" ns name] (get-comments ns name)
     ["comments" version ns name] (get-comments ns name version)
     ["see-also" ns name] (see-also ns name)
     ["versions"] (available-versions)
     ["versions" ns] (available-versions ns)
     ["versions" ns name] (available-versions ns name)
     [&] default)
    request)))


(defn app-wrapper [channel request]
  (app-handler channel request))


(comment (def server (start-http-server app-wrapper {:port *server-port*}))


         (defn restart-server []
         (server)
         (def server (start-http-server app-wrapper {:port *server-port*})))

         (restart-server))

