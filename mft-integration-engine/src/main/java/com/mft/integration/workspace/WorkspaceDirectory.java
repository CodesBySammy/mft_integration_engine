package com.mft.integration.workspace;

public enum WorkspaceDirectory {
    DOWNLOADS("downloads"),
    PROCESSING("processing"),
    OUTPUT("output"),
    QUARANTINE("quarantine"),
    ARCHIVE("archive"),
    STATUS("status"),
    TEMP("temp");

    private final String folderName;

    WorkspaceDirectory(String folderName) {
        this.folderName = folderName;
    }

    public String getFolderName() {
        return folderName;
    }
}
