(ns kurashimori.registry-seed-test
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def seed-path "registry/targets.seed.edn")
(def seed (edn/read-string (slurp seed-path)))
(def targets (get seed "targets"))
(def boundary-tokens ["弁護士法" "司法書士法" "UPL"])

(deftest registry-shape
  (is (seq targets))
  (is (pos-int? (get seed "freshnessWindowDays"))))

(deftest remedy-identities-are-complete-and-unique
  (let [ids (map #(get % "remedyId") targets)]
    (is (every? seq ids))
    (is (= (count ids) (count (set ids))))))

(deftest entries-ship-unverified
  (doseq [target targets]
    (is (= "unverified-seed" (get target "verificationStatus"))
        (get target "remedyId"))))

(deftest provenance-and-verification-times-are-present
  (doseq [target targets]
    (testing (get target "remedyId")
      (is (str/starts-with? (get target "provenance" "") "https://"))
      (let [stamp (get target "lastVerified" "")]
        (is (and (str/includes? stamp "T") (str/ends-with? stamp "Z")))))))

(deftest registry-is-worldwide
  (is (every? #(seq (get % "jurisdiction")) targets))
  (is (<= 12 (count (set (map #(get % "jurisdiction") targets))))))

(deftest entries-reassert-the-upl-boundary
  (is (every? #(seq (str/trim (get % "notes" ""))) targets))
  (let [has-boundary? #(some (fn [token] (str/includes? (get % "notes" "") token))
                             boundary-tokens)]
    (is (<= (quot (count targets) 2) (count (filter has-boundary? targets)))))
  (let [text (slurp seed-path)]
    (is (some #(str/includes? text %) boundary-tokens))))
