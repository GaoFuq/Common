package com.like.common.util.ble

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.MainThread
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.like.common.util.ble.blestate.BaseBleState
import com.like.common.util.ble.blestate.InitialState
import com.like.common.util.ble.blestate.InitializedState
import com.like.common.util.ble.blestate.ReadyState
import com.like.common.util.ble.model.BleCommand
import com.like.common.util.ble.model.BleResult
import com.like.common.util.ble.model.BleStatus
import com.like.common.util.ble.scanstrategy.IScanStrategy
import kotlinx.coroutines.CoroutineScope

/**
 * 低功耗蓝牙服务，负责在后台实现蓝牙的连接，数据的发送接收
 * GATT，属性配置文件，定义数据的交互方式和含义。
 * 定义了三个概念：服务（Service）、特征（Characteristic）和描述（Descriptor）。
 *
 * GATT 包含若干个 Service ，一个 Service 包含若干个 Characteristic，一个 Characteristic 可以包含若干 Descriptor。
 *
 * Service、Characteristic 还有 Descriptor 都是使用 UUID 唯一标示的。
 * 我们说的 BLE 通信，其实就是对 Characteristic 的读写或者订阅通知。
 *
 * UUID 是全局唯一标识，它是 128bit 的值，为了便于识别和阅读，一般标示程如下的形式，8-4-4-12 的16进制标示。
 * 我们也看到，UUID 有点太长了，在低功耗蓝牙中这种数据长度非常受限的情况下，使用起来肯定不方便，
 * 所以蓝牙又使用了所谓的 16 bit 或者 32 bit 的 UUID。其实本质上并没有什么 16bit 或者 32 bit UUID，
 * 蓝牙 SIG 定义了一个基础的UUID（Bluetooth Base UUID），形式如下：除了 XXXX 那几位意外，其他都是固定，
 * 所以说，其实 16 bit UUID 是对应了一个 128 bit 的 UUID。这样一来，UUID 就大幅减少了，
 * 例如 16 bit uuid 只有有限的 65536 个，所以 16 bit UUID 并不能随便使用。SIG 已经预先定义了一些 UUID，
 * 如果你想添加一些自己的 16 bit 的 UUID，可以花钱买。
 *
 * 从 Android 4.3 Jelly Bean，也就是 API 18 才开始支持低功耗蓝牙。
 * 这时支持 BLE 的 Central 模式，也就是我们在上面 GAP 中说的，Android 设备只能作为中心设备去连接其他设备。
 * 从 Android 5.0 开始才支持外设模式。
 *
 * 应用在使用蓝牙设备的时候必须要声明蓝牙权限 BLUETOOTH 需要这个权限才可以进行蓝牙通信，例如：请求连接、接受连接、和传输数据。
 * <uses-permission android:name="android.permission.BLUETOOTH" />
 * 如果还需要发现或者操作蓝牙设置，则需要声明 BLUETOOTH_ADMIN 权限。使用这个权限的前提是要有 BLUETOOTH 权限。
 * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
 * <!-- Android6.0 蓝牙扫描需要申请定位权限，因为 BLE 确实有定位的能力-->
 * <uses-permission-sdk-23 android:name="android.permission.ACCESS_COARSE_LOCATION" />
 * <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
 *
 * BLE 应用可以分为两大类：基于非连接的和连接的。
 * 基于非连接的，这种应用就是依赖 BLE 的广播，也叫作 Beacon。这里有两个角色，发送广播的一方叫做 Broadcaster，监听广播的一方叫 Observer。
 * 基于连接的，就是通过建立 GATT 连接，收发数据。这里也有两个角色，发起连接的一方，叫做中心设备—Central，被连接的设备，叫做外设—Peripheral。一个中心设备可以连接多个外设，一个外设只能被一个中心设备连接。中心设备和外设之间的通信是双向的。其实一个中心设备能够同时连接的外围设备数量也是有限，一般最多连接 7 个外设。
 * Android 从 4.3(API Level 18) 开始支持低功耗蓝牙，但是只支持作为中心设备（Central）模式，这就意味着 Android 设备只能主动扫描和链接其他外围设备（Peripheral）。从 Android 5.0(API Level 21) 开始两种模式都支持。
 *
 * BLE 所有回调函数都不是在主线程中的
 *
 * @author like
 * @version 1.0
 * created on 2017/4/14 11:52
 */
