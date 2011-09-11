(set! *warn-on-reflection* true)

(ns monger.test.collection
  (:import  [com.mongodb WriteResult WriteConcern DBCursor DBObject CommandResult$CommandFailure]
            [java.util Date])
  (:require [monger core collection result util convertion]
            [clojure stacktrace])
  (:use [clojure.test]))

(monger.util/with-ns 'monger.core
  (defonce ^:dynamic *mongodb-connection* (monger.core/connect))
  (defonce ^:dynamic *mongodb-database*   (monger.core/get-db "monger-test")))


;;
;; fixture functions
;;

(defn purge-collection
  [collection-name, f]
  (monger.collection/remove collection-name)
  (f)
  (monger.collection/remove collection-name))

(defn purge-people-collection
  [f]
  (purge-collection "people" f))

(defn purge-docs-collection
  [f]
  (purge-collection "docs" f))

(defn purge-things-collection
  [f]
  (purge-collection "things" f))

(defn purge-libraries-collection
  [f]
  (purge-collection "libraries" f))

(use-fixtures :each purge-people-collection purge-docs-collection purge-things-collection purge-libraries-collection)


;;
;; insert
;;

(deftest insert-a-basic-document-without-id-and-with-default-write-concern
  (let [collection "people"
        doc        { :name "Joe", :age 30 }]
    (is (monger.result/ok? (monger.collection/insert "people" doc)))
    (is (= 1 (monger.collection/count collection)))))

(deftest insert-a-basic-document-without-id-and-with-explicit-write-concern
  (let [collection "people"
        doc        { :name "Joe", :age 30 }]
    (is (monger.result/ok? (monger.collection/insert "people" doc WriteConcern/SAFE)))
    (is (= 1 (monger.collection/count collection)))))

(deftest insert-a-basic-db-object-without-id-and-with-default-write-concern
  (let [collection "people"
        doc        (monger.convertion/to-db-object { :name "Joe", :age 30 })]
    (is (nil? (monger.util/get-id doc)))
    (monger.collection/insert "people" doc)
    (is (not (nil? (monger.util/get-id doc))))))



;;
;; insert-batch
;;

(deftest insert-a-batch-of-basic-documents-without-ids-and-with-default-write-concern
  (let [collection "people"
        docs       [{ :name "Joe", :age 30 }, { :name "Paul", :age 27 }]]
    (is (monger.result/ok? (monger.collection/insert-batch "people" docs)))
    (is (= 2 (monger.collection/count collection)))))

(deftest insert-a-batch-of-basic-documents-without-ids-and-with-explicit-write-concern
  (let [collection "people"
        docs       [{ :name "Joe", :age 30 }, { :name "Paul", :age 27 }]]
    (is (monger.result/ok? (monger.collection/insert-batch "people" docs WriteConcern/NORMAL)))
    (is (= 2 (monger.collection/count collection)))))




;;
;; count, remove
;;

(deftest get-collection-size
  (let [collection "things"]
    (is (= 0 (monger.collection/count collection)))
    (monger.collection/insert-batch collection [{ :language "Clojure", :name "langohr" }
                                                { :language "Clojure", :name "monger" }
                                                { :language "Clojure", :name "incanter" }
                                                { :language "Scala",   :name "akka" }] )
    (is (= 4 (monger.collection/count collection)))
    (is (= 3 (monger.collection/count collection { :language "Clojure" })))
    (is (= 1 (monger.collection/count collection { :language "Scala"   })))
    (is (= 0 (monger.collection/count collection { :language "Python"  })))))


(deftest remove-all-documents-from-collection
  (let [collection "libraries"]
    (monger.collection/insert-batch collection [{ :language "Clojure", :name "monger" }
                                                { :language "Clojure", :name "langohr" }
                                                { :language "Clojure", :name "incanter" }
                                                { :language "Scala",   :name "akka" }])
    (is (= 4 (monger.collection/count collection)))
    (monger.collection/remove collection)
    (is (= 0 (monger.collection/count collection)))))


