import { describeInvitation, formatDateTime } from '@/console/format'
import { MerchantUserActions } from '@/console/MerchantUserActions'
import type { MerchantUserSummary } from '@/api/types'
import { Badge } from '@/components/ui/badge'

/**
 * 가맹점 사용자 명부. 비밀번호 관련 값은 애초에 응답에 없다(Projection 단계에서 제외).
 *
 * [currentMerchantUserId]가 주어지면 **그 행에는 액션을 그리지 않는다** — 자기 자신은
 * 정지·종료·역할 변경의 대상이 될 수 없다(서버도 403으로 막지만, 누를 수 있게 두고
 * 거부하는 것보다 아예 감추는 편이 낫다).
 */
export function MerchantUserTable({
	merchantUsers,
	currentMerchantUserId,
}: {
	merchantUsers: readonly MerchantUserSummary[]
	currentMerchantUserId?: string
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
						<th className="py-2 font-medium" />
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
								{String(user.status) === 'INVITED' && <InvitationHint expiresAt={user.pendingInvitationExpiresAt} />}
							</td>
							<td className="py-2.5 pr-4 text-xs text-muted-foreground">{formatDateTime(user.lastLoginAt)}</td>
							<td className="py-2.5 text-right">
								{user.merchantUserId === currentMerchantUserId ? (
									<span className="text-xs text-muted-foreground">본인</span>
								) : (
									<MerchantUserActions user={user} />
								)}
							</td>
						</tr>
					))}
				</tbody>
			</table>
		</div>
	)
}

/**
 * `INVITED` 행에 왜 아직 활성화되지 않았는지를 덧붙인다 — 초대가 만료됐거나 취소됐으면
 * 재발송이 필요하다는 뜻이라 강조한다.
 */
function InvitationHint({ expiresAt }: { expiresAt: string | null | undefined }) {
	const { text, expired } = describeInvitation(expiresAt)
	return (
		<div className={`mt-0.5 text-xs ${expired ? 'text-destructive' : 'text-muted-foreground'}`}>{text}</div>
	)
}

function StatusBadge({ status }: { status: string }) {
	if (status === 'ACTIVE') return <Badge variant="secondary">ACTIVE</Badge>
	// 아직 초대 링크로 비밀번호를 설정하지 않은 상태 — 운영자가 가장 자주 확인하는 값이다.
	if (status === 'INVITED') return <Badge variant="outline">INVITED</Badge>
	if (status === 'LOCKED' || status === 'SUSPENDED' || status === 'TERMINATED') {
		return <Badge variant="destructive">{status}</Badge>
	}
	// 그 밖의 상태는 중립적으로 — shadcn 생성물의 variant 목록에 muted는 없다.
	return <Badge variant="outline">{status}</Badge>
}
