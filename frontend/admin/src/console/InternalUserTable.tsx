import { formatDateTime } from '@/console/format'
import { InternalUserActions } from '@/console/InternalUserActions'
import type { InternalUserSummary } from '@/api/types'
import { DataTable, EmptyRow, Td, Th } from '@/components/console/DataTable'
import { StatusBadge } from '@/components/console/StatusBadge'

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
			{internalUsers.length === 0 ? (
				<EmptyRow colSpan={7}>아직 등록된 내부 직원이 없습니다.</EmptyRow>
			) : (
				internalUsers.map((user) => (
					<tr key={user.internalUserId} className="hover:bg-muted/40">
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
						<Td className="text-right">
							{user.internalUserId === currentInternalUserId ? (
								<span className="text-xs text-muted-foreground">본인</span>
							) : (
								<InternalUserActions user={user} />
							)}
						</Td>
					</tr>
				))
			)}
		</DataTable>
	)
}
