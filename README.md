# jetbrains-wakatime

[![WakaTime in the JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/7425-wakatime.svg?style=flat-square&color=167dff&label=marketplace)](https://plugins.jetbrains.com/plugin/7425-wakatime)
[![WakaTime Installs](https://img.shields.io/jetbrains/plugin/d/7425-wakatime.svg?style=flat-square&color=167dff)](https://plugins.jetbrains.com/plugin/7425-wakatime)
[![Coding time tracker](https://wakatime.com/badge/github/wakatime/jetbrains-wakatime.svg?style=flat-square&color=167dff)](https://wakatime.com/intellij-idea)

[WakaTime][wakatime] is an open source Jetbrains plugin for metrics, insights, and time tracking automatically generated from your programming activity.

Supports Jetbrains IDES:

* [Android Studio][android studio]
* [AppCode][appcode]
* [CLion][clion]
* [DataGrip][datagrip]
* [GoLand][goland]
* [IntelliJ IDEA][wakatime]
* [PhpStorm][phpstorm]
* [PyCharm][pycharm]
* [Rider][rider]
* [RubyMine][rubymine]
* [WebStorm][webstorm]


## Installation

![install](./install.gif)

1. Inside your IDE, select `Preferences → Plugins`.

2. Search for `WakaTime`.

3. Click the green `Install` button.

4. Re-launch your IDE.

5. Enter your [api key](https://wakatime.com/settings#apikey) in `Tools → WakaTime API Key`, then click `Save`.

6. Use your IDE and your coding activity will be displayed on your [WakaTime dashboard](https://wakatime.com).


## Screen Shots

![Project Overview](https://wakatime.com/static/img/ScreenShots/Screen-Shot-2016-03-21.png)


## Configuring

WakaTime for Jetbrains IDE's can be configured via Tools → WakaTime Settings.

For more settings, WakaTime plugins share a common config file `.wakatime.cfg` located in your user home directory with [these options](https://github.com/wakatime/wakatime-cli/blob/develop/USAGE.md) available.


## Uninstalling

Inside your IDE, select `Preferences → Plugins`, then find the `WakaTime` plugin. Click `Uninstall`. Then delete your `~/.wakatime.cfg` config file.


## Troubleshooting

If you’re using a proxy, try excluding `*.wakatime.com` from using your IDE proxy:

![jetbrains proxy settings](https://wakatime.com/static/img/ScreenShots/jetbrains-no-proxy-setting.png?v=1)

Next, turn on debug mode from `Tools → WakaTime Settings`. Then restart your IDE.

Note: If the plugin wasn’t loaded, you won’t have a WakaTime Settings menu.
In that case, check for error messages in your IDE’s `idea.log` file indicating why the plugin couldn’t load.

![wakatime settings menu](https://wakatime.com/static/img/ScreenShots/jetbrains-wakatime-menu.png?v=1)

Check your IDE’s `idea.log` file for WakaTime related messages:

`Help → Show Log` ( [Locating your idea.log file](https://intellij-support.jetbrains.com/hc/en-us/articles/207241085-Locating-IDE-log-files) )

For more general troubleshooting information, see [WakaTime CLI Troubleshooting](https://github.com/wakatime/wakatime-cli/blob/develop/TROUBLESHOOTING.md).

[wakatime]: https://wakatime.com/
[IntelliJ IDEA]: https://wakatime.com/intellij-idea
[Android Studio]: https://wakatime.com/android-studio
[AppCode]: https://wakatime.com/appcode
[CLion]: https://wakatime.com/clion
[DataGrip]: https://wakatime.com/datagrip
[GoLand]: https://wakatime.com/goland
[PhpStorm]: https://wakatime.com/phpstorm
[PyCharm]: https://wakatime.com/pycharm
[Rider]: https://wakatime.com/rider
[RubyMine]: https://wakatime.com/rubymine
[WebStorm]: https://wakatime.com/webstorm