@TargetApi(18)
@SuppressLint("MissingPermission")
class BleManager(
        private val context: Context,
        private val mCoroutineScope: CoroutineScope,
        val bleResultLiveData: MutableLiveData<BleResult>,
        private val mScanStrategy: IScanStrategy,
        private val scanTimeout: Long = 3000,// 蓝牙扫描时间的限制
        private val connectTimeout: Long = 20000// 蓝牙连接超时时间
) {
    private var mBleState: BaseBleState? = InitialState(context, bleResultLiveData)

    // 蓝牙打开关闭监听器
    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0)) {
                        BluetoothAdapter.STATE_ON -> {// 蓝牙已打开
                            bleResultLiveData.postValue(BleResult(BleStatus.ON))
                            initBle()// 初始化蓝牙
                        }
                        BluetoothAdapter.STATE_OFF -> {// 蓝牙已关闭
                            bleResultLiveData.postValue(BleResult(BleStatus.OFF))
                            mBleState?.close()
                        }
                    }
                }
            }
        }
    }

    init {
        // 注册蓝牙打开关闭监听
        context.registerReceiver(mReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    /**
     * 初始化蓝牙适配器
     */
    @MainThread
    @RequiresPermission(allOf = [android.Manifest.permission.BLUETOOTH_ADMIN, android.Manifest.permission.BLUETOOTH, android.Manifest.permission.ACCESS_FINE_LOCATION])
    fun initBle() {
        mBleState?.init()
        mBleState?.getBluetoothAdapter()?.apply {
            mBleState = InitializedState(mCoroutineScope, bleResultLiveData, this, mScanStrategy, scanTimeout)
        }

        if (context is LifecycleOwner) {
            bleResultLiveData.observe(context, Observer {
                when (it?.status) {
                    BleStatus.CONNECTED -> {
                        mBleState?.getBluetoothAdapter()?.apply {
                            mBleState = ReadyState(context, mCoroutineScope, bleResultLiveData, this, connectTimeout)
                        }
                    }
                    BleStatus.DISCONNECTED -> {
                        mBleState?.getBluetoothAdapter()?.apply {
                            mBleState = InitializedState(mCoroutineScope, bleResultLiveData, this, mScanStrategy, scanTimeout)
                            scanBleDevice()
                        }
                    }
                    else -> {
                    }
                }
            })
        }
    }

    /**
     * 扫描蓝牙设备
     */
    fun scanBleDevice() {
        if (mBleState is InitializedState) {
            mBleState?.startScan()
            mBleState?.getBluetoothAdapter()?.apply {
                mBleState = ReadyState(context, mCoroutineScope, bleResultLiveData, this, connectTimeout)
            }
        } else if (mBleState is ReadyState) {
            mBleState?.disconnectAll()
            mBleState?.getBluetoothAdapter()?.apply {
                mBleState = InitializedState(mCoroutineScope, bleResultLiveData, this, mScanStrategy, scanTimeout)
                scanBleDevice()
            }
        }
    }

    /**
     * 停止扫描蓝牙设备
     */
    fun stopScanBleDevice() {
        mBleState?.stopScan()
    }

    /**
     * 根据蓝牙地址，连接蓝牙设备
     */
    fun connect(address: String) {
        mBleState?.connect(address)
    }

    fun write(command: BleCommand) {
        mBleState?.write(command)
    }

    /**
     * 取消连接
     */
    fun disconnect(address: String) {
        mBleState?.disconnect(address)
    }

    /**
     * 关闭所有蓝牙连接
     */
    fun close() {
        mBleState?.close()
        try {
            context.unregisterReceiver(mReceiver)
        } catch (e: Exception) {// 避免 java.lang.IllegalArgumentException: Receiver not registered
            e.printStackTrace()
        }
    }

    /**
     * 弹出开启蓝牙的对话框
     */
    fun openBTDialog(activity: Activity, requestCode: Int) {
        activity.startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), requestCode)
    }

}
