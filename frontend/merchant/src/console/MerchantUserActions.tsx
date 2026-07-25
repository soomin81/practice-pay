import { useState } from 'react'
import {
	useChangeMerchantUserRole,
	useChangeMerchantUserStatus,
	useResendInvitation,
	useRevokeInvitation,
} from '@/console/useMerchantUsers'
import { InvitationReveal } from '@/console/InvitationReveal'
import { INVITABLE_ROLES, type MerchantUserRole, type MerchantUserStatusAction, type MerchantUserSummary } from '@/api/types'
import { MerchantApiError } from '@/api/client'
import { Button } from '@/components/ui/button'

/**
 * 명부 한 행의 액션. **상태에 따라 할 수 있는 일이 다르다** — 도메인 상태 머신을 화면에
 * 그대로 옮긴 것이다(`docs/architecture/identity-access-api-key.md`의 "5. 계정 상태"):
 *
 * - `ACTIVE` → 정지 / 종료 / 역할 변경
 * - `SUSPENDED` → 재개 / 종료
 * - `INVITED` → 종료만(아직 활성화 전이라 정지·역할 변경이 의미 없다)
 * - `TERMINATED`/`LOCKED` → 없음(종료는 되돌릴 수 없고, 잠금은 로그인 흐름이 푼다)
 *
 * 확인은 브라우저 `confirm()`이 아니라 인라인으로 한 번 더 묻는다(`ApiKeyTable`의 폐기
 * 버튼과 같은 패턴).
 */
export function MerchantUserActions({ user }: { user: MerchantUserSummary }) {
	const status = String(user.status)
	const changeStatus = useChangeMerchantUserStatus()
	const changeRole = useChangeMerchantUserRole()
	const error = changeStatus.error ?? changeRole.error
	const pending = changeStatus.isPending || changeRole.isPending

	if (status === 'TERMINATED' || status === 'LOCKED') {
		return <span className="text-xs text-muted-foreground">—</span>
	}

	return (
		<div className="flex flex-col items-end gap-1">
			<div className="flex flex-wrap items-center justify-end gap-1">
				{status === 'ACTIVE' && (
					<StatusAction
						merchantUserId={user.merchantUserId}
						action="suspend"
						label="정지"
						confirmLabel="정지할까요?"
						disabled={pending}
					/>
				)}
				{status === 'SUSPENDED' && (
					<Button
						variant="outline"
						size="sm"
						disabled={pending}
						onClick={() => changeStatus.mutate({ merchantUserId: user.merchantUserId, action: 'reactivate' })}
					>
						재개
					</Button>
				)}
				<StatusAction
					merchantUserId={user.merchantUserId}
					action="terminate"
					label="종료"
					confirmLabel="종료하면 되돌릴 수 없습니다. 계속할까요?"
					disabled={pending}
				/>
				{status === 'INVITED' && <InvitationActions user={user} disabled={pending} />}
				{status === 'ACTIVE' && <RoleAction user={user} disabled={pending} />}
			</div>
			{error && (
				<span role="alert" className="text-xs text-destructive">
					{actionErrorMessage(error)}
				</span>
			)}
		</div>
	)
}

/**
 * 초대 재발송·취소. `INVITED` 행에만 나온다.
 *
 * **재발송은 새 Token을 발급하는 것이라 이전 링크가 죽는다** — 성공하면 새 링크를
 * 1회 노출한다([InvitationReveal], 최초 발급과 같은 컴포넌트). 취소는 되돌릴 수 없으니
 * 인라인으로 한 번 더 묻는다(계정 자체는 남는다 — 없애려면 "종료"를 쓴다).
 */
function InvitationActions({ user, disabled }: { user: MerchantUserSummary; disabled: boolean }) {
	const [confirmingRevoke, setConfirmingRevoke] = useState(false)
	const resend = useResendInvitation()
	const revoke = useRevokeInvitation()

	if (resend.isSuccess) {
		return (
			<InvitationReveal
				title="새 초대 링크가 발급되었습니다 — 이전 링크는 더 이상 동작하지 않습니다"
				description={
					<>
						<strong>{user.loginId}</strong>에게 아래 링크를 다시 전달하세요. 이 화면을 벗어나면 확인할 수 없습니다.
					</>
				}
				invitationToken={resend.data.invitationToken}
				onDone={() => resend.reset()}
			/>
		)
	}

	if (confirmingRevoke) {
		return (
			<span className="inline-flex items-center gap-1">
				<span className="text-xs text-muted-foreground">초대를 취소할까요? (계정은 남습니다)</span>
				<Button
					variant="destructive"
					size="sm"
					disabled={revoke.isPending}
					onClick={() =>
						revoke.mutate(user.merchantUserId, { onSuccess: () => setConfirmingRevoke(false) })
					}
				>
					확인
				</Button>
				<Button variant="ghost" size="sm" disabled={revoke.isPending} onClick={() => setConfirmingRevoke(false)}>
					취소
				</Button>
			</span>
		)
	}

	return (
		<>
			<Button
				variant="outline"
				size="sm"
				disabled={disabled || resend.isPending}
				onClick={() => resend.mutate(user.merchantUserId)}
			>
				{resend.isPending ? '발송 중…' : '초대 재발송'}
			</Button>
			<Button variant="ghost" size="sm" disabled={disabled} onClick={() => setConfirmingRevoke(true)}>
				초대 취소
			</Button>
		</>
	)
}

