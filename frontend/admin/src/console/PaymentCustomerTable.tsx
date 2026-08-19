import { Link } from 'react-router-dom'
import type { PaymentCustomerMatch } from '@/api/types'
import { DataTable, Td, Th } from '@/components/console/DataTable'
import { StatusBadge } from '@/components/console/StatusBadge'
import { formatDateTime, formatKrw } from '@/console/format'
import { RevealCustomerAction } from '@/console/RevealCustomerAction'

/**
 * 구매자 검색 결과 표. **가려진 값만 그린다** — 원문은 열람 액션을 통해서만 화면에 나타나고,
 * 그때도 그 행 안에서만 잠깐 보인다(`RevealCustomerAction`).
 *
 * 열람 열은 `canReveal`이 아니면 **아예 그리지 않는다.** 서버도 403으로 막지만, 누를 수 있게
 * 두고 거부하는 것보다 감추는 편이 낫다(이 콘솔의 다른 액션과 같은 판단).
 */
export function PaymentCustomerTable({
	matches,
	canReveal,
}: {
	matches: readonly PaymentCustomerMatch[]
	canReveal: boolean
}) {
	return (
		<DataTable
			head={
				<>
					<Th>결제</Th>
					<Th>가맹점</Th>
					<Th>주문</Th>
					<Th>금액</Th>
					<Th>상태</Th>
					<Th>구매자(가림)</Th>
					{canReveal && <Th>원본</Th>}
					<Th>생성</Th>
				</>
			}
		>
			{matches.map((match) => (
				<tr key={match.paymentId} className="hover:bg-muted/40">
					<Td variant="mono" className="text-xs">
						<Link to={`/payments/${match.paymentId}`} className="hover:underline">
							{match.paymentId}
						</Link>
					</Td>
					<Td>{match.merchantName}</Td>
					<Td>
						<span className="block">{match.orderName}</span>
						<span className="block text-xs text-muted-foreground">{match.merchantOrderId}</span>
					</Td>
					<Td className="tabular-nums">{formatKrw(match.orderAmount)}</Td>
					<Td>
						<StatusBadge kind="payment" status={String(match.status)} />
					</Td>
					<Td>
						<span className="block">{match.nameMasked}</span>
						<span className="block text-xs text-muted-foreground">{match.emailMasked}</span>
						<span className="block text-xs text-muted-foreground">{match.phoneMasked}</span>
					</Td>
					{canReveal && (
						<Td>
							<RevealCustomerAction paymentId={match.paymentId} />
						</Td>
					)}
					<Td className="text-xs text-muted-foreground">{formatDateTime(match.createdAt)}</Td>
				</tr>
			))}
		</DataTable>
	)
}