(deftest remove-some-documents-from-collection
  (let [collection "libraries"]
    (monger.collection/insert-batch collection [{ :language "Clojure", :name "monger" }
                                                { :language "Clojure", :name "langohr" }
                                                { :language "Clojure", :name "incanter" }
                                                { :language "Scala",   :name "akka" }])
    (is (= 4 (monger.collection/count collection)))
    (monger.collection/remove collection { :language "Clojure" })
    (is (= 1 (monger.collection/count collection)))))



;;
;; find
;;

(deftest find-full-document-when-collection-is-empty
  (let [collection "docs"]
    (def cursor (monger.collection/find collection))
    (is (instance? DBCursor cursor))))


;;
;; find-one
;;

(deftest find-one-full-document-when-collection-is-empty
  (let [collection "docs"]
    (is (nil? (monger.collection/find-one collection {})))))

(deftest find-one-full-document-as-map-when-collection-is-empty
  (let [collection "docs"]
    (is (nil? (monger.collection/find-one-as-map collection {})))))


(deftest find-one-full-document-when-collection-has-matches
  (let [collection "docs"
        doc-id     (monger.util/random-uuid)
        doc        { :data-store "MongoDB", :language "Clojure", :_id doc-id }]
    (monger.collection/insert collection doc)
    (def #^DBObject found-one (monger.collection/find-one collection { :language "Clojure" }))
    (is (= (:_id doc) (monger.util/get-id found-one)))
    (is (= (monger.convertion/from-db-object found-one true) doc))
    (is (= (monger.convertion/to-db-object doc) found-one))))


(deftest find-one-full-document-as-map-when-collection-has-matches
  (let [collection "docs"
        doc-id     (monger.util/random-uuid)
        doc        { :data-store "MongoDB", :language "Clojure", :_id doc-id }]
    (monger.collection/insert collection doc)
    (is (= doc (monger.collection/find-one-as-map collection { :language "Clojure" })))))



(deftest find-one-partial-document-when-collection-has-matches
  (let [collection "docs"
        doc-id     (monger.util/random-uuid)
        doc        { :data-store "MongoDB", :language "Clojure", :_id doc-id }
        fields     [:language]]
    (monger.collection/insert collection doc)
    (def #^DBObject loaded (monger.collection/find-one collection { :language "Clojure" } fields))
    (is (nil? (.get #^DBObject loaded "data-store")))
    (is (= doc-id (monger.util/get-id loaded)))
    (is (= "Clojure" (.get #^DBObject loaded "language")))))


(deftest find-one-partial-document-as-map-when-collection-has-matches
  (let [collection "docs"
        doc-id     (monger.util/random-uuid)
        doc        { :data-store "MongoDB", :language "Clojure", :_id doc-id }
        fields     [:data-store]]
    (monger.collection/insert collection doc)
    (is (= { :data-store "MongoDB", :_id doc-id } (monger.collection/find-one-as-map collection { :language "Clojure" } fields true)))))



;;
;; find-by-id
;;

(deftest find-full-document-by-id-when-that-document-does-not-exist
  (let [collection "libraries"
        doc-id     (monger.util/random-uuid)]
    (is (nil? (monger.collection/find-by-id collection doc-id)))))


(deftest find-full-document-by-id-as-map-when-that-document-does-not-exist
  (let [collection "libraries"
        doc-id     (monger.util/random-uuid)]
    (is (nil? (monger.collection/find-map-by-id collection doc-id)))))


(deftest find-full-document-by-id-when-document-does-exist
  (let [collection "libraries"
        doc-id     (monger.util/random-uuid)
        doc        { :data-store "MongoDB", :language "Clojure", :_id doc-id }]
    (monger.collection/insert collection doc)
    (is (= (doc (monger.collection/find-by-id collection doc-id))))))

(deftest find-full-document-map-by-id-when-document-does-exist
  (let [collection "libraries"
        doc-id     (monger.util/random-uuid)
        doc        { :data-store "MongoDB", :language "Clojure", :_id doc-id }]
    (monger.collection/insert collection doc)
    (is (= (doc (monger.collection/find-map-by-id collection doc-id))))))


(deftest find-partial-document-by-id-when-document-does-exist
  (let [collection "libraries"
        doc-id     (monger.util/random-uuid)
        doc        { :data-store "MongoDB", :language "Clojure", :_id doc-id }]
    (monger.collection/insert collection doc)
    (is (= ({ :language "Clojure" } (monger.collection/find-by-id collection doc-id [ :language ]))))))


(deftest find-partial-document-as-map-by-id-when-document-does-exist
  (let [collection "libraries"
        doc-id     (monger.util/random-uuid)
        doc        { :data-store "MongoDB", :language "Clojure", :_id doc-id }]
    (monger.collection/insert collection doc)
    (is (= ({ :language "Clojure" } (monger.collection/find-map-by-id collection doc-id [ :language ]))))))


;;
;; find
;;

(deftest find-multiple-documents-when-collection-is-empty
  (let [collection "libraries"]
    (is (empty? (monger.collection/find collection { :language "Scala" })))))

(deftest find-multiple-maps-when-collection-is-empty
  (let [collection "libraries"]
    (is (empty? (monger.collection/find-maps collection { :language "Scala" })))))


(deftest find-multiple-documents
  (let [collection "libraries"]
    (monger.collection/insert-batch collection [{ :language "Clojure", :name "monger" }
                                                { :language "Clojure", :name "langohr" }
                                                { :language "Clojure", :name "incanter" }
                                                { :language "Scala",   :name "akka" }])
    (is (= 1 (monger.core/count (monger.collection/find collection { :language "Scala"   }))))
    (is (= 3 (.count (monger.collection/find collection { :language "Clojure" }))))
    (is (empty? (monger.collection/find collection      { :language "Java"    })))))

(deftest find-multiple-maps
  (let [collection "libraries"]
    (monger.collection/insert-batch collection [{ :language "Clojure", :name "monger" }
                                                { :language "Clojure", :name "langohr" }
                                                { :language "Clojure", :name "incanter" }
                                                { :language "Scala",   :name "akka" }])
    (is (= 1 (clojure.core/count (monger.collection/find-maps collection { :language "Scala" }))))
    (is (= 3 (.count (monger.collection/find-maps collection { :language "Clojure" }))))
    (is (empty? (monger.collection/find-maps collection      { :language "Java"    })))))



(deftest find-multiple-partial-documents
  (let [collection "libraries"]
    (monger.collection/insert-batch collection [{ :language "Clojure", :name "monger" }
                                                { :language "Clojure", :name "langohr" }
                                                { :language "Clojure", :name "incanter" }
                                                { :language "Scala",   :name "akka" }])
    (let [scala-libs   (monger.collection/find collection { :language "Scala" } [:name])
          clojure-libs (monger.collection/find collection { :language "Clojure"} [:language])]
      (is (= 1 (.count scala-libs)))
      (is (= 3 (.count clojure-libs)))
      (doseq [i clojure-libs]
        (let [doc (monger.convertion/from-db-object i true)]
          (is (= (:language doc) "Clojure"))))
      (is (empty? (monger.collection/find collection { :language "Erlang" } [:name]))))))


;;
;; update, save
;;

(deftest update-document-by-id-without-upsert
  (let [collection "libraries"
        doc-id       (monger.util/random-uuid)
        date         (Date.)
        doc          { :created-at date, :data-store "MongoDB", :language "Clojure", :_id doc-id }
        modified-doc { :created-at date, :data-store "MongoDB", :language "Erlang",  :_id doc-id }]
    (monger.collection/insert collection doc)
    (is (= (doc (monger.collection/find-by-id collection doc-id))))
    (monger.collection/update collection { :_id doc-id } { :language "Erlang" })
    (is (= (modified-doc (monger.collection/find-by-id collection doc-id))))))


(deftest update-multiple-documents
  (let [collection "libraries"]
    (monger.collection/insert collection { :language "Clojure", :name "monger" })
    (monger.collection/insert collection { :language "Clojure", :name "langohr" })
    (monger.collection/insert collection { :language "Clojure", :name "incanter" })
    (monger.collection/insert collection { :language "Scala",   :name "akka" })
    (is (= 3 (monger.collection/count collection { :language "Clojure" })))
    (is (= 1 (monger.collection/count collection { :language "Scala"   })))
    (is (= 0 (monger.collection/count collection { :language "Python"  })))
    (monger.collection/update collection { :language "Clojure" } { "$set" { :language "Python" } } :multi true)
    (is (= 0 (monger.collection/count collection { :language "Clojure" })))
    (is (= 1 (monger.collection/count collection { :language "Scala"   })))
    (is (= 3 (monger.collection/count collection { :language "Python"  })))))


(deftest save-a-new-document
  (let [collection "people"
        document       { :name "Joe", :age 30 }]
    (is (monger.result/ok? (monger.collection/save "people" document)))
    (is (= 1 (monger.collection/count collection)))))


(deftest save-a-new-basic-db-object
  (let [collection "people"
        doc        (monger.convertion/to-db-object { :name "Joe", :age 30 })]
    (is (nil? (monger.util/get-id doc)))
    (monger.collection/save "people" doc)
    (is (not (nil? (monger.util/get-id doc))))))



(deftest update-an-existing-document-using-save
  (let [collection "people"
        doc-id            "people-1"
        document          { :_id doc-id, :name "Joe",   :age 30 }]
    (is (monger.result/ok? (monger.collection/insert "people" document)))
    (is (= 1 (monger.collection/count collection)))
    (monger.collection/save collection { :_id doc-id, :name "Alan", :age 40 })
    (is (= 1 (monger.collection/count collection { :name "Alan", :age 40 })))))


(deftest upsert-a-document
  (let [collection "libraries"
        doc-id       (monger.util/random-uuid)
        date         (Date.)
        doc          { :created-at date, :data-store "MongoDB", :language "Clojure", :_id doc-id }
        modified-doc { :created-at date, :data-store "MongoDB", :language "Erlang",  :_id doc-id }]
    (is (not (monger.result/updated-existing? (monger.collection/update collection { :language "Clojure" } doc :upsert true))))
    (is (= 1 (monger.collection/count collection)))
    (is (monger.result/updated-existing? (monger.collection/update collection { :language "Clojure" } modified-doc :multi false :upsert true)))
    (is (= 1 (monger.collection/count collection)))
    (is (= (modified-doc (monger.collection/find-by-id collection doc-id))))
    (monger.collection/remove collection)))


;;
;; indexes
;;

(deftest index-operations
  (let [collection "libraries"]
    (monger.collection/drop-indexes collection)
    (is (= "_id_"
           (:name (first (monger.collection/indexes-on collection)))))
    (is (nil? (second (monger.collection/indexes-on collection))))
    (monger.collection/create-index collection { "language" 1 })
    (is (= "language_1"
           (:name (second (monger.collection/indexes-on collection)))))
    (monger.collection/drop-index collection "language_1")
    (is (nil? (second (monger.collection/indexes-on collection))))
    (monger.collection/ensure-index collection { "language" 1 })
    (is (= "language_1"
           (:name (second (monger.collection/indexes-on collection)))))
    (monger.collection/ensure-index collection { "language" 1 })))


;;
;; exists?, drop, create
;;

(deftest checking-for-collection-existence-when-it-does-not-exist
  (let [collection "widgets"]
    (monger.collection/drop collection)
    (is (false? (monger.collection/exists? collection)))))

(deftest checking-for-collection-existence-when-it-does-exist
  (let [collection "widgets"]
    (monger.collection/drop collection)
    (monger.collection/insert-batch collection [{ :name "widget1" }
                                                { :name "widget2" }])
    (is (monger.collection/exists? collection))
    (monger.collection/drop collection)
    (is (false? (monger.collection/exists? collection)))
    (monger.collection/create "widgets" { :capped true :max 100 })
    (is (monger.collection/exists? collection))
    (monger.collection/rename collection "gadgets")
    (is (not (monger.collection/exists? collection)))
    (is (monger.collection/exists? "gadgets"))
    (monger.collection/drop "gadgets")))
