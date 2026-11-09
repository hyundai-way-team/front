package com.kscho.way

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** D-01 홈 */
@Serializable data object Home : NavKey

/** D-02 동승자 선택 */
@Serializable data object ChoosePassenger : NavKey

/** D-03 약속 장소 검색 */
@Serializable data object SearchPlace : NavKey

/** D-04 핀을 정차 지점에 맞추기 */
@Serializable data object AdjustPin : NavKey

/** D-05 확인 · 출발 */
@Serializable data object ConfirmDeparture : NavKey

/** D-06 ~ D-10 주행 · 대기 · 탑승 */
@Serializable data object Drive : NavKey
