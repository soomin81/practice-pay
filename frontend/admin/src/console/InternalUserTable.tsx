import { formatDateTime } from '@/console/format'
import { InternalUserActions } from '@/console/InternalUserActions'
import type { InternalUserSummary } from '@/api/types'
import { Badge } from '@/components/ui/badge'

/**
 * 내부 운영자 명부. 비밀번호 관련 값은 애초에 응답에 없다(Projection 단계에서 제외).
 *
 * [currentInternalUserId]가 주어지면 **그 행에는 액션을 그리지 않는다** — 자기 자신은
 * 정지·종료·역할 변경의 대상이 될 수 없다(서버도 403으로 막지만, 누를 수 있게 두고
 * 거부하는 것보다 아예 감추는 편이 낫다). 가맹점 쪽 `MerchantUserTable`과 같은 판단이다.
 */
export function InternalUserTable({
	internalUsers,
	currentInternalUserId,
}: {
	internalUsers: readonly InternalUserSummary[]
	currentInternalUserId?: string
}) {
	if (internalUsers.length === 0) {
		return <p className="text-sm text-muted-foreground">아직 등록된 내부 직원이 없습니다.</p>
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
						<th className="py-2 font-medium" />
					</tr>
				</thead>
				<tbody>
					{internalUsers.map((user) => (
						<tr key={user.internalUserId} className="border-b last:border-0">
							<td className="py-2.5 pr-4 font-mono text-xs">{user.loginId}</td>
							<td className="py-2.5 pr-4">{user.userName}</td>
							<td className="py-2.5 pr-4 text-xs text-muted-foreground">{user.email}</td>
							<td className="py-2.5 pr-4 text-xs">{String(user.role)}</td>
							<td className="py-2.5 pr-4">
								<StatusBadge status={String(user.status)} />
							</td>
							<td className="py-2.5 pr-4 text-xs text-muted-foreground">{formatDateTime(user.lastLoginAt)}</td>
							<td className="py-2.5 text-right">
								{user.internalUserId === currentInternalUserId ? (
									<span className="text-xs text-muted-foreground">본인</span>
								) : (
									<InternalUserActions user={user} />
								)}
							</td>
						</tr>
					))}
				</tbody>
			</table>
		</div>
	)
}

function StatusBadge({ status }: { status: string }) {
	if (status === 'ACTIVE') return <Badge variant="secondary">ACTIVE</Badge>
	// 아직 초대 링크로 비밀번호를 설정하지 않은 상태 — 운영자가 가장 자주 확인하는 값이다.
	if (status === 'INVITED') return <Badge variant="outline">INVITED</Badge>
	if (status === 'LOCKED' || status === 'SUSPENDED' || status === 'TERMINATED') {
		return <Badge variant="destructive">{status}</Badge>
	}
	return <Badge variant="outline">{status}</Badge>
}
