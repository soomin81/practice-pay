import { Store } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { StatusScreen } from '@/checkout/components/StatusScreen'
import type { DevReturnKind } from './devReturn'

/**
 * **가맹점 사이트를 대신하는 DEV 전용 화면이다.**
 *
 * 결제가 끝나면 고객은 체크아웃을 떠나 `successUrl`/`cancelUrl`(가맹점이 결제를 만들 때
 * 지정한 자기 사이트 주소)로 이동한다. 로컬에는 그 사이트가 없어서 예전에는
 * `merchant.example.com`을 넣어 뒀는데, 그러면 **마지막 단계가 죽은 도메인으로 끝나** 흐름을
 * 끝까지 확인할 수 없었다. 그 자리를 이 화면이 대신한다.
 *
 * **이건 결제 화면이 아니라 "가맹점 사이트인 척하는 자리"다.** 그 사실이 화면에 드러나야
 * 개발자가 둘을 혼동하지 않는다 — 그래서 상단에 DEV 표식을 붙인다.
 *
 * 운영 번들에는 들어가지 않는다(`DevPaymentCreator`와 같은 규율): 호출부가
 * `import.meta.env.DEV`로 감싸고 이 컴포넌트도 스스로 확인한다.
 */
export function DevMerchantReturn({ kind, onRestart }: { kind: DevReturnKind; onRestart: () => void }) {
	if (!import.meta.env.DEV) return null

	const succeeded = kind === 'success'

	return (
		<div className="flex flex-col gap-3">
			<aside className="flex items-center gap-1.5 rounded-lg border border-dashed bg-background/60 p-3 text-xs">
				<Store className="size-3.5 shrink-0" aria-hidden />
				<span className="text-muted-foreground">
					여기는 <strong>가맹점 사이트</strong>를 대신하는 DEV 화면입니다. 실제로는 가맹점이
					결제를 만들 때 지정한 주소로 이동합니다.
				</span>
			</aside>

			<StatusScreen
				tone={succeeded ? 'success' : 'cancelled'}
				title={succeeded ? '주문이 완료되었습니다' : '주문을 취소했습니다'}
				description={
					succeeded ? (
						<>
							결제가 정상적으로 끝나 가맹점 사이트로 돌아왔습니다. 실제 가맹점이라면 이
							자리에서 주문 내역을 보여줍니다.
						</>
					) : (
						<>결제를 취소하고 가맹점 사이트로 돌아왔습니다. 장바구니는 그대로 남아 있습니다.</>
					)
				}
			>
				<Button variant="outline" onClick={onRestart}>
					새 테스트 결제 시작하기
				</Button>
			</StatusScreen>

			{/*
			  가맹점은 이 화면의 표시를 근거로 주문을 확정하면 안 된다. 고객이 successUrl에
			  도착하는 시점과 Webhook 도착 시점의 선후는 보장되지 않는다(계약 8절).
			*/}
			<p className="text-center text-xs text-muted-foreground">
				실제 가맹점은 이 화면이 아니라 <strong>Webhook</strong>으로 주문을 확정해야 합니다 —
				고객의 복귀와 Webhook의 순서는 보장되지 않습니다.
			</p>
		</div>
	)
}
