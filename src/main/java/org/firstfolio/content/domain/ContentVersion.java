package org.firstfolio.content.domain;

import java.time.LocalDateTime;

public class ContentVersion {

    private Long contentVersionId;
    private long subChapterId;
    private int versionNo;
    private String schemaVersion;
    private String storageObjectKey;
    private String storageVersionId;
    private ContentVersionStatus status;
    private LocalDateTime publishedAt;
    private long createdBy;
    private LocalDateTime createdAt;

    public ContentVersion() {
    }

    public static ContentVersion draft(
            long subChapterId,
            int versionNo,
            String schemaVersion,
            StoredObjectRef storedObject,
            long createdBy,
            LocalDateTime createdAt
    ) {
        ContentVersion version = new ContentVersion();
        version.subChapterId = subChapterId;
        version.versionNo = versionNo;
        version.schemaVersion = schemaVersion;
        version.storageObjectKey = storedObject.objectKey();
        version.storageVersionId = storedObject.versionId();
        version.status = ContentVersionStatus.DRAFT;
        version.createdBy = createdBy;
        version.createdAt = createdAt;
        return version;
    }

    public void publish(LocalDateTime publishedAt) {
        this.status = ContentVersionStatus.PUBLISHED;
        this.publishedAt = publishedAt;
    }

    public void retire() {
        this.status = ContentVersionStatus.RETIRED;
    }

    public Long getContentVersionId() {
        return contentVersionId;
    }

    public void setContentVersionId(Long contentVersionId) {
        this.contentVersionId = contentVersionId;
    }

    public long getSubChapterId() {
        return subChapterId;
    }

    public void setSubChapterId(long subChapterId) {
        this.subChapterId = subChapterId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getStorageObjectKey() {
        return storageObjectKey;
    }

    public void setStorageObjectKey(String storageObjectKey) {
        this.storageObjectKey = storageObjectKey;
    }

    public String getStorageVersionId() {
        return storageVersionId;
    }

    public void setStorageVersionId(String storageVersionId) {
        this.storageVersionId = storageVersionId;
    }

    public ContentVersionStatus getStatus() {
        return status;
    }

    public void setStatus(ContentVersionStatus status) {
        this.status = status;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
