/* ==========================================================
File:        CustomSaveListener.java
Description: Sends a heartbeat when a file is saved.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class CustomSaveListener implements FileDocumentManagerListener {
    @Override
    public void beforeDocumentSaving(Document document) {
        // WakaTime.log.debug("beforeDocumentSaving event");
        if (!WakaTime.isAppActive()) return;
        VirtualFile file = WakaTime.getFile(document);
        if (file == null) return;
        Project project = WakaTime.getProject(document);
        if (!WakaTime.isProjectInitialized(project)) return;
        WakaTime.appendHeartbeat(file, project, true, document.getLineCount(), null);
    }

    @Override
    public void beforeAllDocumentsSaving() {
    }

    @Override
    public void beforeFileContentReload(@NotNull VirtualFile file, @NotNull Document document) {
    }

    @Override
    public void fileWithNoDocumentChanged(@NotNull VirtualFile file) {
    }

    @Override
    public void fileContentReloaded(@NotNull VirtualFile file, @NotNull Document document) {
    }

    @Override
    public void fileContentLoaded(@NotNull VirtualFile file, @NotNull Document document) {
    }

    @Override
    public void unsavedDocumentsDropped() {
    }
}