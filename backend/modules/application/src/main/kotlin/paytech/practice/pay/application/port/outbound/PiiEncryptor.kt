package paytech.practice.pay.application.port.outbound

/**
 * 개인정보를 **되돌릴 수 있게** 암호화하는 Outbound Port다.
 *
 * 이 저장소의 다른 비밀 처리(`ApiKeySecretHasher`, `InvitationTokenHasher`, `PasswordEncoder`)는
 * 전부 **단방향**이다 — 맞는지만 확인하면 되기 때문이다. 구매자 정보는 **다시 읽어야 하므로**
 * 성격이 다르고, 그래서 별도 Port다(ADR-008).
 *
 * 구현은 `modules:infra-support`의 `AesGcmPiiEncryptor`다.
 *
 * **호출 위치를 좁게 유지한다.** [decrypt]를 부르는 곳이 늘어날수록 원문이 로그·응답·파일로
 * 새어 나갈 자리가 늘어난다 — 목록과 상세는 마스킹된 값을 따로 저장해 두고 읽으므로 이
 * Port를 아예 타지 않는다.
 */
interface PiiEncryptor {
	/**
	 * 평문을 암호화한다. **같은 입력이라도 매번 다른 결과**가 나온다(값마다 새 랜덤 IV).
	 *
	 * 그래서 결과를 동등 비교하거나 검색 조건으로 쓸 수 없다 — 검색은 [PiiBlindIndexer]가 맡는다.
	 */
	fun encrypt(plaintext: String): String

	/**
	 * 암호문을 되돌린다.
	 *
	 * 암호문이 변조됐거나 키가 다르면 **예외로 실패한다** — 인증 암호화(AES-GCM)라 조용히
	 * 다른 평문을 내놓지 않는다.
	 */
	fun decrypt(ciphertext: String): String
}
