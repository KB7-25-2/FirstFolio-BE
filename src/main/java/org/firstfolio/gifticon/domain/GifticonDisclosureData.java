package org.firstfolio.gifticon.domain;

public class GifticonDisclosureData extends GifticonOrderView {

    private byte[] codeCiphertext;
    private String encryptionKeyVersion;

    public byte[] getCodeCiphertext() { return codeCiphertext; }
    public void setCodeCiphertext(byte[] codeCiphertext) { this.codeCiphertext = codeCiphertext; }
    public String getEncryptionKeyVersion() { return encryptionKeyVersion; }
    public void setEncryptionKeyVersion(String encryptionKeyVersion) { this.encryptionKeyVersion = encryptionKeyVersion; }
}
