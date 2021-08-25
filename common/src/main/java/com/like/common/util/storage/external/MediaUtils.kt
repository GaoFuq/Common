package com.like.common.util.storage.external

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.BaseColumns
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.RequiresApi
import androidx.core.database.getFloatOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.like.common.util.RequestPermissionWrapper
import com.like.common.util.StartActivityForResultWrapper
import com.like.common.util.StartIntentSenderForResultWrapper
import com.like.common.util.UriUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Date
import java.util.concurrent.TimeUnit

// 分区存储改变了应用在设备的外部存储设备中存储和访问文件的方式。
/**
 * 外部存储公共目录 操作媒体文件（图片、音频、视频）的工具类。
 * 外部存储公共目录：应用卸载后，文件不会删除。
 * /storage/emulated/(0/1/...)/(MediaStore.Images/MediaStore.Video/MediaStore.Audio)
 *
 * 权限：
 * Android10以下：需要申请存储权限：<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
 * Android10及其以上：访问其它应用或者自己的旧版本应用的“媒体文件”时需要申请 READ_EXTERNAL_STORAGE 权限。
 *          当以 Android 10 或更高版本为目标平台的应用启用了分区存储时，系统会将每个媒体文件归因于一个应用，这决定了应用在未请求任何存储权限时可以访问的文件。每个文件只能归因于一个应用。因此，如果您的应用创建的媒体文件存储在照片、视频或音频文件媒体集合中，应用便可以访问该文件。
 *          但是，如果用户卸载并重新安装您的应用，您必须请求 READ_EXTERNAL_STORAGE 才能访问应用最初创建的文件。此权限请求是必需的，因为系统认为文件归因于以前安装的应用版本，而不是新安装的版本。
 *          MediaStore数据库增加owner_package_name字段记录文件属于哪个应用， 应用卸载后owner_package_name字段会置空，也就是说，卸载重装后，之前创建的文件，已不属于应用创建的了，需要相关存储权限才能再次读写
 *      WRITE_EXTERNAL_STORAGE 权限在 android11 里面已被废弃。
 *      所有文件访问权限：像文件管理操作或备份和还原操作等需要访问大量的文件，通过执行以下操作，这些应用可以获得” 所有文件访问权限”：
 *      声明 MANAGE_EXTERNAL_STORAGE 权限
 *      将用户引导至系统设置页面，在该页面上，用户可以对应用启用授予所有文件的管理权限选项
 *
 * 访问方式：
 * Android10以下：Environment.getExternalStorageDirectory()
 * Android10及其以上：MediaStore API
 *      Android11：如果应用具有 READ_EXTERNAL_STORAGE 权限，则可以使用文件直接路径去访问媒体，但是应用的性能会略有下降，还是推荐使用 MediaStore API。
 * 注意：如果您不希望媒体扫描程序发现您的文件，请在特定于应用的目录中添加名为 .nomedia 的空文件（请注意文件名中的句点前缀）。这可以防止媒体扫描程序读取您的媒体文件并通过 MediaStore API 将它们提供给其他应用。
 *
 * 按照分区存储的规范，将用户数据(例如图片、视频、音频等)保存在公共目录，把应用数据保存在私有目录
 *
 * 如果您需要与其他应用共享单个文件或应用数据，可以使用 Android 提供的以下 API：
 *      如果您需要与其他应用共享特定文件，请使用 FileProvider API。
 *      如果您需要向其他应用提供数据，可以使用内容提供器。借助内容提供器，您可以完全控制向其他应用提供的读取和写入访问权限。尽管您可以将内容提供器与任何存储媒介一起使用，但它们通常与数据库一起使用。
 *      媒体共享：按照内容提供程序创建指南中的建议使用 content:// URI。如需在搭载 Android 10 的设备上访问共享存储空间中的其他文件，建议您在应用的清单文件中将 requestLegacyExternalStorage 设置为 true 以停用分区存储。
 *
 * Android 存储用例和最佳做法：https://developer.android.google.cn/training/data-storage/use-cases
 *
 * MediaStore 是 android 系统提供的一个多媒体数据库，专门用于存放多媒体信息的，通过 ContentResolver.query() 获取 Cursor 即可对数据库进行操作。
 *
 * MediaStore.Files: 共享的文件,包括多媒体和非多媒体信息
 * MediaStore.Image: 存放图片信息
 * MediaStore.Audio: 存放音频信息
 * MediaStore.Video: 存放视频信息
 * 每个内部类中都又包含了 Media、Thumbnails、MediaColumns(ImageColumns、AudioColumns、VideoColumns)，分别提供了媒体信息，缩略信息和 操作字段。
 *
 * 执行批量操作需要的权限(在 Android 10 中，应用在对MediaStore的每一个文件请求编辑或删除时都必须一个个地得到用户的确认。而在 Android 11 中，应用可以一次请求修改或者删除多个媒体文件。)
 * createWriteRequest (ContentResolver, Collection)	用户向应用授予对指定媒体文件组的写入访问权限的请求。
 * createFavoriteRequest (ContentResolver, Collection, boolean)	用户将设备上指定的媒体文件标记为 “收藏” 的请求。对该文件具有读取访问权限的任何应用都可以看到用户已将该文件标记为 “收藏”。
 * createTrashRequest (ContentResolver, Collection, boolean)	用户将指定的媒体文件放入设备垃圾箱的请求。垃圾箱中的内容在特定时间段（默认为 7 天）后会永久删除。
 * createDeleteRequest (ContentResolver, Collection)	用户立即永久删除指定的媒体文件（而不是先将其放入垃圾箱）的请求。
 * 系统在调用以上任何一个方法后，会构建一个 PendingIntent 对象。应用调用此 intent 后，用户会看到一个对话框，请求用户同意应用更新或删除指定的媒体文件。
 */
