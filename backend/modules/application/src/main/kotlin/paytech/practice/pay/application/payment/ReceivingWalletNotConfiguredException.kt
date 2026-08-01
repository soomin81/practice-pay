package paytech.practice.pay.application.payment

import paytech.practice.pay.domain.shared.BlockchainNetwork

/**
 * 요청한 네트워크에 대한 PG 수취 지갑이 [ReceivingWalletRegistry]에 없을 때 던진다.
 *
 * **가맹점의 잘못이 아니라 PG 배포 설정의 문제로 취급한다**(inbound Adapter가 503으로
 * 옮긴다) — 원인이 "이 네트워크를 아직 지원하지 않는다"이든 "지갑 설정을 빠뜨렸다"이든
 * 결제를 받을 수 없는 쪽은 PG다. 400으로 돌려주면 가맹점이 자기 요청을 고치려 들게
 * 되는데 고칠 수 있는 것이 없다.
 */
class ReceivingWalletNotConfiguredException(
	val network: BlockchainNetwork,
) : RuntimeException("수취 지갑이 설정되지 않은 네트워크입니다: ${network.code}")
