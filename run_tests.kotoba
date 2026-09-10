(require '[clojure.test :as t])

(doseq [ns-sym '[kurashimori.methods.test-charter-gates
                  kurashimori.registry-seed-test
                  kurashimori.repository-contract-test]]
  (require ns-sym))

(let [result (apply t/run-tests
                    '[kurashimori.methods.test-charter-gates
                      kurashimori.registry-seed-test
                      kurashimori.repository-contract-test])]
  (System/exit (if (zero? (+ (:fail result) (:error result))) 0 1)))
