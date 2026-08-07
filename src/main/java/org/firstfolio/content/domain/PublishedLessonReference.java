package org.firstfolio.content.domain;

public class PublishedLessonReference {

    private long subChapterId;
    private String title;
    private long contentVersionId;
    private String schemaVersion;
    private String storageObjectKey;
    private String storageVersionId;

    public PublishedLessonReference() {
    }

    public long getSubChapterId() {
        return subChapterId;
    }

    public void setSubChapterId(long subChapterId) {
        this.subChapterId = subChapterId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getContentVersionId() {
        return contentVersionId;
    }

    public void setContentVersionId(long contentVersionId) {
        this.contentVersionId = contentVersionId;
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

    public StoredObjectRef toStoredObjectRef() {
        return new StoredObjectRef(storageObjectKey, storageVersionId);
    }
}
