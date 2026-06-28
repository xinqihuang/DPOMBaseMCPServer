#!/usr/bin/env python3
"""最小 AK/SK 签名自检：对华为云 IAM 发一次 SDK-HMAC-SHA256 签名请求。

只验证一件事——这对 AK/SK 是否匹配（HMAC 签名能否通过）。与 region / 权限无关：
  - 200            -> AK/SK 配对正确（凭证有效）
  - 403            -> 配对正确，只是没有该接口权限（凭证仍然有效）
  - 401 + verify ak sk signature failed -> AK/SK 不匹配（凭证无效）

用法：
  set -a; source .env.local; set +a
  python3 scripts/smoke/check-aksk.py
或直接传参：
  python3 scripts/smoke/check-aksk.py <AK> <SK>
"""

import datetime
import hashlib
import hmac
import os
import sys
import urllib.error
import urllib.request

HOST = "iam.myhuaweicloud.com"        # 全局 IAM，AK/SK 签名与 region 无关
METHOD = "GET"
URI = "/v3/auth/projects"             # 列出当前身份可访问的 project，AK/SK 可直接调
QUERY = ""
BODY = b""


def main():
    if len(sys.argv) == 3:
        ak, sk = sys.argv[1], sys.argv[2]
    else:
        ak = os.environ.get("HUAWEICLOUD_AK", "")
        sk = os.environ.get("HUAWEICLOUD_SK", "")
    if not ak or not sk:
        sys.exit("缺少 AK/SK：先 `source .env.local` 或传参 check-aksk.py <AK> <SK>")

    print(f"AK 长度={len(ak)}  SK 长度={len(sk)}  AK ={ak}   SK ={sk}")

    t = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    headers = {"host": HOST, "x-sdk-date": t}
    signed_headers = ";".join(sorted(headers))
    canonical_headers = "".join(f"{k}:{headers[k]}\n" for k in sorted(headers))
    hashed_body = hashlib.sha256(BODY).hexdigest()
    canonical_uri = URI if URI.endswith("/") else URI + "/"

    canonical_request = "\n".join(
        [METHOD, canonical_uri, QUERY, canonical_headers, signed_headers, hashed_body]
    )
    string_to_sign = (
        "SDK-HMAC-SHA256\n" + t + "\n"
        + hashlib.sha256(canonical_request.encode("utf-8")).hexdigest()
    )
    signature = hmac.new(
        sk.encode("utf-8"), string_to_sign.encode("utf-8"), hashlib.sha256
    ).hexdigest()
    authorization = (
        f"SDK-HMAC-SHA256 Access={ak}, "
        f"SignedHeaders={signed_headers}, Signature={signature}"
    )

    req = urllib.request.Request(f"https://{HOST}{URI}", method=METHOD)
    req.add_header("X-Sdk-Date", t)
    req.add_header("Authorization", authorization)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            print(f"HTTP {resp.status}  -> AK/SK 配对正确，凭证有效 ✅")
            print(resp.read(600).decode("utf-8", "replace"))
    except urllib.error.HTTPError as e:
        body = e.read(600).decode("utf-8", "replace")
        print(f"HTTP {e.code}")
        print(body)
        if e.code == 403:
            print("\n=> 签名通过、仅缺该接口权限：AK/SK 配对是对的 ✅")
        elif "verify ak sk signature" in body or e.code == 401:
            print("\n=> 签名校验失败：AK 与 SK 不匹配（或为临时密钥）❌")
    except urllib.error.URLError as e:
        sys.exit(f"网络不通，无法连到 {HOST}：{e}")


if __name__ == "__main__":
    main()
