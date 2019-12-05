package com.like.common.util.ble.blestate

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import com.like.common.util.ble.model.BleCommand
import com.like.common.util.ble.model.BleConnectCommand
import com.like.common.util.ble.model.BleWriteCommand
import com.like.common.util.ble.scanstrategy.IScanStrategy

/**
 * 蓝牙状态
 */
abstract class BaseBleState {
    /**
     * 初始化蓝牙
     */
    open fun init() {}

    /**
     * 开始扫描设备
     */
    open fun startScan(scanStrategy: IScanStrategy, scanTimeout: Long) {}

    /**
     * 停止扫描设备
     */
    open fun stopScan() {}

    /**
     *  连接指定蓝牙设备
     */
    open fun connect(command: BleConnectCommand) {}

    /**
     * 写数据
     */
    open fun write(command: BleWriteCommand) {}

    /**
     * 断开指定蓝牙设备
     */
    open fun disconnect(address: String) {}

    /**
     * 断开所有蓝牙设备
     */
    open fun disconnectAll() {}

    /**
     * 释放资源
     */
    open fun close() {}

    /**
     * 设置mtu
     */
    open fun setMtu(address: String, mtu: Int) {}

    /**
     * 获取 BluetoothAdapter
     */
    open fun getBluetoothAdapter(): BluetoothAdapter? {
        return null
    }

    open fun getBluetoothManager(): BluetoothManager? {
        return null
    }
}