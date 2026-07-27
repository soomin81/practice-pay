import { formatDateTime } from '@/console/format'
import { AdminMerchantUserActions } from '@/console/AdminMerchantUserActions'
import type { MerchantUserSummary } from '@/api/types'
import { Badge } from '@/components/ui/badge'

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
	if (merchantUsers.length === 0) {
		return <p className="text-sm text-muted-foreground">아직 등록된 사용자가 없습니다.</p>
	}

	return (
		<div className="overflow-x-auto">
			<table className="w-full min-w-[40rem] border-collapse text-sm">
				<thead>
					<tr className="border-b text-left text-xs text-muted-foreground">
						<th className="py-2 pr-4 font-medium">로그인 아이디</th>
						<th className="py-2 pr-4 font-medium">이름</th>
						<th className="py-2 pr-4 font-medium">이메일</th>
						<th className="py-2 pr-4 font-medium">역할</th>
						<th className="py-2 pr-4 font-medium">상태</th>
						<th className="py-2 pr-4 font-medium">마지막 로그인</th>
						{canManage && <th className="py-2 font-medium" />}
					</tr>
				</thead>
				<tbody>
					{merchantUsers.map((user) => (
						<tr key={user.merchantUserId} className="border-b last:border-0">
							<td className="py-2.5 pr-4 font-mono text-xs">{user.loginId}</td>
							<td className="py-2.5 pr-4">{user.userName}</td>
							<td className="py-2.5 pr-4 text-xs text-muted-foreground">{user.email}</td>
							<td className="py-2.5 pr-4 text-xs">{String(user.role)}</td>
							<td className="py-2.5 pr-4">
								<StatusBadge status={String(user.status)} />
							</td>
							<td className="py-2.5 pr-4 text-xs text-muted-foreground">{formatDateTime(user.lastLoginAt)}</td>
							{canManage && (
								<td className="py-2.5 text-right">
									<AdminMerchantUserActions merchantId={merchantId} user={user} />
								</td>
							)}
						</tr>
					))}
				</tbody>
			</table>
		</div>
	)
}

function StatusBadge({ status }: { status: string }) {
	if (status === 'ACTIVE') return <Badge variant="secondary">ACTIVE</Badge>
	// 아직 초대 링크로 비밀번호를 설정하지 않은 상태.
	if (status === 'INVITED') return <Badge variant="outline">INVITED</Badge>
	if (status === 'LOCKED' || status === 'SUSPENDED' || status === 'TERMINATED') {
		return <Badge variant="destructive">{status}</Badge>
	}
	return <Badge variant="outline">{status}</Badge>
}
