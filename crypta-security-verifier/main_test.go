package main

import (
	"crypto/sha256"
	"encoding/base64"
	"testing"
)

func TestBuildTransparencyAppendIsDeterministicAndChainsPreviousHead(t *testing.T) {
	v := verifier{signingSecret: []byte("unit-test-secret")}
	canonical := sha256.Sum256([]byte("canonical-entry"))
	req := keyTransparencyAppendRequest{
		OrganizationID:           "org-1",
		SubjectUserID:            "user-1",
		SubjectDeviceID:          "device-1",
		CanonicalEntryHashBase64: base64.StdEncoding.EncodeToString(canonical[:]),
		LogIndex:                 0,
	}

	first, err := v.buildTransparencyAppend(req)
	if err != nil {
		t.Fatalf("first append failed: %v", err)
	}
	second, err := v.buildTransparencyAppend(req)
	if err != nil {
		t.Fatalf("second append failed: %v", err)
	}
	if first.SignedTreeHeadBase64 != second.SignedTreeHeadBase64 {
		t.Fatalf("same input produced different tree heads")
	}

	req.LogIndex = 1
	req.PreviousTreeHeadBase64 = first.SignedTreeHeadBase64
	chained, err := v.buildTransparencyAppend(req)
	if err != nil {
		t.Fatalf("chained append failed: %v", err)
	}
	if chained.SignedTreeHeadBase64 == first.SignedTreeHeadBase64 {
		t.Fatalf("chained append did not change tree head")
	}
	if chained.ProofVersion != "verifier-proof-v1" {
		t.Fatalf("unexpected proof version: %s", chained.ProofVersion)
	}
}

func TestBuildTransparencyAppendRejectsMalformedCanonicalHash(t *testing.T) {
	v := verifier{signingSecret: []byte("unit-test-secret")}
	_, err := v.buildTransparencyAppend(keyTransparencyAppendRequest{
		CanonicalEntryHashBase64: base64.StdEncoding.EncodeToString([]byte("too-short")),
	})
	if err == nil {
		t.Fatalf("expected malformed canonical hash to fail")
	}
}
