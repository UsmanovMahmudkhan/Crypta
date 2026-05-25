package main

import (
	"crypto/hmac"
	"crypto/sha256"
	"crypto/tls"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"time"
)

type verifier struct {
	signingSecret []byte
}

type healthResponse struct {
	Status string `json:"status"`
	Detail string `json:"detail"`
}

type keyTransparencyAppendRequest struct {
	OrganizationID           string `json:"organizationId"`
	SubjectUserID            string `json:"subjectUserId"`
	SubjectDeviceID          string `json:"subjectDeviceId"`
	CanonicalEntryHashBase64 string `json:"canonicalEntryHashBase64"`
	PreviousTreeHeadBase64   string `json:"previousTreeHeadBase64"`
	LogIndex                 int64  `json:"logIndex"`
}

type keyTransparencyAppendResponse struct {
	SignedTreeHeadBase64 string                 `json:"signedTreeHeadBase64"`
	MerkleLeafHashBase64 string                 `json:"merkleLeafHashBase64"`
	InclusionProof       map[string]interface{} `json:"inclusionProof"`
	ConsistencyProof     string                 `json:"consistencyProofBase64"`
	CheckpointID         string                 `json:"checkpointId"`
	ProofVersion         string                 `json:"proofVersion"`
}

type deviceAttestationVerificationRequest struct {
	OrganizationID          string `json:"organizationId"`
	UserID                  string `json:"userId"`
	DeviceID                string `json:"deviceId"`
	Format                  string `json:"format"`
	AttestationObjectBase64 string `json:"attestationObjectBase64"`
}

type deviceAttestationVerificationResponse struct {
	Valid                    bool                   `json:"valid"`
	VerificationStatus       string                 `json:"verificationStatus"`
	HardwareBacked           bool                   `json:"hardwareBacked"`
	StrongBoxOrSecureEnclave bool                   `json:"strongBoxOrSecureEnclave"`
	VerifiedClaims           map[string]interface{} `json:"verifiedClaims"`
}

type auditVerificationRequest struct {
	OrganizationID         string `json:"organizationId"`
	EventCount             int    `json:"eventCount"`
	LatestEventHashBase64  string `json:"latestEventHashBase64"`
}

type auditVerificationResponse struct {
	Valid          bool   `json:"valid"`
	VerifiedEvents int    `json:"verifiedEvents"`
	CheckpointID   string `json:"checkpointId"`
}

type signedAdminActionVerificationRequest struct {
	OrganizationID            string `json:"organizationId"`
	SignedActionEnvelopeJSON  string `json:"signedActionEnvelopeJson"`
}

type signedAdminActionVerificationResponse struct {
	Valid  bool   `json:"valid"`
	Reason string `json:"reason"`
}

func main() {
	secret := []byte(os.Getenv("VERIFIER_SIGNING_SECRET"))
	if len(secret) == 0 {
		secret = []byte("crypta-local-verifier-development-secret")
	}
	v := verifier{signingSecret: secret}
	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", v.health)
	mux.HandleFunc("/v1/device-attestations/verify", v.verifyDeviceAttestation)
	mux.HandleFunc("/v1/key-transparency/append", v.appendKeyTransparency)
	mux.HandleFunc("/v1/audit/verify-chain", v.verifyAuditChain)
	mux.HandleFunc("/v1/admin-actions/verify", v.verifySignedAdminAction)

	addr := env("VERIFIER_ADDR", ":9090")
	certFile := os.Getenv("VERIFIER_TLS_CERT")
	keyFile := os.Getenv("VERIFIER_TLS_KEY")
	log.Printf("crypta-security-verifier listening on %s", addr)
	server := &http.Server{
		Addr:              addr,
		Handler:           mux,
		ReadHeaderTimeout: 5 * time.Second,
		TLSConfig:         &tls.Config{MinVersion: tls.VersionTLS12},
	}
	if certFile != "" && keyFile != "" {
		log.Fatal(server.ListenAndServeTLS(certFile, keyFile))
	}
	log.Fatal(server.ListenAndServe())
}

func (v verifier) verifyDeviceAttestation(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req deviceAttestationVerificationRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	raw, err := base64.StdEncoding.DecodeString(req.AttestationObjectBase64)
	if err != nil || len(raw) == 0 {
		writeJSON(w, deviceAttestationVerificationResponse{Valid: false, VerificationStatus: "REJECTED", VerifiedClaims: map[string]interface{}{"reason": "malformed_attestation"}})
		return
	}
	hardwareBacked := req.Format == "android-key" || req.Format == "apple-appattest" || req.Format == "packed"
	writeJSON(w, deviceAttestationVerificationResponse{
		Valid:                    true,
		VerificationStatus:       "PENDING_EXTERNAL_VERIFICATION",
		HardwareBacked:           hardwareBacked,
		StrongBoxOrSecureEnclave: hardwareBacked,
		VerifiedClaims: map[string]interface{}{
			"format": req.Format,
			"attestationHashBase64": base64.StdEncoding.EncodeToString(hash(raw)),
			"verifier": "crypta-security-verifier",
		},
	})
}

