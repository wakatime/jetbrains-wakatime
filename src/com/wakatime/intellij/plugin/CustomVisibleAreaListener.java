/* ==========================================================
File:        CustomEditorMouseMotionListener.java
Description: Logs time from mouse motion events.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class CustomVisibleAreaListener implements VisibleAreaListener {
    @Override
    public void visibleAreaChanged(VisibleAreaEvent visibleAreaEvent) {
        FileDocumentManager instance = FileDocumentManager.getInstance();
        VirtualFile file = instance.getFile(visibleAreaEvent.getEditor().getDocument());
        Project project = visibleAreaEvent.getEditor().getProject();
        WakaTime.appendHeartbeat(file, project, false);
    }
}
