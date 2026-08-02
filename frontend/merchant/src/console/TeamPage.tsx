import { useMe } from '@/auth/useAuth'
import { useMerchantUsers } from '@/console/useMerchantUsers'
import { InviteSubAccountForm } from '@/console/InviteSubAccountForm'
import { MerchantUserTable } from '@/console/MerchantUserTable'
import { MerchantApiError } from '@/api/client'
import { PageHeader } from '@/components/console/PageHeader'
import { Panel } from '@/components/console/Panel'

/**
 * 팀 계정 페이지 — 명부와 초대 폼을 조립한다. 상태 분기(로딩/오류/목록)는 여기서 정하고
 * 모양은 하위 컴포넌트가 갖는다(`ApiKeysPage`와 같은 구조).
 */
export function TeamPage() {
	const users = useMerchantUsers()
	// 자기 자신 행의 액션을 감추기 위해 현재 사용자를 넘긴다(캐시된 쿼리라 추가 요청이 없다).
	const { data: me } = useMe()

	return (
		<>
			<PageHeader title="팀 계정" description="가맹점 사용자를 초대하고 역할·상태를 관리합니다." />

			<div className="flex flex-col gap-6">
				<Panel
					title="하위 계정 초대"
					meta="ADMIN 또는 VIEWER 계정을 초대합니다. 발급된 초대 링크로 본인이 비밀번호를 설정하면 활성화됩니다."
				>
					<InviteSubAccountForm />
				</Panel>

				<Panel
					title="가맹점 사용자"
					meta="INVITED는 아직 초대 링크로 비밀번호를 설정하지 않은 계정입니다."
					bodyClassName="px-5 pb-5"
				>
					{users.isPending && <p className="text-sm text-muted-foreground">불러오는 중…</p>}
					{users.isError && <p className="text-sm text-destructive">{listErrorMessage(users.error)}</p>}
					{users.isSuccess && (
						<MerchantUserTable merchantUsers={users.data.merchantUsers} currentMerchantUserId={me?.merchantUserId} />
					)}
				</Panel>
			</div>
		</>
	)
}

function listErrorMessage(error: unknown): string {
	if (error instanceof MerchantApiError) {
		if (error.isForbidden) return '가맹점 사용자 목록을 볼 권한이 없습니다(OWNER/ADMIN만 가능).'
		return error.message
	}
	return '가맹점 사용자 목록을 불러오지 못했습니다.'
}
