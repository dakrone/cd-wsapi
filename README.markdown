cd-wsapi - ClojureDocs.org web API
==================================

Usage
-----

    lein uberjar
    java -jar cd-wsapi-0.1.0-SNAPSHOT-standalone.jar

In a separate terminal:

Getting examples
----------------

    curl "http://localhost:8080/examples/clojure.core/map"
    curl "http://localhost:8080/examples/1.2.0/clojure.core/map"

Searching for a function
------------------------

    curl "http://localhost:8080/search/map"
    curl "http://localhost:8080/search/clojure.core/map"

Searching for comments on a function
------------------------------------

    curl "http://localhost:8080/comments/clojure.contrib.json/read-json"
    curl "http://localhost:8080/comments/1.2.0/clojure.contrib.json/read-json"

Getting the 'see-also' functions
--------------------------------

    curl "http://localhost:8080/see-also/clojure.test/are"

Getting the available versions clojuredocs knows about
------------------------------------------------------

    curl "http://localhost:8080/versions"
    curl "http://localhost:8080/versions/clojure.core"
    curl "http://localhost:8080/versions/clojure.core/map"

License
-------

Copyright (C) 2010 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
