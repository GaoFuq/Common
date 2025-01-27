package com.like.common.sample.storage

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.like.common.sample.R
import com.like.common.sample.databinding.ActivityStorageBinding
import com.like.common.util.*
import com.like.common.util.storage.external.MediaStoreUtils
import com.like.common.util.storage.external.SafUtils
import kotlinx.coroutines.launch

class StorageActivity : AppCompatActivity() {
    private val mBinding by lazy {
        DataBindingUtil.setContentView<ActivityStorageBinding>(this, R.layout.activity_storage)
    }

    private val requestPermissionWrapper = RequestPermissionWrapper(this)
    private val startActivityForResultWrapper = StartActivityForResultWrapper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding
    }

    fun openDocument(view: View) {
        lifecycleScope.launch {
            Logger.d("openDocument：${SafUtils.openDocument(startActivityForResultWrapper)}")
        }
    }

    fun openDocumentTree(view: View) {
        lifecycleScope.launch {
            val documentFile = SafUtils.openDocumentTree(startActivityForResultWrapper)
            documentFile?.listFiles()?.forEach {
                Logger.d("openDocumentTree：${it?.uri}")
            }
        }
    }

    private var documentUri: Uri? = null
    fun createDocument(view: View) {
        lifecycleScope.launch {
            documentUri = SafUtils.createDocument(startActivityForResultWrapper, "123.jpg", SafUtils.MimeType._jpg)
            Logger.d("createDocument：$documentUri")
        }
    }

    fun deleteDocument(view: View) {
        lifecycleScope.launch {
            Logger.d(
                "deleteDocument：${SafUtils.deleteDocument(this@StorageActivity, documentUri)}"
            )
        }
    }

    fun selectFile(view: View) {
        lifecycleScope.launch {
            Logger.e(SafUtils.selectFile(startActivityForResultWrapper, SafUtils.MimeType._jpg))
        }
    }

    fun takePhoto(view: View) {
        lifecycleScope.launch {
            MediaStoreUtils.takePhoto(
                requestPermissionWrapper,
                startActivityForResultWrapper,
                false
            )?.let {
                mBinding.iv.setImageBitmap(it)
            }
        }
    }

    fun getFiles(view: View) {
        lifecycleScope.launch {
            if (requestPermissionWrapper.requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                Logger.printCollection(MediaStoreUtils.getFiles(this@StorageActivity))
            }
        }
    }

    fun getImages(view: View) {
        lifecycleScope.launch {
            if (requestPermissionWrapper.requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                val images = MediaStoreUtils.getImages(this@StorageActivity)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&// android 10 及其以上
                    !Environment.isExternalStorageLegacy() &&// 开启了分区存储
                    requestPermissionWrapper.requestPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)
                ) {
                    images.forEach {
                        UriUtils.getLatLongFromUri(this@StorageActivity, it.uri)
                    }
                }
                Logger.printCollection(images)
            }
        }
    }

    fun getAudios(view: View) {
        lifecycleScope.launch {
            Logger.printCollection(MediaStoreUtils.getAudios(this@StorageActivity))
        }
    }

    fun getVideos(view: View) {
        lifecycleScope.launch {
            Logger.printCollection(MediaStoreUtils.getVideos(this@StorageActivity))
        }
    }

    private var createdFileUri: Uri? = null
    fun createFile(view: View) {
        lifecycleScope.launch {
            createdFileUri = MediaStoreUtils.createFile(
                requestPermissionWrapper,
                uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                displayName = "6.jpg",
                relativePath = "${Environment.DIRECTORY_PICTURES}/like"
            ) {
                it?.write(context.assets.open("image_2.jpg").readBytes())
            }
            Logger.d(createdFileUri)
        }
    }

    fun updateFile(view: View) {
        val uri = createdFileUri ?: return
        lifecycleScope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Logger.d(
                    MediaStoreUtils.updateFile(
                        requestPermissionWrapper,
                        uri,
                        displayName = "22",
                        relativePath = "${Environment.DIRECTORY_PICTURES}/like1"
                    )
                )
            }
        }
    }

    fun deleteFile(view: View) {
        val uri = createdFileUri ?: return
        lifecycleScope.launch {
            Logger.d(MediaStoreUtils.deleteFile(requestPermissionWrapper, uri))
        }
    }
}
