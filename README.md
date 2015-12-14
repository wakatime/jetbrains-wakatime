jetbrains-wakatime
==================

WakaTime is a productivity & time tracking tool for programmers. Once the WakaTime plugin is installed, you get a dashboard with reports about your programming by time, language, project, commit, and branch.

Installation
------------

1. Inside your IDE, select `Preferences` -> `Plugins` -> `Browse Repositories...`.

2. Search for `WakaTime`.
   
3. Click the green `Install Plugin` button.

4. Click `Close` and `OK`, then Re-launch your IDE.

5. Enter your [api key](https://wakatime.com/settings#apikey) in `Tools -> WakaTime API Key`, then click `Save`.

6. Use your IDE like you normally do and your time will be tracked for you automatically.

7. Visit https://wakatime.com to see your logged time.

Screen Shots
------------

![Project Overview](https://wakatime.com/static/img/ScreenShots/ScreenShot-2014-10-29.png)

Configuring
-----------

WakaTime plugins share a common config file `.wakatime.cfg` located in your user home directory with [these options](https://github.com/wakatime/wakatime#configuring) available.

Uninstalling
------------

Inside your IDE, select `Preferences` -> `Plugins`, then find the `WakaTime` plugin. Click `Uninstall`. Then delete your `~/.wakatime.cfg` config file.
