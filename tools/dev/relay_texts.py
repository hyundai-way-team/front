#!/usr/bin/env python3
"""
시연용: 에뮬레이터 앱이 '보낼' 동승자 문자를 이 맥의 메시지 앱으로 실제 휴대폰에 보낸다.

에뮬레이터엔 블루투스로 연결된 휴대폰이 없어 Pleos 문자가 나가지 않는다. 앱은 보낼 문자를 로그(WayText)에 남기므로
(WayConfig.TEXTS_VIA_PLEOS = false 면 보내기 화면 없이 로그만), 그 로그를 읽어 같은 문구를 메시지 앱으로 보낸다.
서버의 WAY_DEV_SMS_TO(로컬 서버 시연)와 같은 일을 운영 서버 시연에서 한다.

  WAY_DEV_SMS_TO=<받을 번호> python3 tools/dev/relay_texts.py        (iMessage)
  WAY_DEV_SMS_TO=<받을 번호> WAY_DEV_SMS_VIA=sms python3 tools/dev/relay_texts.py   (아이폰 '문자 메시지 전달')

번호는 실행할 때 환경 변수로만 받는다(파일·저장소에 남기지 않음). 시작할 때 에뮬 로그를 비우고 그 뒤 문자만 보낸다.
"""

import os
import re
import subprocess
import sys

SCRIPT = """
on run argv
  set theText to item 1 of argv
  set theNumber to item 2 of argv
  set wanted to item 3 of argv
  tell application "Messages"
    set chosen to missing value
    repeat with a in accounts
      try
        if ((service type of a) as text) is wanted and enabled of a then
          set chosen to a
          exit repeat
        end if
      end try
    end repeat
    if chosen is missing value then error wanted & " 계정이 없어요 — 메시지 앱 설정을 확인해 주세요"
    send theText to participant theNumber of chosen
  end tell
end run
"""

HEADER = re.compile(r"^\[ .* ([VDIWEF])/WayText\s*\]$")


def masked(number: str) -> str:
    digits = re.sub(r"\D", "", number)
    return f"***-****-{digits[-4:]}" if len(digits) >= 4 else "****"


def send(text: str, number: str, service: str) -> None:
    result = subprocess.run(["osascript", "-", text, number, service], input=SCRIPT, text=True, capture_output=True, timeout=30)
    if result.returncode == 0:
        print(f"[보냄] {service} → {masked(number)} · {text.splitlines()[0][:40]}", flush=True)
    else:
        print(f"[실패] {service} → {masked(number)}: {result.stderr.strip()[:200]}", flush=True)


def main() -> None:
    number = os.environ.get("WAY_DEV_SMS_TO", "").strip()
    if not number:
        sys.exit("WAY_DEV_SMS_TO 에 받을 번호를 넣어 주세요")
    service = "SMS" if os.environ.get("WAY_DEV_SMS_VIA", "").lower() == "sms" else "iMessage"
    subprocess.run(["adb", "logcat", "-c"], check=False)
    print(f"[대기] 앱 문자 로그(WayText) → {service} {masked(number)}", flush=True)
    proc = subprocess.Popen(["adb", "logcat", "-v", "long", "-s", "WayText:I"], stdout=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
    entry = None
    for raw in proc.stdout:
        line = raw.rstrip("\n")
        if HEADER.match(line):
            entry = []
            continue
        if entry is None:
            continue
        if line.strip() == "":
            # 첫 줄 '문자 → 이름(가린 번호)' 뒤가 문자 내용
            if entry and entry[0].startswith("문자 →") and len(entry) > 1:
                send("\n".join(entry[1:]).strip(), number, service)
            entry = None
            continue
        entry.append(line)


if __name__ == "__main__":
    main()
