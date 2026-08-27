(ns kurashimori.murakumo-test
  (:require [clojure.test :refer [deftest is testing]]
            [kurashimori.murakumo :as kurashimori]))

(def full-attestations
  (into {}
        (map (fn [gate] [gate (str "attested-" (name gate))]))
        (distinct
         (mapcat (fn [spec]
                   (concat (:required-gates spec)
                           (:agent-on-behalf-gates spec)))
                 (vals kurashimori/cell-specs)))))

(deftest maps-all-legacy-kurashimori-cells
  (is (= #{"kurashimori_compose"
           "kurashimori_cooloff_check"
           "kurashimori_escalation"
           "kurashimori_intake"
           "kurashimori_remedy_registry"
           "kurashimori_send"
           "kurashimori_status_track"}
         (set (map :legacy-cell (vals kurashimori/cell-specs))))))

(deftest r0-gates-block-effects
  (let [plan (kurashimori/cell-plan :cooloff-check
                                    {:member-did "did:example:member"
                                     :session-id "session-001"
                                     :remedy-id "jp-houmon-cooloff"
                                     :computed-at "2026-06-29T00:00:00Z"})]
    (is (= :blocked (:status plan)))
    (is (= [:council-charter-attestation
            :silen-kurashimori-baseline-review
            :member-consent-baseline
            :own-matter-only-baseline
            :transparent-unofficial-assistant-baseline
            :upl-boundary-no-advice-no-representation-baseline
            :encrypted-pii-envelope-baseline
            :murakumo-only-inference-baseline
            :state-aligned-flag-passthrough-baseline
            :informational-date-computation-baseline
            :not-legal-opinion-baseline
            :g14-verified-remedy-only-baseline
            :statutory-window-reverification-baseline]
           (:missing-gates plan)))
    (is (empty? (:effects plan)))))

(deftest attested-self-send-emits-dispatch-record
  (let [plan (kurashimori/cell-plan :send
                                    {:attestations full-attestations
                                     :member-did "did:example:member"
                                     :session-id "session-001"
                                     :remedy-id "jp-houmon-cooloff"
                                     :draft-id "draft-001"
                                     :dispatch-id "dispatch-001"
                                     :computed-at "2026-06-29T00:00:00Z"
                                     :record {:tid "dispatch-001"
                                              :channel "member-inbox"}})
        effect (first (:effects plan))]
    (is (= :ready (:status plan)))
    (is (= :mst/put-record (:op effect)))
    (is (= kurashimori/actor-did (:actor effect)))
    (is (= "com.etzhayyim.kurashimori.dispatchRecord" (:collection effect)))
    (is (= "dispatch-001" (:rkey effect)))
    (is (= "member-self-send" (get-in effect [:record :mode])))
    (is (= true (get-in effect [:record :memberSelfActionDefault])))))

(deftest daikou-send-keeps-r3-gates
  (testing "agent-on-behalf requires R3 and reserved-practice clearance"
    (let [attestations (apply dissoc full-attestations
                              [:r3-daikou-activation-adr
                               :council-lv7-unanimity-attestation
                               :reserved-practice-clearance-attestation
                               :per-submission-agent-consent-baseline])
          plan (kurashimori/cell-plan :send
                                      {:attestations attestations
                                       :send-mode "agent-on-behalf"
                                       :dispatch-id "dispatch-002"})]
      (is (= :blocked (:status plan)))
      (is (= [:r3-daikou-activation-adr
              :council-lv7-unanimity-attestation
              :reserved-practice-clearance-attestation
              :per-submission-agent-consent-baseline]
             (:missing-gates plan)))
      (is (empty? (:effects plan)))))
  (testing "self-send does not require daikou gates"
    (let [attestations (apply dissoc full-attestations
                              [:r3-daikou-activation-adr
                               :council-lv7-unanimity-attestation
                               :reserved-practice-clearance-attestation
                               :per-submission-agent-consent-baseline])
          plan (kurashimori/cell-plan :send
                                      {:attestations attestations
                                       :send-mode "member-self-send"
                                       :dispatch-id "dispatch-003"})]
      (is (= :ready (:status plan)))
      (is (= ["com.etzhayyim.kurashimori.dispatchRecord"]
             (map :collection (:effects plan)))))))

(deftest cell-specific-gates-remain-specific
  (testing "registry keeps official-source provenance"
    (let [attestations (dissoc full-attestations :official-source-provenance-baseline)
          plan (kurashimori/cell-plan :remedy-registry {:attestations attestations})]
      (is (= [:official-source-provenance-baseline] (:missing-gates plan)))))
  (testing "compose keeps drafting-assist only"
    (let [attestations (dissoc full-attestations :drafting-assist-only-baseline)
          plan (kurashimori/cell-plan :compose {:attestations attestations})]
      (is (= [:drafting-assist-only-baseline] (:missing-gates plan)))))
  (testing "escalation keeps route-not-represent"
    (let [attestations (dissoc full-attestations :route-not-represent-baseline)
          plan (kurashimori/cell-plan :escalation {:attestations attestations})]
      (is (= [:route-not-represent-baseline] (:missing-gates plan))))))

(deftest all-cell-plans-ready-when-attested
  (let [plans (kurashimori/all-cell-plans {:attestations full-attestations
                                           :member-did "did:example:member"
                                           :session-id "session-001"
                                           :remedy-id "jp-houmon-cooloff"
                                           :draft-id "draft-001"
                                           :dispatch-id "dispatch-001"
                                           :jurisdiction "jpn"
                                           :computed-at "2026-06-29T00:00:00Z"})]
    (is (= (set (keys kurashimori/cell-specs)) (set (keys plans))))
    (is (every? #(= :ready (:status %)) (vals plans)))
    (is (= (count kurashimori/cell-specs)
           (count (mapcat :effects (vals plans)))))))
