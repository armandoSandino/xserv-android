<p align="center" >
  <img src="http://mobile-italia.com/xserv/assets/images/logo-big.png?t=3" alt="Xserv" title="Xserv">
</p>

<br>

This library is client that allows Android clients to connect to the [Xserv](http://mobile-italia.com/xserv/) WebSocket API.<br>
[Xserv](http://mobile-italia.com/xserv/) iprovides a complete solution to implement the backend of all your applications such as web, mobile, desktop, embedded or otherwise.

## How To Get Started

- [Download xserv-android](https://github.com/xserv/xserv-android/archive/master.zip) and try out the included Android example apps.
- Read the ["Getting Started" guide](http://mobile-italia.com/xserv/docs#).

## Installation

Add internet permission to you `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### Gradle

Add as a dependency to your build.gradle `build.gradle`:

```java
dependencies {
    ...
    compile(group: 'com.mi.xserv', name: 'xserv-android', version: '+', ext: 'aar', classifier: '')
}
```

## Credits

Xserv is owned and maintained by the [mobile-italia.com] (http://mobile-italia.com).

Dependencies:

https://github.com/koush/AndroidAsync

### Security Disclosure

If you believe you have identified a security vulnerability with Xserv, you should report it as soon as possible via email to xserv.dev@gmail.com. Please do not post it to a public issue tracker.

## License

Xserv is released under the GNU General Public License Version 3. See LICENSE for details.
