package io.cabinos.one.platform

/** A wall-clock correction may require one full interval, never hours of extra waiting. */
internal fun requestWaitMillis(last:Long,now:Long,interval:Long):Long =
    if(now<last)interval else (interval-(now-last)).coerceAtLeast(0)
