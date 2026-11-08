package com.kscho.way.pickup

import android.content.Intent
import android.net.Uri

/**
 * 동승자에게 전화: 차량 전화 화면(ACTION_DIAL)을 번호가 채워진 채로 띄운다.
 * 직접 걸기(ACTION_CALL)는 CALL_PHONE 권한이 필요하고 차량에선 전화 기능이 제한될 수 있어 쓰지 않는다.
 */
object PassengerCall {
  fun dialIntent(phone: String): Intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))
}
