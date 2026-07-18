(ns kurashimori.repository-contract-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]))

(defn read-edn [path] (edn/read-string (slurp path)))

(deftest canonical-repository-documents
  (let [contract (read-edn "repository-contracts.edn")
        identity (read-edn "identity.edn")
        manifest (read-edn "manifest.edn")]
    (is (= :edn (:repository/canonical-format contract)))
    (is (= "kurashimori" (:identity/actor identity)))
    (is (= "kurashimori" (:actor/id manifest)))
    (doseq [legacy (:repository/legacy-artifacts-forbidden contract)]
      (is (not (.exists (io/file legacy))) (str legacy " must stay pruned")))))

(deftest actor-owned-data-is-edn-canonical
  (doseq [path (concat (map #(.getPath %) (file-seq (io/file "lex")))
                       ["registry/targets.seed.edn" "kotoba.app.edn" "schema.edn"])
          :when (.endsWith path ".edn")]
    (is (some? (read-edn path)) path))
  (is (= 7 (count (filter #(.endsWith (.getName %) ".edn")
                          (file-seq (io/file "lex")))))))

(deftest dependencies-are-reproducibly-pinned
  (doseq [dependency (:dependencies (read-edn "dependencies.edn"))]
    (is (re-matches #"[0-9a-f]{40}" (:dependency/revision dependency))
        (str (:dependency/id dependency)))))
