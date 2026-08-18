package org.firstfolio.gifticon.domain;

public class GifticonProductInventory extends GifticonProduct {

    private int availableCodeCount;
    private int assignedCodeCount;
    private int voidCodeCount;

    public int getAvailableCodeCount() { return availableCodeCount; }
    public void setAvailableCodeCount(int availableCodeCount) { this.availableCodeCount = availableCodeCount; }
    public int getAssignedCodeCount() { return assignedCodeCount; }
    public void setAssignedCodeCount(int assignedCodeCount) { this.assignedCodeCount = assignedCodeCount; }
    public int getVoidCodeCount() { return voidCodeCount; }
    public void setVoidCodeCount(int voidCodeCount) { this.voidCodeCount = voidCodeCount; }
}
