/* ==========================================================
File:        CustomSaveListener.java
Description: Logs time from document save events.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;

public class CustomSaveListener extends FileDocumentManagerAdapter {

    @Override
    public void beforeDocumentSaving(Document document) {
        String currentFile = FileDocumentManager.getInstance().getFile(document).getPath();
        long currentTime = System.currentTimeMillis() / 1000;
        WakaTime.logFile(currentFile, true);
        WakaTime.lastFile = currentFile;
        WakaTime.lastTime = currentTime;
    }
}