object MediaUtils {

    /**
     * 拍照
     * 照片存储位置：/storage/emulated/0/Pictures
     *
     * @param isThumbnail     表示返回值是否为缩略图
     */
    suspend fun takePhoto(
        requestPermissionWrapper: RequestPermissionWrapper,
        startActivityForResultWrapper: StartActivityForResultWrapper,
        startIntentSenderForResultWrapper: StartIntentSenderForResultWrapper,
        isThumbnail: Boolean = false
    ): Bitmap? {
        // 如果你的应用没有配置android.permission.CAMERA权限，则不会出现下面的问题。如果你的应用配置了android.permission.CAMERA权限，那么你的应用必须获得该权限的授权，否则会出错
        if (!requestPermissionWrapper.requestPermission(Manifest.permission.CAMERA)) {
            return null
        }

        val context = requestPermissionWrapper.activity.applicationContext
        //android 11 无法唤起第三方相机了，只能唤起系统相机.如果要使用特定的第三方相机应用来代表其捕获图片或视频，可以通过为intent设置软件包名称或组件来使这些intent变得明确。
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        return if (isThumbnail) {
            // 如果[MediaStore.EXTRA_OUTPUT]为 null，那么返回拍照的缩略图，可以通过下面的方法获取。
            startActivityForResultWrapper.startActivityForResult(intent)?.getParcelableExtra("data")
        } else {
            val imageUri = createFile(
                requestPermissionWrapper,
                startIntentSenderForResultWrapper,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                System.currentTimeMillis().toString(),
                Environment.DIRECTORY_PICTURES
            ) ?: return null
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
            // 如果[MediaStore.EXTRA_OUTPUT]不为 null，那么返回值不为 null，表示拍照成功返回，其中 imageUri 参数则是照片的 Uri。
            startActivityForResultWrapper.startActivityForResult(intent)
            UriUtils.getBitmapFromUriByFileDescriptor(context, imageUri)
        }
    }

    /**
     * 如果启用了分区存储，集合只会显示您的应用创建的照片、视频和音频文件。
     * 如果分区存储不可用或未使用，集合将显示所有类型的媒体文件。
     *
     * 如果要显示特定文件夹中的文件，请求 READ_EXTERNAL_STORAGE 权限，根据 MediaColumns.DATA 的值检索媒体文件，该值包含磁盘上的媒体项的绝对文件系统路径。
     *
     * @param selection         查询条件
     * @param selectionArgs     查询条件填充值
     * @param sortOrder         排序依据
     */
    suspend fun getFiles(
        requestPermissionWrapper: RequestPermissionWrapper,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): List<FileEntity> {
        if (!requestPermissionWrapper.requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            return emptyList()
        }
        val context = requestPermissionWrapper.activity.applicationContext
        val files = mutableListOf<FileEntity>()
        withContext(Dispatchers.IO) {
            val projection = BaseEntity.projection + MediaEntity.projection + FileEntity.projection
            val contentUri = MediaStore.Files.getContentUri("external")
            context.contentResolver.query(contentUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                while (cursor.moveToNext()) {
                    files += FileEntity().apply { fill(requestPermissionWrapper, cursor, contentUri) }
                }
            }
        }
        return files
    }

