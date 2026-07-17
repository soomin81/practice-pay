rootProject.name = "practice-pay"

include("modules:domain")
include("modules:application")
include("modules:infra-persistence")
include("db-core")
include("architecture-tests")
include("apps:api-payment")
include("apps:api-admin")
include("apps:api-merchant")
include("apps:batch")
