/* ==========================================================
File:        CustomSaveListener.java
Description: Sends a heartbeat when a file is saved.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.vfs.VirtualFile;

import java.math.BigDecimal;

public class CustomSaveListener extends FileDocumentManagerAdapter {

    @Override
    public void beforeDocumentSaving(Document document) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        WakaTime.appendHeartbeat(file, true);
    }
}