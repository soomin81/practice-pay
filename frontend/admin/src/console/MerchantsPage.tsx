import { useMerchants } from '@/console/useMerchants'
import { MerchantTable } from '@/console/MerchantTable'
import { RegisterMerchantForm } from '@/console/RegisterMerchantForm'
import { AdminApiError } from '@/api/client'
import { canRegisterMerchant, type MeResponse } from '@/api/types'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

/**
 * 가맹점 목록·등록 페이지. 상태 분기는 여기서 정하고 모양은 하위 컴포넌트가 갖는다.
 *
 * **등록 폼은 `SUPER_ADMIN`/`OPERATOR`에게만 보인다** — `VIEWER`는 조회 전용이다.
 * 서버도 403으로 막지만(SecurityConfig의 메서드 스코핑), 누를 수 있게 두고 거부하는
 * 것보다 감추는 편이 낫다.
 */
export function MerchantsPage({ me }: { me: MeResponse }) {
	const merchants = useMerchants()

	return (
		<div className="flex flex-col gap-6">
			{canRegisterMerchant(String(me.role)) && (
				<Card>
					<CardHeader>
						<CardTitle>가맹점 등록</CardTitle>
						<CardDescription>
							가맹점과 최초 OWNER 계정을 함께 만듭니다. 가맹점은 스스로 가입할 수 없습니다.
						</CardDescription>
					</CardHeader>
					<CardContent>
						<RegisterMerchantForm />
					</CardContent>
				</Card>
			)}

			<Card>
				<CardHeader>
					<CardTitle>가맹점</CardTitle>
					<CardDescription>등록된 가맹점 목록입니다.</CardDescription>
				</CardHeader>
				<CardContent>
					{merchants.isPending && <p className="text-sm text-muted-foreground">불러오는 중…</p>}
					{merchants.isError && <p className="text-sm text-destructive">{listErrorMessage(merchants.error)}</p>}
					{merchants.isSuccess && <MerchantTable merchants={merchants.data.merchants} />}
				</CardContent>
			</Card>
		</div>
	)
}

function listErrorMessage(error: unknown): string {
	if (error instanceof AdminApiError) return error.message
	return '가맹점 목록을 불러오지 못했습니다.'
}
