/* ==========================================================
File:        CustomCaretListener.java
Description: Logs time from cursor movement events.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class CustomCaretListener implements CaretListener {
    @Override
    public void caretPositionChanged(CaretEvent event) {
        // WakaTime.log.debug("caret event");
        try {
            if (!WakaTime.isAppActive()) return;
            Editor editor = event.getEditor();
            Document document = editor.getDocument();
            VirtualFile file = WakaTime.getFile(document);
            if (file == null) return;
            Project project = editor.getProject();
            if (!WakaTime.isProjectInitialized(project)) return;
            ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                    LineStats lineStats = WakaTime.getLineStats(document, editor);
                    WakaTime.appendHeartbeat(file, project, false, lineStats);
                }
            });
        } catch(Exception e) {
            WakaTime.debugException(e);
        }
    }
}
