/* ==========================================================
File:        CustomSaveListener.java
Description: Sends a heartbeat when a file is saved.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class CustomSaveListener extends FileDocumentManagerAdapter {

    @Override
    public void beforeDocumentSaving(Document document) {
        Editor[] editors = EditorFactory.getInstance().getEditors(document);
        if (editors.length > 0) {
            FileDocumentManager instance = FileDocumentManager.getInstance();
            VirtualFile file = instance.getFile(document);
            Project project = editors[0].getProject();
            WakaTime.appendHeartbeat(file, project, true);
        }
    }
}