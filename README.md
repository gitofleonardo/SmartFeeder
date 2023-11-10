# SmartFeeder

SmartFeeder, forked from [SmartSpacer](https://github.com/KieronQuinn/Smartspacer), is an Android app that focuses on Launcher3 overlay customizations.

## Features

- Multiple columns for widget display.
- Use spring animations, visually smoother.
- Directly drag and drop widgets on overlay page.
- Reorganize UI.

## Screenshots

![Screenshots](assets/home_page.png)

## Integrate

Want to integrate to your rom? Now the good news is that it's totally possible to replace google feed with this overlay, and run without root. Please refer to [this commit](https://github.com/gitofleonardo/android_packages_apps_Launcher3/commit/caacdd3db6fee0da00da02f7771c93fa006a2302), download the modded google feed jar file, and import to your `Launcher3` project.

By the following command

```bash
adb shell settings put global launcher_overlay_package "com.hhvvg.smartfeeder"
```

then restarting launcher, your google feed will be replaced!

## Licences 

The Smartspacer app is licensed under the [GNU GPL v3 licence](https://github.com/KieronQuinn/Smartspacer/blob/main/LICENSE), the SDK is licensed under [Apache 2.0](https://github.com/KieronQuinn/Smartspacer/blob/main/sdk-core/LICENSE). And as a fork of the original project, this project also uses the GNU GPL v3 licence.