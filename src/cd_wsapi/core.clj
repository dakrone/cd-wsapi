(ns cd-wsapi.core
  (:use [aleph core http]
        [net.cgrand.moustache]
        [clojure.contrib.sql]
        [org.danlarkin.json]))

(def *server-port* 8080)

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
         :subname "//localhost:3306/clojuredocs?user=cd_wsapi&password=cd_wsapi"
         :create true
         :username "cd_wsapi"
         :password "cd_wsapi"})

(defn default [request]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body "null"})


(defn format-example
  "Given an example, format the example for the API JSON output."
  [example]
  (dissoc (into {} example) :id :function_id :user_id))


(defn examples [ns name]
  (fn [r]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (encode-to-str 
             (with-connection db
                              (transaction
                                (when-let [id (with-query-results
                                                rs
                                                ["select id from functions where ns = ? and name = ?" ns name]
                                                (:id (first (doall rs))))]
                                  (when-let [examples (with-query-results
                                                        rs
                                                        ["select * from examples where function_id = ?" id]
                                                        (doall rs))]
                                    (map format-example examples))))))}))


; Doesn't do anything yet, but it might in the future.
(defn format-search
  "Given a function result set, format the function for the API JSON output."
  [function]
  function)


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
              [(str "select id,name,ns from functions where name like '%" name "%'")]
              [(str "select id,name,ns from functions where ns = ? and name like '%" name "%'") ns])]
     (perform-search qv))))


(defn format-comment
  "Given a comment, format it for json output."
  [c]
  (dissoc (into {} c) :subject :parent_id :lft :rgt :id :commentable_id :commentable_type :title))


(defn get-comments
  "Return the comments for a given namespace and method."
  [ns name]
  (fn [n]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (encode-to-str
             (with-connection db
                              (transaction
                                (when-let [id (with-query-results
                                                rs
                                                ["select id from functions where ns = ? and name = ?" ns name]
                                                (:id (first (doall rs))))]
                                  (when-let [comments (with-query-results
                                                      rs
                                                      ["select * from comments where comments.commentable_id = ? " id]
                                                      (doall rs))]
                                    (map format-comment comments))))))}))


(defn format-function
  "Given a function, format it for json."
  [function]
  (dissoc (into {} function) :id :doc :source :shortdoc))


(defn format-see-also
  "Given an id, format the function to see also."
  [id]
  (with-connection db
                   (transaction
                     (when-let [functions (with-query-results
                                           rs
                                           ["select * from functions where id = ?" (:to_id id)]
                                           (doall rs))]
                       (map format-function functions)))))


(defn see-also
  "Return the functions to see for a given namespace and method."
  [ns name]
  (fn [n]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (encode-to-str
             (with-connection db
                              (transaction
                                (when-let [id (with-query-results
                                                rs
                                                ["select id from functions where ns = ? and name = ?" ns name]
                                                (:id (first (doall rs))))]
                                  (when-let [see-also-ids (with-query-results
                                                            rs
                                                            ["select to_id from see_alsos where from_id = ?" id]
                                                            (doall rs))]
                                    (map format-see-also see-also-ids))))))}))



(defn app-handler [channel request]
  (enqueue-and-close 
    channel
    ((app
       ["stuff"] examples
       ["examples" ns name] (examples ns name)
       ["search" ns name] (search ns name)
       ["search" name] (search name)
       ["comments" ns name] (get-comments ns name)
       ["see-also" ns name] (see-also ns name)
       [&] default)
       request)))


(defn app-wrapper [channel request]
  (app-handler channel request))


(comment (def server (start-http-server app-wrapper {:port *server-port*}))


         (defn restart-server []
         (server)
         (def server (start-http-server app-wrapper {:port *server-port*})))

         (restart-server))

