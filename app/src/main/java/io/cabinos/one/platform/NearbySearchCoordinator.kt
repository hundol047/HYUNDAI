package io.cabinos.one.platform

import io.cabinos.core.NearbyActivity
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Supplement only missing categories; a restaurant result must not hide a failed cafe search. */
internal object NearbySearchCoordinator {
    val categories=listOf("카페","식당","산책","쇼핑")
    suspend fun search(primary:suspend (Int)->List<NearbyActivity>,fallback:suspend (Set<String>)->List<NearbyActivity>):NearbySearchResult {
        val places=mutableListOf<NearbyActivity>()
        val missing=linkedSetOf<String>()
        for((index,category) in categories.withIndex()) {
            currentCoroutineContext().ensureActive()
            val found=try {primary(index).filter {it.category==category}}catch(_:IOException){emptyList()}
            places+=found
            if(found.isEmpty())missing+=category
        }
        var complete=true
        if(missing.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            try {places+=fallback(missing).filter {it.category in missing}}
            catch(_:IOException){complete=false}
        }
        val merged=places.distinctBy {it.id}.groupBy {it.category}.values.flatMap {it.sortedBy {p->p.oneWaySeconds}.take(15)}.sortedBy {it.oneWaySeconds}
        if(merged.isEmpty()&&!complete)throw IOException("주변 검색을 완료하지 못했습니다. 기존 결과를 유지합니다.")
        val notice="OpenStreetMap 등록 장소 ${merged.size}곳 · 반경 2km · "+
            if(complete)"영업 여부 확인 필요" else "${missing.joinToString("·")} 보완 검색 실패 · 다른 분류 결과 유지, 잠시 후 재시도"
        return NearbySearchResult(merged,notice,complete)
    }
}
