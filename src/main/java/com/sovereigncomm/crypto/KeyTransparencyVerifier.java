package com.sovereigncomm.crypto;

public interface KeyTransparencyVerifier {
    boolean verifyInclusionProof(byte[] canonicalEntry, byte[] inclusionProof, byte[] signedTreeHead);
