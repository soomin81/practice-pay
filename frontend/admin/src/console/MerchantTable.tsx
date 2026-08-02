import { Link } from 'react-router-dom'
import { formatDateTime } from '@/console/format'
import type { MerchantSummary } from '@/api/types'
import { DataTable, EmptyRow, Td, Th } from '@/components/console/DataTable'
import { StatusBadge } from '@/components/console/StatusBadge'

/**
 * 가맹점 목록. 인증된 내부 운영자 전원이 볼 수 있다(VIEWER 포함).
 *
 * 가맹점 이름은 상세 화면(`/merchants/{id}`)으로 가는 링크다 — 거기서 그 가맹점의 사용자
 * 명부와 계정 관리를 한다.
 */
export function MerchantTable({ merchants }: { merchants: readonly MerchantSummary[] }) {
	return (
		<DataTable
			head={
				<>
					<Th>가맹점 코드</Th>
					<Th>이름</Th>
					<Th>상태</Th>
					<Th>등록</Th>
				</>
			}
		>
			{merchants.length === 0 ? (
				<EmptyRow colSpan={4}>아직 등록된 가맹점이 없습니다.</EmptyRow>
			) : (
				merchants.map((merchant) => (
					<tr key={merchant.merchantId} className="hover:bg-muted/40">
						<Td variant="mono" className="text-foreground">
							{merchant.merchantCode}
						</Td>
						<Td>
							<Link to={`/merchants/${merchant.merchantId}`} className="font-medium underline-offset-2 hover:underline">
								{merchant.merchantName}
							</Link>
						</Td>
						<Td>
							<StatusBadge kind="merchant" status={String(merchant.status)} />
						</Td>
						<Td variant="mono" className="text-xs">
							{formatDateTime(merchant.createdAt)}
						</Td>
					</tr>
				))
			)}
		</DataTable>
	)
}
