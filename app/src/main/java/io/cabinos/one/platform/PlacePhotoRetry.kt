package io.cabinos.one.platform

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Bounded retries exceed the negative-cache TTL; cancellation stops requests on leaving the screen. */
internal suspend fun <T:Any> retryPlacePhoto(load:suspend ()->T?,loading:(Boolean)->Unit,waiting:(Long)->Unit={}):T? {
    for(wait in listOf(0L,61_000L,180_000L)) {
        waiting(wait)
        delay(wait)
        waiting(0)
        loading(true)
        val result=try {load()} catch(error:Exception) {
            if(error is CancellationException)throw error
            null
        } finally {loading(false)}
        if(result!=null)return result
    }
    return null
}
