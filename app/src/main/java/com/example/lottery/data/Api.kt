package com.example.lottery.data

import com.example.lottery.LotteryApp
import com.example.lottery.data.model.*
import com.google.gson.Gson
import okhttp3.MultipartBody
import retrofit2.HttpException

/**
 * 接口统一封装：自动带鉴权头在 LotteryApp 的拦截器里完成。
 * 这里统一处理异常与 401 跳登录，业务层只需 try/catch ApiException。
 */
object Api {
    /** 401 未授权时回调（由 MainActivity 设置，跳回登录页） */
    var unauthorizedHandler: (() -> Unit)? = null

    private val svc: ApiService
        get() = LotteryApp.instance.api

    private val gson = Gson()

    private suspend fun <T> run(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: HttpException) {
            if (e.code() == 401) {
                LotteryApp.instance.authStore.clear()
                unauthorizedHandler?.invoke()
            }
            val body = e.response()?.errorBody()?.string()
            val msg = body?.let {
                try { gson.fromJson(it, BaseResp::class.java).msg } catch (_: Exception) { null }
            }
            throw ApiException(msg ?: "请求失败（${e.code()}）")
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(e.message ?: "网络错误")
        }
    }

    /* ---------------- 用户 ---------------- */

    suspend fun register(username: String, password: String, email: String, deviceId: String): LoginResp =
        run { svc.register(mapOf("username" to username, "password" to password, "email" to email, "device_id" to deviceId)) }

    suspend fun login(username: String, password: String): LoginResp =
        run { svc.login(mapOf("username" to username, "password" to password)) }

    suspend fun deviceLogin(deviceId: String, email: String): LoginResp =
        run { svc.deviceLogin(mapOf("device_id" to deviceId, "email" to email)) }

    suspend fun me(): MeResp = run { svc.me() }

    suspend fun updateMe(payload: Map<String, String>): MeResp = run { svc.updateMe(payload) }

    /* ---------------- 彩票 ---------------- */

    suspend fun listLottery(params: Map<String, String>): List<Lottery> =
        run { svc.listLottery(params).list ?: emptyList() }

    suspend fun createLottery(body: Map<String, Any>): BaseResp = run { svc.createLottery(body) }

    suspend fun batchLottery(items: List<Map<String, String>>): BatchResp =
        run { svc.batchLottery(mapOf("items" to items)) }

    suspend fun deleteLottery(id: Long): BaseResp = run { svc.deleteLottery(id) }

    suspend fun updateStatus(id: Long, status: String): BaseResp =
        run { svc.updateStatus(id, mapOf("status" to status)) }

    /* ---------------- 开奖 ---------------- */

    suspend fun listDraw(type: String): List<DrawResult> =
        run { svc.listDraw(type).list ?: emptyList() }

    /* ---------------- 图片识别 ---------------- */

    suspend fun recognize(image: MultipartBody.Part, dryRun: Boolean): RecognizeResp =
        run {
            svc.recognize(
                image,
                MultipartBody.Part.createFormData("dry_run", if (dryRun) "1" else "0")
            )
        }

    /* ---------------- 分享 ---------------- */

    suspend fun createShare(ids: List<Long>): ShareResp =
        run { svc.createShare(mapOf("ids" to ids)) }

    suspend fun getShare(code: String): List<Lottery> =
        run { svc.getShare(code).list ?: emptyList() }

    /* ---------------- AI 预测 ---------------- */

    /** 调用后端 AI 预测接口（Key 存服务器环境变量，客户端不暴露） */
    suspend fun aiPredict(type: String): AIPredictResp = run {
        val resp = svc.aiPredict(mapOf("type" to type))
        if (resp.ok != true) {
            throw ApiException(resp.msg ?: "AI 预测失败")
        }
        resp
    }
}
