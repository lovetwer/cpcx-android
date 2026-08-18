package com.example.lottery.util

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.BufferedInputStream

/** 把选择的图片 Uri 读成 MultipartBody.Part（字段名 image，对齐后端 /api/lottery/recognize） */
object ImageUtil {
    fun toMultipart(context: Context, uri: Uri, name: String = "ticket.jpg"): MultipartBody.Part? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = BufferedInputStream(input).use { it.readBytes() }
            val reqBody = RequestBody.create("image/*".toMediaType(), bytes)
            MultipartBody.Part.createFormData("image", name, reqBody)
        } catch (e: Exception) {
            null
        }
    }
}