/** 되돌릴 수 없거나 영향이 큰 동작은 인라인으로 한 번 더 확인한다. */
function StatusAction({
	merchantUserId,
	action,
	label,
	confirmLabel,
	disabled,
}: {
	merchantUserId: string
	action: MerchantUserStatusAction
	label: string
	confirmLabel: string
	disabled: boolean
}) {
	const [confirming, setConfirming] = useState(false)
	const changeStatus = useChangeMerchantUserStatus()

	if (!confirming) {
		return (
			<Button variant="destructive" size="sm" disabled={disabled} onClick={() => setConfirming(true)}>
				{label}
			</Button>
		)
	}

	return (
		<span className="inline-flex items-center gap-1">
			<span className="text-xs text-muted-foreground">{confirmLabel}</span>
			<Button
				variant="destructive"
				size="sm"
				disabled={changeStatus.isPending}
				onClick={() => changeStatus.mutate({ merchantUserId, action })}
			>
				확인
			</Button>
			<Button variant="ghost" size="sm" disabled={changeStatus.isPending} onClick={() => setConfirming(false)}>
				취소
			</Button>
		</span>
	)
}

/**
 * 역할 변경. 선택지는 `INVITABLE_ROLES`(ADMIN/VIEWER)를 재사용한다 — **OWNER 승격이
 * 불가능하다는 도메인 규칙이 화면에도 그대로 반영된다**(서버도 400으로 막는다).
 */
function RoleAction({ user, disabled }: { user: MerchantUserSummary; disabled: boolean }) {
	const [editing, setEditing] = useState(false)
	const [role, setRole] = useState<MerchantUserRole>(String(user.role) === 'VIEWER' ? 'VIEWER' : 'ADMIN')
	const changeRole = useChangeMerchantUserRole()

	// OWNER의 역할 변경은 강등이라 서버 규칙(마지막 OWNER 보호·ADMIN 차단)에 걸리기 쉽다 —
	// 화면에서는 하위 역할(ADMIN/VIEWER)만 바꿀 수 있게 둔다.
	if (String(user.role) === 'OWNER') return null

	if (!editing) {
		return (
			<Button variant="outline" size="sm" disabled={disabled} onClick={() => setEditing(true)}>
				역할 변경
			</Button>
		)
	}

	return (
		<span className="inline-flex items-center gap-1">
			<select
				aria-label="역할 선택"
				className="h-7 rounded-md border border-input bg-background px-1.5 text-xs"
				value={role}
				onChange={(event) => setRole(event.target.value as MerchantUserRole)}
			>
				{INVITABLE_ROLES.map((invitableRole) => (
					<option key={invitableRole} value={invitableRole}>
						{invitableRole}
					</option>
				))}
			</select>
			<Button
				size="sm"
				disabled={changeRole.isPending}
				onClick={() =>
					changeRole.mutate(
						{ merchantUserId: user.merchantUserId, role },
						{ onSuccess: () => setEditing(false) },
					)
				}
			>
				적용
			</Button>
			<Button variant="ghost" size="sm" disabled={changeRole.isPending} onClick={() => setEditing(false)}>
				취소
			</Button>
		</span>
	)
}

function actionErrorMessage(error: unknown): string {
	if (error instanceof MerchantApiError) {
		// 409는 사실상 둘 중 하나다: 마지막 활성 OWNER 보호, 또는 허용되지 않는 상태 전이.
		if (error.isConflict) return error.message
		if (error.isForbidden) return '이 계정을 변경할 권한이 없습니다.'
		if (error.status === 404) return '이미 삭제되었거나 찾을 수 없는 계정입니다.'
		return error.message
	}
	return '계정 변경에 실패했습니다.'
}
