package com.snsdpen.launcher.data

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build

/**
 * 背面ライト(トーチ)。権限不要。明るさの段は端末が決める(SM-F971Q は 1..5、既定 3)。
 * 段に対応していない端末では ON/OFF だけ(maxLevel = 1)
 */
object Torch {
    private fun manager(context: Context) = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    /** 背面でフラッシュのあるカメラ */
    fun cameraId(context: Context): String? {
        val m = manager(context) ?: return null
        return runCatching {
            m.cameraIdList.firstOrNull { id ->
                val c = m.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        }.getOrNull()
    }

    fun maxLevel(context: Context): Int {
        if (Build.VERSION.SDK_INT < 33) return 1
        val m = manager(context) ?: return 1
        val id = cameraId(context) ?: return 1
        return runCatching { m.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1 }
            .getOrDefault(1).coerceAtLeast(1)
    }

    /** level 0 = 消灯、1..maxLevel = 点灯(段) */
    fun set(context: Context, level: Int) {
        val m = manager(context) ?: return
        val id = cameraId(context) ?: return
        runCatching {
            when {
                level <= 0 -> m.setTorchMode(id, false)
                Build.VERSION.SDK_INT >= 33 && maxLevel(context) > 1 -> m.turnOnTorchWithStrengthLevel(id, level.coerceIn(1, maxLevel(context)))
                else -> m.setTorchMode(id, true)
            }
        }.onFailure { android.util.Log.w("Torch", "set $level failed: $it") }
    }
}
