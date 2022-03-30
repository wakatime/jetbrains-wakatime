/* ==========================================================
File:        Heartbeat.java
Description: Stores coding activity waiting to be sent to the api.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import java.math.BigDecimal;

public class Heartbeat {
    public String entity;
    public BigDecimal timestamp;
    public Boolean isWrite;
    public String project;
    public String language;
    public Boolean isBuilding;
}
