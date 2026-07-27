import { useState } from 'react'
import { useChangeMerchantUserRole, useChangeMerchantUserStatus } from '@/console/useMerchantUsers'
import {
	INVITABLE_MERCHANT_ROLES,
	type MerchantUserRole,
	type MerchantUserStatusAction,
	type MerchantUserSummary,
} from '@/api/types'
import { AdminApiError } from '@/api/client'
import { Button } from '@/components/ui/button'

/**
 * 가맹점 사용자 명부 한 행의 액션(내부 운영자 콘솔). 상태에 따라 할 수 있는 일이 다르다 —
 * 도메인 상태 머신을 그대로 옮겼다: `ACTIVE`→정지·종료·역할 변경, `SUSPENDED`→재개·종료,
 * `INVITED`→종료만, `TERMINATED`/`LOCKED`→없음.
 *
 * 가맹점 콘솔의 `MerchantUserActions`와 같은 모양이되 **초대 재발송·취소는 없다**(내부
 * 운영자는 가맹점의 초대를 대신 관리하지 않는다). 이 컴포넌트는 관리 권한이 있는 내부
 * 역할(SUPER_ADMIN/OPERATOR)일 때만 렌더된다(`AdminMerchantUserTable`이 판단).
 */
export function AdminMerchantUserActions({
	merchantId,
	user,
}: {
	merchantId: string
	user: MerchantUserSummary
}) {
	const status = String(user.status)
	const changeStatus = useChangeMerchantUserStatus(merchantId)
	const changeRole = useChangeMerchantUserRole(merchantId)
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
						merchantId={merchantId}
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
					merchantId={merchantId}
					merchantUserId={user.merchantUserId}
					action="terminate"
					label="종료"
					confirmLabel="종료하면 되돌릴 수 없습니다. 계속할까요?"
					disabled={pending}
				/>
				{status === 'ACTIVE' && <RoleAction merchantId={merchantId} user={user} disabled={pending} />}
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
	merchantId,
	merchantUserId,
	action,
	label,
	confirmLabel,
	disabled,
}: {
	merchantId: string
	merchantUserId: string
	action: MerchantUserStatusAction
	label: string
	confirmLabel: string
	disabled: boolean
}) {
	const [confirming, setConfirming] = useState(false)
	const changeStatus = useChangeMerchantUserStatus(merchantId)

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
 * 역할 변경. 선택지는 `INVITABLE_MERCHANT_ROLES`(ADMIN/VIEWER)를 재사용한다 — **`OWNER`
 * 승격이 불가능하다는 도메인 규칙이 화면에도 반영된다**(서버도 400). `OWNER` 계정 자체는
 * 강등이 마지막 OWNER 보호에 걸리기 쉬워 역할 변경 버튼을 감춘다(가맹점 콘솔과 같은 판단).
 */
function RoleAction({
	merchantId,
	user,
	disabled,
}: {
	merchantId: string
	user: MerchantUserSummary
	disabled: boolean
}) {
	const [editing, setEditing] = useState(false)
	const [role, setRole] = useState<MerchantUserRole>(String(user.role) === 'VIEWER' ? 'VIEWER' : 'ADMIN')
	const changeRole = useChangeMerchantUserRole(merchantId)

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
				{INVITABLE_MERCHANT_ROLES.map((selectableRole) => (
					<option key={selectableRole} value={selectableRole}>
						{selectableRole}
					</option>
				))}
			</select>
			<Button
				size="sm"
				disabled={changeRole.isPending}
				onClick={() =>
					changeRole.mutate({ merchantUserId: user.merchantUserId, role }, { onSuccess: () => setEditing(false) })
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
		// 409는 사실상 둘 중 하나다: 마지막 활성 OWNER 보호, 또는 허용되지 않는 상태 전이.
		if (error.isConflict) return error.message
		if (error.isForbidden) return '이 계정을 변경할 권한이 없습니다.'
		if (error.status === 404) return '이미 삭제되었거나 찾을 수 없는 계정입니다.'
		return error.message
	}
	return '계정 변경에 실패했습니다.'
}