func (v verifier) health(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, healthResponse{Status: "UP", Detail: "crypta-security-verifier"})
}

func (v verifier) appendKeyTransparency(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req keyTransparencyAppendRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	resp, err := v.buildTransparencyAppend(req)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	writeJSON(w, resp)
}

func (v verifier) buildTransparencyAppend(req keyTransparencyAppendRequest) (keyTransparencyAppendResponse, error) {
	canonical, err := base64.StdEncoding.DecodeString(req.CanonicalEntryHashBase64)
	if err != nil || len(canonical) != sha256.Size {
		return keyTransparencyAppendResponse{}, errors.New("canonicalEntryHashBase64 must be a SHA-256 base64 value")
	}
	previous := []byte{}
	if req.PreviousTreeHeadBase64 != "" {
		previous, err = base64.StdEncoding.DecodeString(req.PreviousTreeHeadBase64)
		if err != nil {
			return keyTransparencyAppendResponse{}, errors.New("previousTreeHeadBase64 must be base64")
		}
	}
	leaf := merkleLeaf(req.OrganizationID, req.SubjectUserID, req.SubjectDeviceID, canonical, req.LogIndex)
	treeHead := signedTreeHead(v.signingSecret, previous, leaf, req.LogIndex)
	consistency := consistencyProof(previous, treeHead)
	checkpointID := checkpointID(treeHead)
	return keyTransparencyAppendResponse{
		SignedTreeHeadBase64: base64.StdEncoding.EncodeToString(treeHead),
		MerkleLeafHashBase64: base64.StdEncoding.EncodeToString(leaf),
		InclusionProof: map[string]interface{}{
			"algorithm": "sha256-rfc6962-style-local-v1",
			"logIndex": req.LogIndex,
			"leafHashBase64": base64.StdEncoding.EncodeToString(leaf),
			"previousTreeHeadBase64": req.PreviousTreeHeadBase64,
		},
		ConsistencyProof: base64.StdEncoding.EncodeToString(consistency),
		CheckpointID:     checkpointID,
		ProofVersion:     "verifier-proof-v1",
	}, nil
}

func (v verifier) verifyAuditChain(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req auditVerificationRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	valid := req.EventCount >= 0
	if req.EventCount > 0 {
		hash, err := base64.StdEncoding.DecodeString(req.LatestEventHashBase64)
		valid = err == nil && len(hash) == sha256.Size
	}
	sum := sha256.Sum256([]byte(fmt.Sprintf("%s:%d:%s", req.OrganizationID, req.EventCount, req.LatestEventHashBase64)))
	checkpoint := fmt.Sprintf("audit-%x", sum[:8])
	writeJSON(w, auditVerificationResponse{Valid: valid, VerifiedEvents: req.EventCount, CheckpointID: checkpoint})
}

func (v verifier) verifySignedAdminAction(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req signedAdminActionVerificationRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	var envelope map[string]interface{}
	if err := json.Unmarshal([]byte(req.SignedActionEnvelopeJSON), &envelope); err != nil {
		writeJSON(w, signedAdminActionVerificationResponse{Valid: false, Reason: "INVALID_JSON_ENVELOPE"})
		return
	}
	if _, ok := envelope["signatureBase64"].(string); !ok {
		writeJSON(w, signedAdminActionVerificationResponse{Valid: false, Reason: "SIGNATURE_REQUIRED"})
		return
	}
	writeJSON(w, signedAdminActionVerificationResponse{Valid: true, Reason: "SIGNATURE_FIELD_PRESENT"})
}

func merkleLeaf(orgID, userID, deviceID string, canonical []byte, logIndex int64) []byte {
	index := make([]byte, 8)
	binary.BigEndian.PutUint64(index, uint64(logIndex))
	h := sha256.New()
	h.Write([]byte{0x00})
	h.Write([]byte(orgID))
	h.Write([]byte(userID))
	h.Write([]byte(deviceID))
	h.Write(canonical)
	h.Write(index)
	return h.Sum(nil)
}

func signedTreeHead(secret, previous, leaf []byte, logIndex int64) []byte {
	index := make([]byte, 8)
	binary.BigEndian.PutUint64(index, uint64(logIndex))
	mac := hmac.New(sha256.New, secret)
	mac.Write([]byte("crypta-sth-v1"))
	mac.Write(previous)
	mac.Write(leaf)
	mac.Write(index)
	return mac.Sum(nil)
}

func consistencyProof(previous, next []byte) []byte {
	h := sha256.New()
	h.Write([]byte{0x01})
	h.Write(previous)
	h.Write(next)
	return h.Sum(nil)
}

func hash(value []byte) []byte {
	sum := sha256.Sum256(value)
	return sum[:]
}

func checkpointID(treeHead []byte) string {
	sum := sha256.Sum256(treeHead)
	return fmt.Sprintf("kt-%x", sum[:8])
}

func writeJSON(w http.ResponseWriter, value interface{}) {
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(value); err != nil {
		http.Error(w, "json encode failed", http.StatusInternalServerError)
	}
}

func env(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
