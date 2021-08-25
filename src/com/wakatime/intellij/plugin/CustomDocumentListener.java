/* ==========================================================
File:        CustomDocumentListener.java
Description: Logs time from document change events.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class CustomDocumentListener implements DocumentListener {
    @Override
    public void beforeDocumentChange(DocumentEvent documentEvent) {
    }

    @Override
    public void documentChanged(DocumentEvent documentEvent) {
        // WakaTime.log.debug("documentChanged event");
        if (!WakaTime.isAppActive()) return;
        Document document = documentEvent.getDocument();
        VirtualFile file = WakaTime.getFile(document);
        if (file == null) return;
        Project project = WakaTime.getProject(document);
        if (!WakaTime.isProjectInitialized(project)) return;
        WakaTime.appendHeartbeat(file, project, false);
    }
}