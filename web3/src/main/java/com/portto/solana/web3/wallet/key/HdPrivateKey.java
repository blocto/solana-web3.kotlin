package com.portto.solana.web3.wallet.key;

/**
 * Defines a key with a given private key
 */
public class HdPrivateKey extends HdKey {
    private byte[] privateKey;

    public byte[] getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(byte[] privateKey) {
        this.privateKey = privateKey;
    }
}
