#### 最新版本

模块|Common
---|---
最新版本|[![Download](https://jitpack.io/v/like5188/Common.svg)](https://jitpack.io/#like5188/Common)

## 功能介绍
1、常用工具类、自定义View。

2、常用第三方库的引用。

## 使用方法：

1、引用

在Project的gradle中加入：
```groovy
    allprojects {
        repositories {
            ...
            maven { url 'https://jitpack.io' }
        }
    }
```
在Module的gradle中加入：
```groovy
    dependencies {
        compile 'com.github.like5188:Common:版本号'
    }
```

2、常用工具类
```java
    ble
    validator
    AppUtils
    ArrayUtils
    BcdUtils
    ByteUtils
    CheckManager
    ClickTextViewSpanUtils
    ClickUtils
    CloseableEx.kt
    DateUtils
    DialogFragmentEx.kt
    DimensionUtils
    DoubleFormatUtils
    Executors.kt
    FilterUtils
    GlideUtils.kt
    HexUtil
    HighLightUtils
    ImageUtils
    InputSoftKeybordUtils
    JsonEx.kt
    LifecycleEx.kt
    Logger
    MD5Utils
    NetWorkUtils
    NotificationEx.kt
    PathUtils
    PermissionUtils
    PhoneUtils
    RadioManager
    RunningTaskUtils
    RxJavaUtils
    SingletonHolder
    SPUtils.kt
    StatusBarUtils
    StorageUtils
    TabLayoutUtils
    TextViewEx.kt
    TimerUtils
    ToastUtils.kt
    UnitEx.kt
    VibrateUtils
    ViewEx.kt
    ZxingUtils
```

3、自定义View
```java
    badgeview
    banner
    callback
    dragview
    pwdedittext
    toolbar
    update
    viewPagerTransformer
    CircleTextView
    ContainsEmojiEditText
    ExpandView
    MyGridView
    MyVideoView
    RotateTextView
    SidebarView
    SquareImageView
    TimerTextView
    VerticalMarqueeView
```

4、常用第三方库的引用
```java
    // constraint
    api 'com.android.support.constraint:constraint-layout:1.1.3'

    // support
    api 'com.android.support:appcompat-v7:28.0.0'
    api 'com.android.support:design:28.0.0'
    api 'com.android.support:recyclerview-v7:28.0.0'
    api 'com.android.support:palette-v7:28.0.0'// 调色板

    // anko 包含了协程相关的库
    api('org.jetbrains.anko:anko:0.10.8') {
        exclude group: 'com.android.support'
    }

    // kotlin
    api "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin_version"
    api "org.jetbrains.kotlin:kotlin-reflect:$kotlin_version"

    // rxjava2
    api 'io.reactivex.rxjava2:rxjava:2.2.9'
    api 'io.reactivex.rxjava2:rxkotlin:2.3.0'
    api 'io.reactivex.rxjava2:rxandroid:2.1.1'
    api 'com.github.tbruyelle:rxpermissions:0.10.2'

    // fastjson
    api 'com.alibaba:fastjson:1.2.51'

    // 65535
    api 'com.android.support:multidex:1.0.3'
    // retrofit2
    api 'com.squareup.retrofit2:retrofit:2.6.0'
    api 'com.squareup.retrofit2:converter-gson:2.6.0'
    api 'com.squareup.okhttp3:logging-interceptor:3.12.0'

    // okhttp3
    api 'com.squareup.okhttp3:okhttp:3.12.2'

    // dagger
    api 'com.google.dagger:dagger:2.17'
    api 'com.google.dagger:dagger-android:2.17'
    api 'com.google.dagger:dagger-android-support:2.17'

    // arouter
    api('com.alibaba:arouter-api:1.4.0') {
        exclude group: 'com.android.support'
    }

    // room
    api 'android.arch.persistence.room:runtime:1.1.1'
    api 'android.arch.persistence.room:rxjava2:1.1.1'

    // lifecycle
    api 'android.arch.lifecycle:extensions:1.1.1'

    // paging
    api('android.arch.paging:runtime:1.0.1') {
        exclude group: 'com.android.support'
    }
    api "android.arch.paging:rxjava2:1.0.1"

    // work
    api('android.arch.work:work-runtime-ktx:1.0.0-alpha11') {
        exclude group: 'android.arch.lifecycle'
    }

    // glide
    api("com.github.bumptech.glide:glide:4.8.0") {
        exclude group: "com.android.support"
    }
    api "com.github.bumptech.glide:okhttp3-integration:4.8.0"
    api 'jp.wasabeef:glide-transformations:4.0.1'// glide对应的图片处理库，可以转换图片为圆形、圆角矩形、高斯模糊等等效果

    // zxing
    api 'com.google.zxing:core:3.3.3'

    // rxbinding
    api 'com.jakewharton.rxbinding2:rxbinding:2.2.0'

    // PhotoView
    api 'com.github.chrisbanes:PhotoView:2.1.4'// 不能升级2.2.0，因为使用了AndroidX库，不能和support库共存。

    // 图片选择器
    api 'com.github.LuckSiege.PictureSelector:picture_library:v2.2.3'

    // 日期时间格式化工具
    api 'net.danlew:android.joda:2.10.1.2'

    api 'com.google.android:flexbox:1.0.0'

    api 'com.github.like5188:Repository:1.2.5'
    api 'com.github.like5188:LiveDataRecyclerView:1.2.5'
    api 'com.github.like5188:LibRetrofit:1.2.0'
    api 'com.github.like5188.LiveDataBus:livedatabus:1.2.8'

    // 使用 Glide 时需要
    kapt 'com.github.bumptech.glide:compiler:4.8.0'
    // 使用 com.like.common.view.update 中的更新功能时需要
    kapt 'com.github.like5188.LiveDataBus:livedatabus_compiler:1.2.8'
//    kapt 'android.arch.lifecycle:compiler:1.1.1'
//    kapt 'android.arch.persistence.room:compiler:1.1.1'
//    kapt 'com.google.dagger:dagger-compiler:2.17'
//    kapt 'com.google.dagger:dagger-android-processor:2.17'
```