    /**
     * 存储在 Download/ 目录中。在搭载 Android 10（API 级别 29）及更高版本的设备上，这些文件存储在 MediaStore.Downloads 表格中。此表格在 Android 9（API 级别 28）及更低版本中不可用。
     *
     * @param selection         查询条件
     * @param selectionArgs     查询条件填充值
     * @param sortOrder         排序依据
     */
    suspend fun getDownloads(
        requestPermissionWrapper: RequestPermissionWrapper,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): List<DownloadEntity> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return emptyList()
        }

        if (!requestPermissionWrapper.requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            return emptyList()
        }
        val context = requestPermissionWrapper.activity.applicationContext
        val files = mutableListOf<DownloadEntity>()
        withContext(Dispatchers.IO) {
            val projection = BaseEntity.projection + MediaEntity.projection + DownloadEntity.projectionQ
            val contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            context.contentResolver.query(contentUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                while (cursor.moveToNext()) {
                    files += DownloadEntity().apply { fill(requestPermissionWrapper, cursor, contentUri) }
                }
            }
        }
        return files
    }

    /**
     * （包括照片和屏幕截图），存储在 DCIM/ 和 Pictures/ 目录中。系统将这些文件添加到 MediaStore.Images 表格中。
     *
     * 您需要在应用的清单中声明 ACCESS_MEDIA_LOCATION 权限，然后在运行时请求此权限，应用才能从照片中检索未编辑的 Exif 元数据。
     * 用户在 Settings UI 里看不到这个权限，但是它属于运行时权限，所以必须要在 Manifest 里声明该权限，并在运行时同时请求该权限和读取外部存储权限
     * 一些照片在其 Exif 元数据中包含位置信息，以便用户查看照片的拍摄地点。但是，由于此位置信息属于敏感信息，如果应用使用了分区存储，默认情况下 Android 10 会对应用隐藏此信息。
     *
     * @param selection         查询条件
     * @param selectionArgs     查询条件填充值
     * @param sortOrder         排序依据
     */
    suspend fun getImages(
        requestPermissionWrapper: RequestPermissionWrapper,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): List<ImageEntity> {
        if (!requestPermissionWrapper.requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            return emptyList()
        }
        val context = requestPermissionWrapper.activity.applicationContext
        val files = mutableListOf<ImageEntity>()
        withContext(Dispatchers.IO) {
            val projection = BaseEntity.projection + MediaEntity.projection + ImageEntity.projection + ImageEntity.projectionQ
            val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(contentUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                while (cursor.moveToNext()) {
                    files += ImageEntity().apply {
                        fill(requestPermissionWrapper, cursor, contentUri)
                    }
                }
            }
        }
        return files
    }

    /**
     * 存储在 Alarms/、Audiobooks/、Music/、Notifications/、Podcasts/ 和 Ringtones/ 目录中，以及位于 Music/ 或 Movies/ 目录中的音频播放列表中。系统将这些文件添加到 MediaStore.Audio 表格中。
     *
     * @param selection         查询条件
     * @param selectionArgs     查询条件填充值
     * @param sortOrder         排序依据
     */
    suspend fun getAudios(
        requestPermissionWrapper: RequestPermissionWrapper,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): List<AudioEntity> {
        if (!requestPermissionWrapper.requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            return emptyList()
        }
        val context = requestPermissionWrapper.activity.applicationContext
        val files = mutableListOf<AudioEntity>()
        withContext(Dispatchers.IO) {
            val projection = BaseEntity.projection + MediaEntity.projection + AudioEntity.projectionQ + AudioEntity.projectionR
            val contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(contentUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                while (cursor.moveToNext()) {
                    files += AudioEntity().apply { fill(requestPermissionWrapper, cursor, contentUri) }
                }
            }
        }
        return files
    }

    /**
     * 存储在 DCIM/、Movies/ 和 Pictures/ 目录中。系统将这些文件添加到 MediaStore.Video 表格中。
     *
     * @param selection         查询条件
     * @param selectionArgs     查询条件填充值
     * @param sortOrder         排序依据
     */
    suspend fun getVideos(
        requestPermissionWrapper: RequestPermissionWrapper,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): List<VideoEntity> {
        if (!requestPermissionWrapper.requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            return emptyList()
        }
        val context = requestPermissionWrapper.activity.applicationContext
        val files = mutableListOf<VideoEntity>()
        withContext(Dispatchers.IO) {
            val projection =
                BaseEntity.projection + MediaEntity.projection + VideoEntity.projection + VideoEntity.projectionQ + VideoEntity.projectionR
            val contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(contentUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                while (cursor.moveToNext()) {
                    files += VideoEntity().apply { fill(requestPermissionWrapper, cursor, contentUri) }
                }
            }
        }
        return files
    }

    /**
     * 创建文件
     * 如果您的应用执行可能非常耗时的操作（例如写入媒体文件），那么在处理文件时对其进行独占访问非常有用。在搭载 Android 10 或更高版本的设备上，您的应用可以通过将 IS_PENDING 标记的值设为 1 来获取此独占访问权限。如此一来，只有您的应用可以查看该文件，直到您的应用将 IS_PENDING 的值改回 0。
     *
     * @param uri           content://media/<volumeName>/<Uri路径>
     * 其中 volumeName 可以是：
     * [android.provider.MediaStore.VOLUME_INTERNAL]
     * [android.provider.MediaStore.VOLUME_EXTERNAL]
     * [android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY]
     * [android.provider.MediaStore.getExternalVolumeNames]
     *
    ●  Audio
    ■  Internal: MediaStore.Audio.Media.INTERNAL_CONTENT_URI
    content://media/internal/audio/media。
    ■  External: MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    content://media/external/audio/media。
    ■  可移动存储: MediaStore.Audio.Media.getContentUri
    content://media/<volumeName>/audio/media。

    ●  Video
    ■    Internal: MediaStore.Video.Media.INTERNAL_CONTENT_URI
    content://media/internal/video/media。
    ■    External: MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    content://media/external/video/media。
    ■    可移动存储: MediaStore.Video.Media.getContentUri
    content://media/<volumeName>/video/media。

    ●  Image
    ■    Internal: MediaStore.Images.Media.INTERNAL_CONTENT_URI
    content://media/internal/images/media。
    ■    External: MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    content://media/external/images/media。
    ■    可移动存储: MediaStore.Images.Media.getContentUri
    content://media/<volumeName>/images/media。

    下面两种非媒体文件请使用 SAF 操作
    ●  File
    ■    MediaStore.Files.Media.getContentUri
    content://media/<volumeName>/file。

    ●  Downloads
    ■    Internal: MediaStore.Downloads.INTERNAL_CONTENT_URI
    content://media/internal/downloads。
    ■    External: MediaStore.Downloads.EXTERNAL_CONTENT_URI
    content://media/external/downloads。
    ■    可移动存储: MediaStore.Downloads.getContentUri
    content://media/<volumeName>/downloads。
     * @param displayName   文件名称。如果是 android10 以下，则必须要有后缀。android10 及以上版本，最好不加后缀，因为有些后缀不能被识别，比如".png"，创建后的文件名会变成".png.jpg"
     * @param relativePath  相对路径。比如"Pictures/like"。如果 >= android10，那么此路径不存在也会自动创建；否则会报错。
     * 如果 uri 为 internal 类型，那么会报错：Writing exception to parcel java.lang.UnsupportedOperationException: Writing to internal storage is not supported.
     * 如果 uri 为 External、可移动存储 类型，那么 relativePath 格式：root/xxx。注意：根目录 root 必须是以下这些：
     * Audio：[Alarms, Music, Notifications, Podcasts, Ringtones]
     * Video：[DCIM, Movies]
     * Image：[DCIM, Pictures]
     * File：[Download, Documents]
     * Downloads：[Download]
     * @param onWrite      写入数据的操作
     */
    suspend fun createFile(
        requestPermissionWrapper: RequestPermissionWrapper,
        startIntentSenderForResultWrapper: StartIntentSenderForResultWrapper,
        uri: Uri?,
        displayName: String,
        relativePath: String,
        onWrite: ((ParcelFileDescriptor?) -> Unit)? = null
    ): Uri? {
        uri ?: return null
        if (displayName.isEmpty()) {
            return null
        }
        if (relativePath.isEmpty()) {
            return null
        }

        when {
            Build.VERSION.SDK_INT > Build.VERSION_CODES.Q -> {
                if (!createWriteRequest(startIntentSenderForResultWrapper, listOf(uri))) {
                    return null
                }
            }
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> {
                if (!requestPermissionWrapper.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    return null
                }
            }
        }
        val resolver = requestPermissionWrapper.activity.applicationContext.contentResolver
        return withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                        if (onWrite != null) {
                            // 如果您的应用执行可能非常耗时的操作（例如写入媒体文件），那么在处理文件时对其进行独占访问非常有用。
                            // 在搭载 Android 10 或更高版本的设备上，您的应用可以通过将 IS_PENDING 标记的值设为 1 来获取此独占访问权限。
                            // 如此一来，只有您的应用可以查看该文件，直到您的应用将 IS_PENDING 的值改回 0。
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                    } else {
                        put(
                            MediaStore.MediaColumns.DATA,
                            "${Environment.getExternalStorageDirectory().path}/$relativePath/$displayName"
                        )
                    }
                }

                resolver.insert(uri, values)?.also {
                    if (onWrite != null) {
                        resolver.openFileDescriptor(it, "w", null).use { pfd ->
                            // Write data into the pending file.
                            onWrite(pfd)
                        }
                        // Now that we're finished, release the "pending" status, and allow other apps
                        // to use.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            values.clear()
                            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                            resolver.update(it, values, null, null)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * 更新文件。
     *
     * @param relativePath  相对路径，>= android10 有效，用于移动文件。比如"Pictures/like"。如果 >= android10，那么此路径不存在也会自动创建；否则会报错。
     */
    suspend fun updateFile(
        requestPermissionWrapper: RequestPermissionWrapper,
        startIntentSenderForResultWrapper: StartIntentSenderForResultWrapper,
        uri: Uri?,
        displayName: String,
        relativePath: String = "",
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): Boolean {
        uri ?: return false
        if (displayName.isEmpty()) {
            return false
        }
        when {
            Build.VERSION.SDK_INT > Build.VERSION_CODES.Q -> {
                if (!createWriteRequest(startIntentSenderForResultWrapper, listOf(uri))) {
                    return false
                }
            }
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> {
                if (!requestPermissionWrapper.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    return false
                }
            }
        }
        return withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    }
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                }
                requestPermissionWrapper.activity.applicationContext.contentResolver.update(uri, values, selection, selectionArgs) > 0
            } catch (securityException: SecurityException) {
                // 如果您的应用使用分区存储，它通常无法更新其他应用存放到媒体库中的媒体文件。
                // 不过，您仍可通过捕获平台抛出的 RecoverableSecurityException 来征得用户同意修改文件。然后，您可以请求用户授予您的应用对此特定内容的写入权限。
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    (securityException as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender?.let {
                        requestPermissionWrapper.activity.startIntentSenderForResult(it, 0, null, 0, 0, 0, null)
                    }
                }
                false
            }
        }
    }

    /**
     * 删除文件
     *
     * 如果启用了分区存储，您就需要为应用要移除的每个文件捕获 RecoverableSecurityException
     */
    suspend fun deleteFile(
        requestPermissionWrapper: RequestPermissionWrapper,
        startIntentSenderForResultWrapper: StartIntentSenderForResultWrapper,
        uri: Uri?
    ): Boolean {
        uri ?: return false
        when {
            Build.VERSION.SDK_INT > Build.VERSION_CODES.Q -> {
                if (!createDeleteRequest(startIntentSenderForResultWrapper, listOf(uri))) {
                    return false
                }
            }
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> {
                if (!requestPermissionWrapper.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    return false
                }
            }
        }
        return withContext(Dispatchers.IO) {
            try {
                startIntentSenderForResultWrapper.activity.applicationContext.contentResolver.delete(uri, null, null) > 0
            } catch (securityException: SecurityException) {
                // 如果您的应用使用分区存储，它通常无法更新其他应用存放到媒体库中的媒体文件。
                // 不过，您仍可通过捕获平台抛出的 RecoverableSecurityException 来征得用户同意修改文件。然后，您可以请求用户授予您的应用对此特定内容的写入权限。
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    (securityException as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender?.let {
                        startIntentSenderForResultWrapper.activity.startIntentSenderForResult(it, 0, null, 0, 0, 0, null)
                    }
                }
                false
            }
        }
    }

    /**
     * 用户向应用授予对指定媒体文件组的写入访问权限的请求。
     *
     * 系统在调用此方法后，会构建一个 PendingIntent 对象。应用调用此 intent 后，用户会看到一个对话框，请求用户同意应用更新指定的媒体文件。
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun createWriteRequest(
        startIntentSenderForResultWrapper: StartIntentSenderForResultWrapper,
        uris: List<Uri>
    ): Boolean {
        val pendingIntent =
            MediaStore.createWriteRequest(startIntentSenderForResultWrapper.activity.applicationContext.contentResolver, uris)
        val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent).build()
        // Launch a system prompt requesting user permission for the operation.
        return startIntentSenderForResultWrapper.startIntentSenderForResult(intentSenderRequest)
    }

    /**
     * 用户立即永久删除指定的媒体文件（而不是先将其放入垃圾箱）的请求。
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun createDeleteRequest(
        startIntentSenderForResultWrapper: StartIntentSenderForResultWrapper,
        uris: List<Uri>
    ): Boolean {
        val pendingIntent =
            MediaStore.createDeleteRequest(startIntentSenderForResultWrapper.activity.applicationContext.contentResolver, uris)
        val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent).build()
        // Launch a system prompt requesting user permission for the operation.
        return startIntentSenderForResultWrapper.startIntentSenderForResult(intentSenderRequest)
    }

    /**
     * 用户将指定的媒体文件放入设备垃圾箱的请求。垃圾箱中的内容会在系统定义的时间段后被永久删除。
     *
     * @param isTrashed     注意：如果您的应用是设备 OEM 的预安装图库应用，您可以将文件放入垃圾箱而不显示对话框。如需执行该操作，请直接将 IS_TRASHED 设置为 1。及把参数设置为 true
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun createTrashRequest(
        startIntentSenderForResultWrapper: StartIntentSenderForResultWrapper,
        uris: List<Uri>,
        isTrashed: Boolean
    ): Boolean {
        val pendingIntent = MediaStore.createTrashRequest(
            startIntentSenderForResultWrapper.activity.applicationContext.contentResolver,
            uris,
            isTrashed
        )
        val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent).build()
        // Launch a system prompt requesting user permission for the operation.
        return startIntentSenderForResultWrapper.startIntentSenderForResult(intentSenderRequest)
    }

    /**
     * 用户将设备上指定的媒体文件标记为“收藏”的请求。对该文件具有读取访问权限的任何应用都可以看到用户已将该文件标记为“收藏”。
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun createFavoriteRequest(
        startIntentSenderForResultWrapper: StartIntentSenderForResultWrapper,
        uris: List<Uri>,
        isFavorite: Boolean
    ): Boolean {
        val pendingIntent = MediaStore.createFavoriteRequest(
            startIntentSenderForResultWrapper.activity.applicationContext.contentResolver,
            uris,
            isFavorite
        )
        val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent).build()
        // Launch a system prompt requesting user permission for the operation.
        return startIntentSenderForResultWrapper.startIntentSenderForResult(intentSenderRequest)
    }

    open class BaseEntity {
        var id: Long? = null
        var uri: Uri? = null

        companion object {
            val projection = arrayOf(
                BaseColumns._ID
            )
        }

        open suspend fun fill(requestPermissionWrapper: RequestPermissionWrapper, cursor: Cursor, uri: Uri) {
            with(cursor) {
                this@BaseEntity.id = getLongOrNull(getColumnIndexOrThrow(projection[0]))
                this@BaseEntity.uri = ContentUris.withAppendedId(uri, id ?: -1L)
            }
        }
    }

    open class MediaEntity : BaseEntity() {
        var size: Int? = null
        var displayName: String? = null
        var title: String? = null
        var mimeType: String? = null
        var dateAdded: Date? = null

        companion object {
            val projection = arrayOf(
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.TITLE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_ADDED
            )
        }

        override suspend fun fill(requestPermissionWrapper: RequestPermissionWrapper, cursor: Cursor, uri: Uri) {
            super.fill(requestPermissionWrapper, cursor, uri)
            with(cursor) {
                this@MediaEntity.size = getIntOrNull(getColumnIndexOrThrow(projection[0]))
                this@MediaEntity.displayName = getStringOrNull(getColumnIndexOrThrow(projection[1]))
                this@MediaEntity.title = getStringOrNull(getColumnIndexOrThrow(projection[2]))
                this@MediaEntity.mimeType = getStringOrNull(getColumnIndexOrThrow(projection[3]))
                this@MediaEntity.dateAdded = Date(TimeUnit.SECONDS.toMillis(getLong(getColumnIndexOrThrow(projection[4]))))
            }
        }

    }

    class FileEntity : MediaEntity() {
        /**
        int MEDIA_TYPE_NONE = 0;
        int MEDIA_TYPE_IMAGE = 1;
        int MEDIA_TYPE_AUDIO = 2;
        int MEDIA_TYPE_VIDEO = 3;
        int MEDIA_TYPE_PLAYLIST = 4;
        int MEDIA_TYPE_SUBTITLE = 5;
        int MEDIA_TYPE_DOCUMENT = 6;
         */
        var mediaType: Int? = null

        companion object {
            val projection = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE
            )
        }

        override fun toString(): String {
            return "FileEntity(id=$id, uri=$uri, " +
                    "size=$size, displayName=$displayName, title=$title, mimeType=$mimeType, dateAdded=$dateAdded, " +
                    "mediaType=${getMediaTypeString()})"
        }

        private fun getMediaTypeString(): String = when (mediaType) {
            1 -> "image"
            2 -> "audio"
            3 -> "video"
            4 -> "playlist"
            5 -> "subtitle"
            6 -> "document"
            else -> "none"
        }

        override suspend fun fill(requestPermissionWrapper: RequestPermissionWrapper, cursor: Cursor, uri: Uri) {
            super.fill(requestPermissionWrapper, cursor, uri)
            with(cursor) {
                this@FileEntity.mediaType = getIntOrNull(getColumnIndex(projection[0]))
            }
        }
    }

    class ImageEntity : MediaEntity() {
        var description: String? = null
        var width: Int? = null
        var height: Int? = null
        var latitude: Float? = null
        var longitude: Float? = null
        var orientation: Int? = null

        companion object {
            val projection = arrayOf(
                MediaStore.Images.ImageColumns.DESCRIPTION,
                MediaStore.Images.ImageColumns.WIDTH,
                MediaStore.Images.ImageColumns.HEIGHT,
                MediaStore.Images.ImageColumns.LATITUDE,
                MediaStore.Images.ImageColumns.LONGITUDE,
            )

            @RequiresApi(Build.VERSION_CODES.Q)
            val projectionQ = arrayOf(
                MediaStore.Images.ImageColumns.ORIENTATION
            )
        }

        override fun toString(): String {
            return "ImageEntity(id=$id, uri=$uri, " +
                    "size=$size, displayName=$displayName, title=$title, mimeType=$mimeType, dateAdded=$dateAdded, " +
                    "description=$description, width=$width, height=$height, orientation=$orientation, latitude=$latitude, longitude=$longitude)"
        }

        override suspend fun fill(requestPermissionWrapper: RequestPermissionWrapper, cursor: Cursor, uri: Uri) {
            super.fill(requestPermissionWrapper, cursor, uri)
            with(cursor) {
                this@ImageEntity.description = getStringOrNull(getColumnIndexOrThrow(projection[0]))
                this@ImageEntity.width = getIntOrNull(getColumnIndexOrThrow(MediaEntity.projection[1]))
                this@ImageEntity.height = getIntOrNull(getColumnIndexOrThrow(MediaEntity.projection[2]))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    this@ImageEntity.orientation = getIntOrNull(getColumnIndexOrThrow(projectionQ[0]))
                    if (Environment.isExternalStorageLegacy()) {
                        // 如果没有开启分区存储
                        this@ImageEntity.latitude = getFloatOrNull(getColumnIndexOrThrow(projection[3]))
                        this@ImageEntity.longitude = getFloatOrNull(getColumnIndexOrThrow(projection[4]))
                    } else {
                        // 如果开启了分区存储，以下面的方式来获取位置信息。
                        withContext(Dispatchers.Main) {
                            if (requestPermissionWrapper.requestPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)) {
                                val array = UriUtils.getLatLongFromImageUri(
                                    requestPermissionWrapper.activity.applicationContext,
                                    this@ImageEntity.uri
                                )
                                this@ImageEntity.latitude = array?.get(0)
                                this@ImageEntity.longitude = array?.get(1)
                            }
                        }
                    }
                } else {
                    this@ImageEntity.latitude = getFloatOrNull(getColumnIndexOrThrow(projection[3]))
                    this@ImageEntity.longitude = getFloatOrNull(getColumnIndexOrThrow(projection[4]))
                }
            }
        }
    }

    class AudioEntity : MediaEntity() {
        var duration: Int? = null
        var artist: String? = null
        var album: String? = null

        companion object {
            @RequiresApi(Build.VERSION_CODES.Q)
            val projectionQ = arrayOf(
                MediaStore.Audio.AudioColumns.DURATION,
            )

            @RequiresApi(Build.VERSION_CODES.R)
            val projectionR = arrayOf(
                MediaStore.Audio.AudioColumns.ARTIST,
                MediaStore.Audio.AudioColumns.ALBUM
            )
        }

        override fun toString(): String {
            return "AudioEntity(id=$id, uri=$uri, " +
                    "size=$size, displayName=$displayName, title=$title, mimeType=$mimeType, dateAdded=$dateAdded, " +
                    "duration=$duration, artist=$artist, album=$album)"
        }

        override suspend fun fill(requestPermissionWrapper: RequestPermissionWrapper, cursor: Cursor, uri: Uri) {
            super.fill(requestPermissionWrapper, cursor, uri)
            with(cursor) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    this@AudioEntity.duration = getIntOrNull(getColumnIndexOrThrow(projectionQ[0]))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    this@AudioEntity.artist = getStringOrNull(getColumnIndexOrThrow(projectionR[0]))
                    this@AudioEntity.album = getStringOrNull(getColumnIndexOrThrow(projectionR[1]))
                }
            }
        }
    }

    class VideoEntity : MediaEntity() {
        var description: String? = null
        var width: Int? = null
        var height: Int? = null
        var duration: Int? = null
        var artist: String? = null
        var album: String? = null

        companion object {
            val projection = arrayOf(
                MediaStore.Video.VideoColumns.DESCRIPTION,
                MediaStore.Video.VideoColumns.WIDTH,
                MediaStore.Video.VideoColumns.HEIGHT
            )

            @RequiresApi(Build.VERSION_CODES.Q)
            val projectionQ = arrayOf(
                MediaStore.Video.VideoColumns.DURATION,
            )

            @RequiresApi(Build.VERSION_CODES.R)
            val projectionR = arrayOf(
                MediaStore.Video.VideoColumns.ARTIST,
                MediaStore.Video.VideoColumns.ALBUM
            )
        }

        override fun toString(): String {
            return "VideoEntity(id=$id, uri=$uri, " +
                    "size=$size, displayName=$displayName, title=$title, mimeType=$mimeType, dateAdded=$dateAdded, " +
                    "description=$description, width=$width, height=$height, duration=$duration, artist=$artist, album=$album)"
        }

        override suspend fun fill(requestPermissionWrapper: RequestPermissionWrapper, cursor: Cursor, uri: Uri) {
            super.fill(requestPermissionWrapper, cursor, uri)
            with(cursor) {
                this@VideoEntity.description = getStringOrNull(getColumnIndexOrThrow(projection[0]))
                this@VideoEntity.width = getIntOrNull(getColumnIndexOrThrow(MediaEntity.projection[1]))
                this@VideoEntity.height = getIntOrNull(getColumnIndexOrThrow(MediaEntity.projection[2]))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    this@VideoEntity.duration = getIntOrNull(getColumnIndexOrThrow(projectionQ[0]))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    this@VideoEntity.artist = getStringOrNull(getColumnIndexOrThrow(projectionR[0]))
                    this@VideoEntity.album = getStringOrNull(getColumnIndexOrThrow(projectionR[1]))
                }
            }
        }
    }

    class DownloadEntity : MediaEntity() {
        var downloadUri: String? = null

        companion object {
            @RequiresApi(Build.VERSION_CODES.Q)
            val projectionQ = arrayOf(
                MediaStore.DownloadColumns.DOWNLOAD_URI
            )
        }

        override fun toString(): String {
            return "DownloadEntity(id=$id, uri=$uri, " +
                    "size=$size, displayName=$displayName, title=$title, mimeType=$mimeType, dateAdded=$dateAdded, " +
                    "downloadUri=$downloadUri)"
        }

        override suspend fun fill(requestPermissionWrapper: RequestPermissionWrapper, cursor: Cursor, uri: Uri) {
            super.fill(requestPermissionWrapper, cursor, uri)
            with(cursor) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    this@DownloadEntity.downloadUri = getStringOrNull(getColumnIndexOrThrow(projectionQ[0]))
                }
            }
        }
    }

}