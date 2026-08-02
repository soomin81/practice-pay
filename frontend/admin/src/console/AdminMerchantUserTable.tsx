import { formatDateTime } from '@/console/format'
import { AdminMerchantUserActions } from '@/console/AdminMerchantUserActions'
import type { MerchantUserSummary } from '@/api/types'
import { DataTable, EmptyRow, Td, Th } from '@/components/console/DataTable'
import { StatusBadge } from '@/components/console/StatusBadge'

/**
 * 가맹점 사용자 명부(내부 운영자 콘솔). 비밀번호 관련 값은 애초에 응답에 없다(Projection
 * 단계에서 제외).
 *
 * [canManage]가 `true`일 때만 행 액션을 그린다 — 관리 권한이 있는 내부 역할
 * (SUPER_ADMIN/OPERATOR)에게만이다(`canManageMerchantAccounts`). VIEWER는 명부만 본다.
 * 내부 운영자는 가맹점 사용자가 될 수 없으므로 "자기 자신" 개념이 없다(내부 직원 명부와
 * 다른 점).
 */
export function AdminMerchantUserTable({
	merchantId,
	merchantUsers,
	canManage,
}: {
	merchantId: string
	merchantUsers: readonly MerchantUserSummary[]
	canManage: boolean
}) {
	return (
		<DataTable
			head={
				<>
					<Th>로그인 아이디</Th>
					<Th>이름</Th>
					<Th>이메일</Th>
					<Th>역할</Th>
					<Th>상태</Th>
					<Th>마지막 로그인</Th>
					{canManage && <Th align="right"> </Th>}
				</>
			}
		>
			{merchantUsers.length === 0 ? (
				<EmptyRow colSpan={canManage ? 7 : 6}>아직 등록된 사용자가 없습니다.</EmptyRow>
			) : (
				merchantUsers.map((user) => (
					<tr key={user.merchantUserId} className="hover:bg-muted/40">
						<Td variant="mono" className="text-foreground">
							{user.loginId}
						</Td>
						<Td className="font-medium">{user.userName}</Td>
						<Td className="text-xs text-muted-foreground">{user.email}</Td>
						<Td className="text-xs">{String(user.role)}</Td>
						<Td>
							<StatusBadge status={String(user.status)} />
						</Td>
						<Td variant="mono" className="text-xs">
							{formatDateTime(user.lastLoginAt)}
						</Td>
						{canManage && (
							<Td className="text-right">
								<AdminMerchantUserActions merchantId={merchantId} user={user} />
							</Td>
						)}
					</tr>
				))
			)}
		</DataTable>
	)
}
