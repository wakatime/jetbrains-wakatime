jetbrains-wakatime
==================

Fully automatic time tracking for JetBrains IDEs (IntelliJ IDEA, PyCharm, RubyMine, PhpStorm, AppCode, WebStorm).

Installation
------------

Heads Up! WakaTime depends on [Python](http://www.python.org/getit/) being installed to work correctly.

1. Inside your IDE, select `Preferences` -> `Plugins` -> `Browse Repositories...`.

2. Search for `WakaTime`.
   
3. Click the green `Install Plugin` button.

4. Click `Close` and `OK`, then Re-launch your IDE.

5. Enter your [api key](https://wakatime.com/settings#apikey) from [https://wakatime.com/settings#apikey](https://wakatime.com/settings#apikey), then click `Save`.

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

Remove `Bundle 'wakatime/vim-wakatime'` from your `.vimrc` file, then delete your `~/.wakatime.cfg` config file.
