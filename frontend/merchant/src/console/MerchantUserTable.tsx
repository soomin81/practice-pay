import { describeInvitation, formatDateTime } from '@/console/format'
import { MerchantUserActions } from '@/console/MerchantUserActions'
import type { MerchantUserSummary } from '@/api/types'
import { DataTable, EmptyRow, Td, Th } from '@/components/console/DataTable'
import { StatusBadge } from '@/components/console/StatusBadge'

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
					<Th align="right"> </Th>
				</>
			}
		>
			{merchantUsers.length === 0 ? (
				<EmptyRow colSpan={7}>아직 등록된 사용자가 없습니다.</EmptyRow>
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
							{String(user.status) === 'INVITED' && <InvitationHint expiresAt={user.pendingInvitationExpiresAt} />}
						</Td>
						<Td variant="mono" className="text-xs">
							{formatDateTime(user.lastLoginAt)}
						</Td>
						<Td className="text-right">
							{user.merchantUserId === currentMerchantUserId ? (
								<span className="text-xs text-muted-foreground">본인</span>
							) : (
								<MerchantUserActions user={user} />
							)}
						</Td>
					</tr>
				))
			)}
		</DataTable>
	)
}

/**
 * `INVITED` 행에 왜 아직 활성화되지 않았는지를 덧붙인다 — 초대가 만료됐거나 취소됐으면
 * 재발송이 필요하다는 뜻이라 강조한다.
 */
function InvitationHint({ expiresAt }: { expiresAt: string | null | undefined }) {
	const { text, expired } = describeInvitation(expiresAt)
	return <div className={`mt-0.5 text-xs ${expired ? 'text-destructive' : 'text-muted-foreground'}`}>{text}</div>
}
