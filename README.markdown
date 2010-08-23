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

Searching for a function
------------------------

    curl "http://localhost:8080/search/map"
    curl "http://localhost:8080/search/clojure.core/map"

License
-------

Copyright (C) 2010 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
