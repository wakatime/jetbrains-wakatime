/* ==========================================================
File:        LineStats.java
Description: Stores total lines in file and current cursor position.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;


public class LineStats {
    public Integer lineCount;
    public Integer lineNumber;
    public Integer cursorPosition;
    public Long updatedAt;

    public boolean isOK() {
        return lineCount != null && lineNumber != null && cursorPosition != null;
    }

    public boolean hasLineCount() {
        return lineCount != null;
    }
}
