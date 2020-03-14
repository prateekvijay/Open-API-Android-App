package com.codingwithmitch.openapi.repository

import com.codingwithmitch.openapi.util.*
import com.codingwithmitch.openapi.util.ApiResult.*
import com.codingwithmitch.openapi.util.Constants.Companion.CACHE_ERROR_TIMEOUT
import com.codingwithmitch.openapi.util.Constants.Companion.CACHE_TIMEOUT
import com.codingwithmitch.openapi.util.Constants.Companion.NETWORK_ERROR_TIMEOUT
import com.codingwithmitch.openapi.util.Constants.Companion.NETWORK_TIMEOUT
import com.codingwithmitch.openapi.util.Constants.Companion.UNKNOWN_ERROR
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException

/**
 * Reference: https://medium.com/@douglas.iacovelli/how-to-handle-errors-with-retrofit-and-coroutines-33e7492a912
 */
private val TAG: String = "AppDebug"

suspend fun <T> safeApiCall(
    dispatcher: CoroutineDispatcher,
    apiCall: suspend () -> T?
): ApiResult<T?> {
    return withContext(dispatcher) {
        try {
            // throws TimeoutCancellationException
            withTimeout(NETWORK_TIMEOUT){
                Success(apiCall.invoke())
            }
        } catch (throwable: Throwable) {
            when (throwable) {
                is TimeoutCancellationException -> {
                    val code = 408 // timeout error code
                    GenericError(code, NETWORK_ERROR_TIMEOUT)
                }
                is IOException -> {
                    NetworkError
                }
                is HttpException -> {
                    val code = throwable.code()
                    val errorResponse = convertErrorBody(throwable)
                    GenericError(
                        code,
                        errorResponse
                    )
                }
                else -> {
                    GenericError(
                        null,
                        UNKNOWN_ERROR
                    )
                }
            }
        }
    }
}

suspend fun <T> safeCacheCall(
    dispatcher: CoroutineDispatcher,
    cacheCall: suspend () -> T?
): CacheResult<T?> {
    return withContext(dispatcher) {
        try {
            // throws TimeoutCancellationException
            withTimeout(CACHE_TIMEOUT){
                CacheResult.Success(cacheCall.invoke())
            }
        } catch (throwable: Throwable) {
            when (throwable) {
                is TimeoutCancellationException -> {
                    CacheResult.GenericError(CACHE_ERROR_TIMEOUT)
                }
                else -> {
                    CacheResult.GenericError(UNKNOWN_ERROR)
                }
            }
        }
    }
}


fun <ViewState> emitError(
    message: String,
    uiComponentType: UIComponentType,
    stateEvent: StateEvent?
): Flow<DataState<ViewState>> = flow{
    emit(
        DataState.error(
            response = Response(
                message = "${stateEvent?.errorInfo()}\n\nReason: ${message}",
                uiComponentType = uiComponentType,
                messageType = MessageType.Error()
            ),
            stateEvent = stateEvent
        )
    )
}

private fun convertErrorBody(throwable: HttpException): String? {
    return try {
        throwable.response()?.errorBody()?.toString()
    } catch (exception: Exception) {
        UNKNOWN_ERROR
    }
}
