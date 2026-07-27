import { Link, useParams } from 'react-router-dom'
import { useMerchants } from '@/console/useMerchants'
import { useMerchantUsers } from '@/console/useMerchantUsers'
import { AdminMerchantUserTable } from '@/console/AdminMerchantUserTable'
import { formatDateTime } from '@/console/format'
import { AdminApiError } from '@/api/client'
import { canManageMerchantAccounts, type MeResponse } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

/**
 * 가맹점 상세 — 그 가맹점의 사용자 명부와 계정 관리(정지·재개·종료·역할 변경)를 보여준다.
 *
 * **관리 액션은 SUPER_ADMIN/OPERATOR에게만 그린다**(`canManageMerchantAccounts`). 서버도
 * `POST /admin/merchants/**`를 그 역할로 좁히지만(SecurityConfig), 누를 수 있게 두고 거부하는
 * 것보다 감추는 편이 낫다 — VIEWER는 명부만 본다.
 *
 * 헤더의 가맹점 이름·코드는 이미 캐시된 가맹점 목록(`useMerchants`)에서 찾는다 — 별도
 * "가맹점 단건 조회" 엔드포인트를 만들지 않았다. 목록을 거치지 않고 깊은 링크로 들어와
 * 캐시에 없으면 식별자만 보여준다.
 */
export function MerchantDetailPage({ me }: { me: MeResponse }) {
	const { merchantId = '' } = useParams()
	const merchants = useMerchants()
	const users = useMerchantUsers(merchantId)
	const merchant = merchants.data?.merchants.find((m) => m.merchantId === merchantId)
	const canManage = canManageMerchantAccounts(String(me.role))

	return (
		<div className="flex flex-col gap-6">
			<div>
				<Button asChild variant="ghost" size="sm">
					<Link to="/">← 가맹점 목록</Link>
				</Button>
			</div>

			<Card>
				<CardHeader>
					<CardTitle>{merchant ? merchant.merchantName : merchantId}</CardTitle>
					<CardDescription>
						{merchant ? (
							<>
								<span className="font-mono">{merchant.merchantCode}</span> · {String(merchant.status)} · 등록{' '}
								{formatDateTime(merchant.createdAt)}
							</>
						) : (
							<span className="font-mono">{merchantId}</span>
						)}
					</CardDescription>
				</CardHeader>
				<CardContent>
					<p className="text-sm text-muted-foreground">
						{canManage
							? '가맹점이 스스로 잠기거나(마지막 OWNER 정지) 계정 사고가 났을 때 개입하는 화면입니다. 마지막 활성 OWNER는 정지·종료·강등할 수 없습니다.'
							: '조회 전용입니다. 계정 관리는 SUPER_ADMIN 또는 OPERATOR만 할 수 있습니다.'}
					</p>
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>가맹점 사용자</CardTitle>
					<CardDescription>INVITED는 아직 초대 링크로 비밀번호를 설정하지 않은 계정입니다.</CardDescription>
				</CardHeader>
				<CardContent>
					{users.isPending && <p className="text-sm text-muted-foreground">불러오는 중…</p>}
					{users.isError && <p className="text-sm text-destructive">{listErrorMessage(users.error)}</p>}
					{users.isSuccess && (
						<AdminMerchantUserTable merchantId={merchantId} merchantUsers={users.data.merchantUsers} canManage={canManage} />
					)}
				</CardContent>
			</Card>
		</div>
	)
}

function listErrorMessage(error: unknown): string {
	if (error instanceof AdminApiError) {
		return error.message
	}
	return '가맹점 사용자 목록을 불러오지 못했습니다.'
}
