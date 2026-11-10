package com.example.lottery.data

import com.example.lottery.data.model.BaseResp
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

class ApiServiceTest {
    interface BrokenApi {
        @POST("/api/lottery")
        suspend fun createLottery(@Body body: Map<String, Any>): BaseResp
    }

    private fun retrofit(client: OkHttpClient = OkHttpClient()): Retrofit = Retrofit.Builder()
        .baseUrl("http://localhost/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .validateEagerly(true)
        .build()

    @Test fun originalMapSignatureReproducesScreenshotError() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            retrofit().create(BrokenApi::class.java)
        }
        assertTrue(error.message.orEmpty().contains("type variable or wildcard"))
    }

    @Test fun everyProductionEndpointPassesRetrofitValidation() {
        retrofit().create(ApiService::class.java)
    }

    @Test fun createLotterySendsExactFieldsAndNumericMultiplier() = runBlocking {
        var capturedPath = ""
        var capturedBody = ""
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            capturedPath = request.url.encodedPath
            val buffer = Buffer()
            request.body!!.writeTo(buffer)
            capturedBody = buffer.readUtf8()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{\"ok\":true}".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val request = ApiService.CreateLotteryReq(
            "ssq", "2026-11-10", "03,09,15,21,28,33", "07", "single", 2, "", ""
        )
        val response = retrofit(client).create(ApiService::class.java).createLottery(request)
        assertEquals(true, response.ok)
        assertEquals("/api/lottery", capturedPath)
        val body = JsonParser.parseString(capturedBody).asJsonObject
        assertEquals(setOf("type", "issue", "red_balls", "blue_balls", "play_type", "multiple", "banker_red", "banker_blue"), body.keySet())
        assertEquals("ssq", body["type"].asString)
        assertEquals("2026-11-10", body["issue"].asString)
        assertEquals("03,09,15,21,28,33", body["red_balls"].asString)
        assertEquals("07", body["blue_balls"].asString)
        assertEquals("single", body["play_type"].asString)
        assertTrue(body["multiple"].asJsonPrimitive.isNumber)
        assertEquals(2, body["multiple"].asInt)
        assertEquals("", body["banker_red"].asString)
        assertEquals("", body["banker_blue"].asString)
    }
}
