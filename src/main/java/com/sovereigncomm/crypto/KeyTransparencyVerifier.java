package com.sovereigncomm.crypto;

public interface KeyTransparencyVerifier {
    boolean verifyInclusionProof(byte[] canonicalEntry, byte[] inclusionProof, byte[] signedTreeHead);
    boolean verifyConsistencyProof(byte[] oldSignedTreeHead, byte[] newSignedTreeHead, byte[] consistencyProof);
}
