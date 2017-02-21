/* ==========================================================
File:        CustomEditorMouseListener.java
Description: Logs time from mouse click events.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class CustomEditorMouseListener  implements EditorMouseListener {
    @Override
    public void mousePressed(EditorMouseEvent editorMouseEvent) {
        FileDocumentManager instance = FileDocumentManager.getInstance();
        VirtualFile file = instance.getFile(editorMouseEvent.getEditor().getDocument());
        Project project = editorMouseEvent.getEditor().getProject();
        WakaTime.appendHeartbeat(file, project, false);
    }

    @Override
    public void mouseClicked(EditorMouseEvent editorMouseEvent) {
    }

    @Override
    public void mouseReleased(EditorMouseEvent editorMouseEvent) {
    }

    @Override
    public void mouseEntered(EditorMouseEvent editorMouseEvent) {
    }

    @Override
    public void mouseExited(EditorMouseEvent editorMouseEvent) {
    }
}
