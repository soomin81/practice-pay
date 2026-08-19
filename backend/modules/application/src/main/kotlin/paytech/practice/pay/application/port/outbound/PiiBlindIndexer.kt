package paytech.practice.pay.application.port.outbound

/**
 * 암호화된 개인정보를 **정확 일치로 찾기 위한** Blind Index를 만드는 Outbound Port다.
 *
 * [PiiEncryptor]가 값마다 랜덤 IV를 써서 같은 이메일도 행마다 암호문이 달라지는데(그게
 * 목적이다), 그러면 암호문으로는 검색할 수 없다. 그래서 `HMAC(pepper, 정규화된 값)`을 별도
 * 컬럼에 두고 그걸로 찾는다(ADR-008).
 *
 * **정규화된 값을 넘겨야 한다** — `CustomerEmail.normalized`/`CustomerPhone.normalized`가 그
 * 값이다. 원문을 그대로 넘기면 `A@b.com`과 `a@b.com`이 다른 인덱스를 가져 같은 사람이
 * 검색에 걸리지 않는다.
 *
 * **대가**: 같은 값이 같은 인덱스를 가지므로 **DB가 유출되면 동일인 여부가 드러난다.**
 * 암호문의 랜덤 IV로 얻은 성질을 이 컬럼이 일부 되돌린다 — 검색이 실제로 필요해서 감수한다.
 * 부분 검색(도메인만, 앞글자)은 되지 않는다.
 */
fun interface PiiBlindIndexer {
	/** 정규화된 평문의 Blind Index를 돌려준다. 같은 입력이면 언제나 같은 결과다. */
	fun index(normalizedValue: String): String
}
