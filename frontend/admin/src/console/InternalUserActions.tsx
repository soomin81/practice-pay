import { useState } from 'react'
import { useChangeInternalUserRole, useChangeInternalUserStatus } from '@/console/useInternalUsers'
import {
	ISSUABLE_INTERNAL_ROLES,
	type InternalUserRole,
	type InternalUserStatusAction,
	type InternalUserSummary,
} from '@/api/types'
import { AdminApiError } from '@/api/client'
import { Button } from '@/components/ui/button'

/**
 * 명부 한 행의 액션. **상태에 따라 할 수 있는 일이 다르다** — 도메인 상태 머신을 화면에
 * 그대로 옮긴 것이다(`docs/architecture/identity-access-api-key.md`의 "계정 상태"):
 *
 * - `ACTIVE` → 정지 / 종료 / 역할 변경
 * - `SUSPENDED` → 재개 / 종료
 * - `INVITED` → 종료만(아직 활성화 전이라 정지·역할 변경이 의미 없다)
 * - `TERMINATED`/`LOCKED` → 없음(종료는 되돌릴 수 없고, 잠금은 로그인 흐름이 푼다)
 *
 * 가맹점 쪽 `MerchantUserActions`와 같은 모양이되 **초대 재발송·취소는 없다** — 그 흐름은
 * 아직 내부 운영자 API에 없다(가맹점 콘솔에만 있다). 확인은 브라우저 `confirm()`이 아니라
 * 인라인으로 한 번 더 묻는다.
 */
export function InternalUserActions({ user }: { user: InternalUserSummary }) {
	const status = String(user.status)
	const changeStatus = useChangeInternalUserStatus()
	const changeRole = useChangeInternalUserRole()
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
						internalUserId={user.internalUserId}
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
						onClick={() => changeStatus.mutate({ internalUserId: user.internalUserId, action: 'reactivate' })}
					>
						재개
					</Button>
				)}
				<StatusAction
					internalUserId={user.internalUserId}
					action="terminate"
					label="종료"
					confirmLabel="종료하면 되돌릴 수 없습니다. 계속할까요?"
					disabled={pending}
				/>
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

/** 되돌릴 수 없거나 영향이 큰 동작은 인라인으로 한 번 더 확인한다. */
function StatusAction({
	internalUserId,
	action,
	label,
	confirmLabel,
	disabled,
}: {
	internalUserId: string
	action: InternalUserStatusAction
	label: string
	confirmLabel: string
	disabled: boolean
}) {
	const [confirming, setConfirming] = useState(false)
	const changeStatus = useChangeInternalUserStatus()

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
				onClick={() => changeStatus.mutate({ internalUserId, action })}
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
 * 역할 변경. 선택지는 `ISSUABLE_INTERNAL_ROLES`(OPERATOR/VIEWER)를 재사용한다 —
 * **`SUPER_ADMIN` 승격이 불가능하다는 도메인 규칙이 화면에도 그대로 반영된다**(서버도
 * 400으로 막는다). `SUPER_ADMIN` 계정 자체는 강등이 마지막 SUPER_ADMIN 보호에 걸리기 쉬워
 * 역할 변경 버튼을 감춘다(가맹점 쪽에서 `OWNER` 행에 역할 변경을 감춘 것과 같은 판단).
 */
function RoleAction({ user, disabled }: { user: InternalUserSummary; disabled: boolean }) {
	const [editing, setEditing] = useState(false)
	const [role, setRole] = useState<InternalUserRole>(String(user.role) === 'VIEWER' ? 'VIEWER' : 'OPERATOR')
	const changeRole = useChangeInternalUserRole()

	if (String(user.role) === 'SUPER_ADMIN') return null

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
				onChange={(event) => setRole(event.target.value as InternalUserRole)}
			>
				{ISSUABLE_INTERNAL_ROLES.map((selectableRole) => (
					<option key={selectableRole} value={selectableRole}>
						{selectableRole}
					</option>
				))}
			</select>
			<Button
				size="sm"
				disabled={changeRole.isPending}
				onClick={() =>
					changeRole.mutate({ internalUserId: user.internalUserId, role }, { onSuccess: () => setEditing(false) })
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
	if (error instanceof AdminApiError) {
		// 409는 사실상 둘 중 하나다: 마지막 활성 SUPER_ADMIN 보호, 또는 허용되지 않는 상태 전이.
		if (error.isConflict) return error.message
		if (error.isForbidden) return '이 계정을 변경할 권한이 없습니다.'
		if (error.status === 404) return '이미 삭제되었거나 찾을 수 없는 계정입니다.'
		return error.message
	}
	return '계정 변경에 실패했습니다.'
}
