/* ==========================================================
File:        CustomDocumentListener.java
Description: Logs time from document change events.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class CustomDocumentListener implements DocumentListener {
    @Override
    public void beforeDocumentChange(DocumentEvent documentEvent) {
    }

    @Override
    public void documentChanged(DocumentEvent documentEvent) {
        final FileDocumentManager instance = FileDocumentManager.getInstance();
        final VirtualFile file = instance.getFile(documentEvent.getDocument());
        if (file != null) {
            final String currentFile = file.getPath();
            DataContext dataContext = DataManager.getInstance().getDataContext();
            Project project = DataKeys.PROJECT.getData(dataContext);
            String currentProject = null;
            if (project != null) {
                currentProject = project.getName();
            }
            final long currentTime = System.currentTimeMillis() / 1000;
            if ((!currentFile.equals(WakaTime.lastFile) || WakaTime.enoughTimePassed(currentTime)) && !currentFile.contains("/.idea/workspace.xml")) {
                WakaTime.logFile(currentFile, currentProject, false);
                WakaTime.lastFile = currentFile;
                WakaTime.lastTime = currentTime;
            }
        }
    }
}
