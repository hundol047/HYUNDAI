package io.cabinos.one.ui

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ArrivalClockTest {
    @Test fun earlierClockTimeSelectsTomorrow() {
        val zone=ZoneId.of("Asia/Seoul")
        val now=ZonedDateTime.of(2026,10,24,23,50,0,0,zone).toInstant().toEpochMilli()
        val result=Instant.ofEpochMilli(nextArrivalAt(now,0,10,zone)).atZone(zone)
        assertEquals(25,result.dayOfMonth);assertEquals(0,result.hour);assertEquals(10,result.minute)
    }
    @Test fun targetClockKeepsExactMinuteWithoutCurrentSeconds() {
        val now=Instant.parse("2026-10-24T10:00:43Z").toEpochMilli()
        assertEquals(Instant.parse("2026-10-24T10:30:00Z").toEpochMilli(),nextArrivalAt(now,10,30,ZoneOffset.UTC))
    }
}
