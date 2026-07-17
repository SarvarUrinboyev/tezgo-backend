# TEZGO Admin Security Containment Wave 0.1 - skipped-test inventory (2026-07-17)

## Result

- Worktree: `C:\Users\Laptop\Documents\tezgo-admin-security-containment`
- Branch / HEAD: `fix/admin-security-containment-wave-0` / `b58a91bfb3953cdf9634bc138d88131e666cb39d`
- Disposable execution: Docker Desktop Server `29.6.1`, Testcontainers `1.21.4`, `postgres:16-alpine` / PostgreSQL `16.14`.
- Focused command exit `0`; duration `02:11`; `63` executed, `63` passed, `0` skipped, `0` failure, `0` error.
- Full `.\mvnw.cmd test`: exit `0`, duration `03:58`, `506` passed, `0` skipped/failure/error.
- `.\mvnw.cmd clean package`: exit `0`, duration `02:41`; package also reports `506` passed, `0` skipped/failure/error.

## Reconciliation of 493, 468, 490 and 506

| Snapshot | XML reports | Testcases | Passed | Skipped | Failed | Errors | Meaning |
|---|---:|---:|---:|---:|---:|---:|---|
| Pre-Docker remediation | 102 | 468 | 443 | 25 | 0 | 0 | Maven/Surefire counted skipped cases in the 468 total. |
| Earlier receipt | - | 493 | - | - | - | - | Arithmetic receipt error: 468 already included the 25 skipped. |
| Canonical backend baseline | 97 | 490 | 490 | 0 | 0 | 0 | Prior all-pass baseline. |
| Final disposable PostgreSQL | 102 | 506 | 506 | 0 | 0 | 0 | The 25 skipped methods expanded to 63 passing PostgreSQL testcases (+38); Wave 0 adds 16 tests over the 490 baseline. |

Final Surefire XML-set SHA-256: `4FECB513B23A2E314453CF0BFC4FCE818B550429C1AD84578D572F99F54F1868`.

## Previously skipped methods, now executed

| Class | Method(s) in final XML | Category | Final status |
|---|---|---|---|
| com.taxi.backend.schema.SchemaMigrationBootstrapTest | flywayChainAppliesFromScratch_throughV50_includingV49 | migration | PASS |
| com.taxi.backend.schema.SchemaMigrationBootstrapTest | systemSettingEntityMapsToMigratedAppSettings | migration | PASS |
| com.taxi.backend.schema.SchemaMigrationBootstrapTest | v47AddsDurableIdempotencyAndTheImmediateTripGuard | migration | PASS |
| com.taxi.backend.schema.SchemaMigrationBootstrapTest | v48RequestHashColumnAndEntityMappingRemainAligned | migration | PASS |
| com.taxi.backend.schema.SchemaMigrationBootstrapTest | v49AddsTheSingleLiveOfferDatabaseConstraint | migration | PASS |
| com.taxi.backend.schema.SchemaMigrationBootstrapTest | v50CatalogColumnsConstraintsIndexesAndEntityMappingsRemainAligned | migration | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | confirmationFailureRollsBackBeforeAnyWalletMutation | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | ledgerInsertFailureRollsBackClaimBalanceAndLedgerTogether | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | offAndDrainRespectDurableCatalogStateWithoutTouchingNewMoney | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | realSpringProxiesUseOneTransactionForPrepareCompleteAndWalletCredit | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | tenDistinctCatalogPaymentsShareOneDriverWithoutLosingLedgerSnapshots | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[1] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[10] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[11] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[12] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[13] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[14] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[15] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[16] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[17] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[18] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[19] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[2] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[20] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[3] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[4] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[5] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[6] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[7] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[8] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[9] | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | uniqueLedgerConflictRollsBackConfirmationAndBalance | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickPostgresConcurrencyTest | concurrentCompleteClaimsExactlyOneCredit | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickPostgresConcurrencyTest | concurrentDistinctCreditsFormOneAuthoritativeLedgerChain | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickPostgresConcurrencyTest | rollbackAfterBalanceMutationLeavesNoBalanceOrLedgerChange | postgres-concurrency | PASS |
| com.taxi.backend.service.ClickPostgresConcurrencyTest | rollbackAfterLedgerCreationLeavesNoBalanceOrLedgerChange | postgres-concurrency | PASS |
| com.taxi.backend.service.OperatorTripPostgresConcurrencyTest | scheduledTripsRemainOutsideTheImmediateTripConstraint | postgres-concurrency | PASS |
| com.taxi.backend.service.OperatorTripPostgresConcurrencyTest | twentyDistinctKeysForOnePassengerCreateOneImmediateTripAndConflictTheRest | postgres-concurrency | PASS |
| com.taxi.backend.service.OperatorTripPostgresConcurrencyTest | twentyParallelSameKeyRequestsCreateExactlyOneTripAndNineteenReplays | postgres-concurrency | PASS |
| com.taxi.backend.service.OperatorTripPostgresConcurrencyTest | twoParallelSameKeyRequestsCreateOneTripAndOneReplay | postgres-concurrency | PASS |
| com.taxi.backend.service.PaymentLedgerSnapshotRegressionTest | clickCreditWritesAuthoritativeLedgerSnapshots | payment-ledger | PASS |
| com.taxi.backend.service.TripDriverOfferFlywayBootstrapTest | flywayBootstrapsV49WithTypedTimingColumnsAndBigintForeignKeys | migration | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[1] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[10] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[11] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[12] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[13] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[14] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[15] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[16] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[17] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[18] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[19] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[2] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[20] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[3] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[4] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[5] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[6] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[7] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[8] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[9] | postgres-concurrency | PASS |
| com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | terminalOfferAllowsOneNextGenerationButNeverReoffersSameDriver | postgres-concurrency | PASS |

All 25 pre-Docker skipped XML testcase entries are represented above. Parameterized `@RepeatedTest(20)` methods are shown once per expanded invocation (`[1]` through `[20]`) in the final XML, yielding 63 executed cases.

## Complete final Surefire inventory (all 506 testcases)

| # | Class | Method | Category | Status | Duration (s) |
|---:|---|---|---|---|---:|
| 1 | com.taxi.backend.config.ClockWiringContextTest | productionClockConsumersUseTheirNamedClocksWhenBothBeansExist | unit | PASS | 3.853 |
| 2 | com.taxi.backend.config.WebSocketAuthInterceptorTest | allowsAdministratorOnlyForKnownAdminOrResourceDestinations | auth-kyc | PASS | 0.015 |
| 3 | com.taxi.backend.config.WebSocketAuthInterceptorTest | allowsEveryCurrentMobileSubscriptionOnlyForItsAuthorizedRecipient | auth-kyc | PASS | 0.022 |
| 4 | com.taxi.backend.config.WebSocketAuthInterceptorTest | connectRejectsRefreshTokensAndStaleRoles | auth-kyc | PASS | 0.432 |
| 5 | com.taxi.backend.config.WebSocketAuthInterceptorTest | connectUsesDatabaseRoleForAuthenticatedSession | auth-kyc | PASS | 0.0 |
| 6 | com.taxi.backend.config.WebSocketAuthInterceptorTest | deniesForeignAndMalformedOrUnsupportedSubscriptionsBeforeTheBroker | auth-kyc | PASS | 0.016 |
| 7 | com.taxi.backend.config.WebSocketAuthInterceptorTest | unauthenticatedSubscriptionIsDeniedBeforeAnyRepositoryLookup | auth-kyc | PASS | 0.0 |
| 8 | com.taxi.backend.config.WebSocketSubscriptionChannelIntegrationTest | unknownTopicNeverReachesBrokerButAuthorizedDriverSubscriptionDoes | ao-dispatch | PASS | 0.033 |
| 9 | com.taxi.backend.controller.AdminOtpMonitorContainmentTest | otpMonitorRequiresAdmin | unit | PASS | 0.0 |
| 10 | com.taxi.backend.controller.AdminOtpMonitorContainmentTest | otpMonitorReturnsMaskedMetadataWithoutLiveCode | unit | PASS | 0.857 |
| 11 | com.taxi.backend.controller.AuthControllerContainmentTest | anonymousDriverRegistrationIsDenied | auth-kyc | PASS | 0.02 |
| 12 | com.taxi.backend.controller.AuthControllerContainmentTest | blacklistedRefreshCannotMintAccessToken | auth-kyc | PASS | 0.0 |
| 13 | com.taxi.backend.controller.AuthControllerContainmentTest | driverRegistrationUsesAuthenticatedOwnerAndIgnoresBodyPhone | auth-kyc | PASS | 0.0 |
| 14 | com.taxi.backend.controller.AuthControllerContainmentTest | passportResponseIsBoundToDriverAndOmitsPinfl | auth-kyc | PASS | 0.016 |
| 15 | com.taxi.backend.controller.AuthControllerContainmentTest | publicOtpCannotSelectPrivilegedRoles | auth-kyc | PASS | 0.116 |
| 16 | com.taxi.backend.controller.AuthControllerContainmentTest | vehicleResponseOmitsOwnerPiiAndDoesNotUseFixtureFallback | auth-kyc | PASS | 0.419 |
| 17 | com.taxi.backend.controller.ClickGetInfoSecurityTest | chunkedStyleOversizedBodyIsBoundedEvenWithoutADeclaredContentLength | payment | PASS | 0.012 |
| 18 | com.taxi.backend.controller.ClickGetInfoSecurityTest | confirmedBusinessFailuresReturnHttp200WithOnlyErrorAndErrorNote | payment | PASS | 0.409 |
| 19 | com.taxi.backend.controller.ClickGetInfoSecurityTest | localTransportFallbacksUseOnlyTheProtocolErrorSchemaPendingClickAcceptance | payment | PASS | 0.027 |
| 20 | com.taxi.backend.controller.ClickGetInfoSecurityTest | onlyTypedGetInfoIsPublicAndOldShopMoneyRouteIsDenied | payment | PASS | 0.034 |
| 21 | com.taxi.backend.controller.PassengerNearbyContainmentTest | nearbyMapNeverReturnsExactDriverCoordinates | location-privacy | PASS | 0.281 |
| 22 | com.taxi.backend.controller.PaymentSecurityTest | clickCallbacksAcceptUnauthenticatedFormPostsWithoutRedirect | payment | PASS | 0.018 |
| 23 | com.taxi.backend.controller.PaymentSecurityTest | paymentCreationAndDriverOrderStatusRemainProtectedWithoutJwt | payment | PASS | 0.015 |
| 24 | com.taxi.backend.dto.BookTripRequestTest | distanceTooLarge_shouldViolate | unit | PASS | 0.015 |
| 25 | com.taxi.backend.dto.BookTripRequestTest | invalidLatitude_shouldViolate | unit | PASS | 0.013 |
| 26 | com.taxi.backend.dto.BookTripRequestTest | invalidLongitude_shouldViolate | unit | PASS | 0.016 |
| 27 | com.taxi.backend.dto.BookTripRequestTest | invalidPromoCodePattern_shouldViolate | unit | PASS | 0.018 |
| 28 | com.taxi.backend.dto.BookTripRequestTest | missingFromAddress_shouldViolate | unit | PASS | 0.016 |
| 29 | com.taxi.backend.dto.BookTripRequestTest | missingTariffId_shouldViolate | unit | PASS | 0.198 |
| 30 | com.taxi.backend.dto.BookTripRequestTest | validRequest_shouldHaveNoViolations | unit | PASS | 0.015 |
| 31 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleBusiness_shouldReturn400 | unit | PASS | 0.0 |
| 32 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleForbidden_shouldReturn403 | unit | PASS | 0.016 |
| 33 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleGeneral_shouldReturn500 | unit | PASS | 0.117 |
| 34 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleNotFound_shouldReturn404 | unit | PASS | 0.0 |
| 35 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleRateLimit_shouldReturn429 | unit | PASS | 0.0 |
| 36 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleRuntime_alreadyCancelled_shouldReturn400 | unit | PASS | 0.0 |
| 37 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleRuntime_alreadyCompleted_shouldReturn400_not500 | unit | PASS | 0.002 |
| 38 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleRuntime_busy_shouldReturn400 | unit | PASS | 0.0 |
| 39 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleRuntime_cooldown_shouldReturn400 | unit | PASS | 0.0 |
| 40 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleRuntime_excludedDriver_shouldReturn400 | unit | PASS | 0.0 |
| 41 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleRuntime_genericAllaqachon_stillReturns400_notBrokenBy409 | unit | PASS | 0.0 |
| 42 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleRuntime_illegalTransition_shouldReturn400 | unit | PASS | 0.001 |
| 43 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleRuntime_negativeBalance_shouldReturn400 | unit | PASS | 0.0 |
| 44 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleRuntime_notFoundPattern_shouldReturn404 | unit | PASS | 0.002 |
| 45 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleRuntime_notYourTrip_shouldReturn400_not500 | unit | PASS | 0.0 |
| 46 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleRuntime_nullMessage_shouldReturn500 | unit | PASS | 0.016 |
| 47 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleRuntime_otpError_shouldReturn400 | unit | PASS | 0.0 |
| 48 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleRuntime_serviceMismatch_shouldReturn400 | unit | PASS | 0.0 |
| 49 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleRuntime_tariffMismatch_shouldReturn400 | unit | PASS | 0.01 |
| 50 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleRuntime_tripAlreadyTakenOnAccept_shouldReturn409 | unit | PASS | 0.0 |
| 51 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleRuntime_tripAlreadyTakenOnClaim_shouldReturn409 | unit | PASS | 0.0 |
| 52 | com.taxi.backend.exception.GlobalExceptionHandlerTest | handleRuntime_unknownError_shouldReturn500 | unit | PASS | 0.003 |
| 53 | com.taxi.backend.pricing.NightFareServiceTest | applyToBaseAtCreation_storedUtcConvertedToTashkent | unit | PASS | 0.0 |
| 54 | com.taxi.backend.pricing.NightFareServiceTest | applyToBaseNow_atDay_unchanged | unit | PASS | 0.0 |
| 55 | com.taxi.backend.pricing.NightFareServiceTest | applyToBaseNow_atNight_addsSurchargeOnce_allTariffs | unit | PASS | 0.0 |
| 56 | com.taxi.backend.pricing.NightFareServiceTest | configurableWindow_respectsStartAndEnd | unit | PASS | 0.0 |
| 57 | com.taxi.backend.pricing.NightFareServiceTest | isNightAt_createdAt_boundaries | unit | PASS | 0.0 |
| 58 | com.taxi.backend.pricing.NightFareServiceTest | isNightAt_null_isNotNight | unit | PASS | 0.0 |
| 59 | com.taxi.backend.pricing.NightFareServiceTest | isNightNow_tashkent_0000_isNight | unit | PASS | 0.0 |
| 60 | com.taxi.backend.pricing.NightFareServiceTest | isNightNow_tashkent_0559_isNight | unit | PASS | 0.0 |
| 61 | com.taxi.backend.pricing.NightFareServiceTest | isNightNow_tashkent_0600_isNotNight | unit | PASS | 0.0 |
| 62 | com.taxi.backend.pricing.NightFareServiceTest | isNightNow_tashkent_2359_isNotNight | unit | PASS | 0.016 |
| 63 | com.taxi.backend.pricing.NightFareServiceTest | isNightNow_tashkent_noon_isNotNight | unit | PASS | 0.0 |
| 64 | com.taxi.backend.pricing.NightFareServiceTest | nightDecisionIsZoneIndependent_wouldFailUnderJvmLocalNow | unit | PASS | 0.016 |
| 65 | com.taxi.backend.pricing.NightFareServiceTest | surcharge_amountAndUnit | unit | PASS | 0.0 |
| 66 | com.taxi.backend.schema.SchemaMigrationBootstrapTest | flywayChainAppliesFromScratch_throughV50_includingV49 | migration | PASS | 0.023 |
| 67 | com.taxi.backend.schema.SchemaMigrationBootstrapTest | systemSettingEntityMapsToMigratedAppSettings | migration | PASS | 0.113 |
| 68 | com.taxi.backend.schema.SchemaMigrationBootstrapTest | v47AddsDurableIdempotencyAndTheImmediateTripGuard | migration | PASS | 0.027 |
| 69 | com.taxi.backend.schema.SchemaMigrationBootstrapTest | v48RequestHashColumnAndEntityMappingRemainAligned | migration | PASS | 0.373 |
| 70 | com.taxi.backend.schema.SchemaMigrationBootstrapTest | v49AddsTheSingleLiveOfferDatabaseConstraint | migration | PASS | 0.017 |
| 71 | com.taxi.backend.schema.SchemaMigrationBootstrapTest | v50CatalogColumnsConstraintsIndexesAndEntityMappingsRemainAligned | migration | PASS | 0.063 |
| 72 | com.taxi.backend.security.JwtFilterContainmentTest | currentDatabaseRoleIsTheGrantedAuthority | auth-kyc | PASS | 0.098 |
| 73 | com.taxi.backend.security.JwtFilterContainmentTest | staleJwtRoleCannotUseCurrentDatabaseIdentity | auth-kyc | PASS | 0.018 |
| 74 | com.taxi.backend.security.JwtServiceTest | extractExpiration_shouldBeInFuture | auth-kyc | PASS | 0.0 |
| 75 | com.taxi.backend.security.JwtServiceTest | extractJti_shouldReturnUniqueId | auth-kyc | PASS | 0.193 |
| 76 | com.taxi.backend.security.JwtServiceTest | generateRefreshToken_shouldHaveRefreshType | auth-kyc | PASS | 0.0 |
| 77 | com.taxi.backend.security.JwtServiceTest | generateToken_shouldCreateValidAccessToken | auth-kyc | PASS | 0.006 |
| 78 | com.taxi.backend.security.JwtServiceTest | isValid_shouldReturnFalseForTamperedToken | auth-kyc | PASS | 0.0 |
| 79 | com.taxi.backend.security.OperatorSecurityTest | operatorRoleIsDistinct | auth-kyc | PASS | 0.016 |
| 80 | com.taxi.backend.security.OperatorSecurityTest | roleEnumHasOperator | auth-kyc | PASS | 0.001 |
| 81 | com.taxi.backend.security.OperatorSecurityTest | roleValueOfWorks | auth-kyc | PASS | 0.0 |
| 82 | com.taxi.backend.security.OperatorSecurityTest | tripRequest_addressValidation | auth-kyc | PASS | 0.0 |
| 83 | com.taxi.backend.security.OperatorSecurityTest | tripRequest_phoneValidation | auth-kyc | PASS | 0.001 |
| 84 | com.taxi.backend.security.OperatorSecurityTest | tripSourceShouldBeCall | auth-kyc | PASS | 0.001 |
| 85 | com.taxi.backend.security.PenetrationTest | bruteForce_differentIpsShouldBeIndependent | auth-kyc | PASS | 0.0 |
| 86 | com.taxi.backend.security.PenetrationTest | bruteForce_shouldBlockAfterLimit | auth-kyc | PASS | 0.002 |
| 87 | com.taxi.backend.security.PenetrationTest | ddos_globalLimitShouldWork | auth-kyc | PASS | 0.004 |
| 88 | com.taxi.backend.security.PenetrationTest | inputValidation_chatMessageLength | auth-kyc | PASS | 0.0 |
| 89 | com.taxi.backend.security.PenetrationTest | inputValidation_promoCodePattern | auth-kyc | PASS | 0.001 |
| 90 | com.taxi.backend.security.PenetrationTest | jwtExpired_shouldBeRejected | auth-kyc | PASS | 0.006 |
| 91 | com.taxi.backend.security.PenetrationTest | jwtTamper_modifiedTokenShouldBeRejected | auth-kyc | PASS | 0.006 |
| 92 | com.taxi.backend.security.PenetrationTest | rateLimit_bookingPerUser | auth-kyc | PASS | 0.002 |
| 93 | com.taxi.backend.security.PenetrationTest | rateLimit_perIp | auth-kyc | PASS | 0.002 |
| 94 | com.taxi.backend.security.PenetrationTest | rateLimiter_cleanupWorks | auth-kyc | PASS | 0.002 |
| 95 | com.taxi.backend.security.PenetrationTest | sqlInjection_otpValidation | auth-kyc | PASS | 0.001 |
| 96 | com.taxi.backend.security.PenetrationTest | sqlInjection_phoneValidation | auth-kyc | PASS | 0.002 |
| 97 | com.taxi.backend.service.ActivityScoreTest | cancelBoshqa_decrementsByPointTwo | unit | PASS | 0.016 |
| 98 | com.taxi.backend.service.ActivityScoreTest | cancelExcused_noChange | unit | PASS | 0.0 |
| 99 | com.taxi.backend.service.ActivityScoreTest | completion_incrementsActivityScoreByOne | unit | PASS | 0.685 |
| 100 | com.taxi.backend.service.ActivityScoreTest | decline_noChange | unit | PASS | 0.007 |
| 101 | com.taxi.backend.service.AdminBroadcastSpecificTest | legacy4argOverload_stillWorks | unit | PASS | 0.27 |
| 102 | com.taxi.backend.service.AdminBroadcastSpecificTest | targetAll_globalTopic_noTargetIds | unit | PASS | 0.0 |
| 103 | com.taxi.backend.service.AdminBroadcastSpecificTest | targetDriverAlias_normalizesToSpecific | unit | PASS | 0.013 |
| 104 | com.taxi.backend.service.AdminBroadcastSpecificTest | targetSpecific_missingDriverId_throws | unit | PASS | 0.005 |
| 105 | com.taxi.backend.service.AdminBroadcastSpecificTest | targetSpecific_singleDriverTopic_storesTargetIds | unit | PASS | 0.0 |
| 106 | com.taxi.backend.service.AdminBroadcastSpecificTest | targetSpecific_unknownDriver_throws | unit | PASS | 0.01 |
| 107 | com.taxi.backend.service.AdminDashboardStatsTest | commissionStats_convertsTiiyinToUzs | unit | PASS | 0.016 |
| 108 | com.taxi.backend.service.AdminDashboardStatsTest | topupStats_sumAmountAndDistinctDriversAndCashCardSplit | unit | PASS | 0.0 |
| 109 | com.taxi.backend.service.AdminOnlineStatusTest | setDriverOffline_whenHasActiveTrip_throwsConflictException | unit | PASS | 0.0 |
| 110 | com.taxi.backend.service.AdminOnlineStatusTest | setDriverOffline_whenNoActiveTrip_succeeds | unit | PASS | 0.0 |
| 111 | com.taxi.backend.service.AdminOnlineStatusTest | setDriverOnline_whenCurrentlyOffline_succeeds | unit | PASS | 0.017 |
| 112 | com.taxi.backend.service.AdminPhotoDeleteTest | adminCanDeleteDriverFace | unit | PASS | 0.0 |
| 113 | com.taxi.backend.service.AdminPhotoDeleteTest | adminCanDeleteSelfie | unit | PASS | 0.0 |
| 114 | com.taxi.backend.service.AdminServiceLiniyadaTest | getLiniyadaCount_returnsOnlineActiveDriverCount | unit | PASS | 0.008 |
| 115 | com.taxi.backend.service.AdminServiceLiniyadaTest | getLiniyadaCount_returnsZeroWhenNoneOnline | unit | PASS | 0.006 |
| 116 | com.taxi.backend.service.AdminTopupPaymentMethodTest | topupWithCard_storesPaymentMethodCard | payment | PASS | 0.006 |
| 117 | com.taxi.backend.service.AdminTopupPaymentMethodTest | topupWithCash_storesPaymentMethodCash | payment | PASS | 0.002 |
| 118 | com.taxi.backend.service.AdminTripMgmtServiceTest | changeTariff_nonSearching_throws | unit | PASS | 0.0 |
| 119 | com.taxi.backend.service.AdminTripMgmtServiceTest | changeTariff_searching_recomputesBaseAndTotal | unit | PASS | 0.018 |
| 120 | com.taxi.backend.service.AdminTripMgmtServiceTest | editAddresses_blankFromAddress_throws | unit | PASS | 0.012 |
| 121 | com.taxi.backend.service.AdminTripMgmtServiceTest | editAddresses_nonSearching_throws | unit | PASS | 0.0 |
| 122 | com.taxi.backend.service.AdminTripMgmtServiceTest | editAddresses_searching_updatesAllFields | unit | PASS | 0.0 |
| 123 | com.taxi.backend.service.AdminTripMgmtServiceTest | reassign_happyPath_callsDataOnlyDispatch | unit | PASS | 0.005 |
| 124 | com.taxi.backend.service.AdminTripMgmtServiceTest | reassign_nonActiveDriver_noDispatch | unit | PASS | 0.017 |
| 125 | com.taxi.backend.service.AdminTripMgmtServiceTest | reassign_nonSearching_noDispatch | unit | PASS | 0.0 |
| 126 | com.taxi.backend.service.AdvancedShopServiceTest | canonicalIdentifierKey_isAccount | unit | PASS | 0.0 |
| 127 | com.taxi.backend.service.AdvancedShopServiceTest | complete_badSign_returnsMinusOne_noCredit | unit | PASS | 0.001 |
| 128 | com.taxi.backend.service.AdvancedShopServiceTest | complete_duplicateCall_isIdempotent_noDoubleCredit | unit | PASS | 0.004 |
| 129 | com.taxi.backend.service.AdvancedShopServiceTest | complete_firstCallCreditsOnce | unit | PASS | 0.0 |
| 130 | com.taxi.backend.service.AdvancedShopServiceTest | complete_unknownDriver_returnsFailedStatus_noCredit_noConfirmedLedger | unit | PASS | 0.001 |
| 131 | com.taxi.backend.service.AdvancedShopServiceTest | concatParamValues_preservesInsertionOrder_andSkipsNullsAsEmpty | unit | PASS | 0.001 |
| 132 | com.taxi.backend.service.AdvancedShopServiceTest | getinfo_accountKeyWins_overOtherCandidates | unit | PASS | 0.002 |
| 133 | com.taxi.backend.service.AdvancedShopServiceTest | getinfo_activeDriver_returnsFio | unit | PASS | 0.101 |
| 134 | com.taxi.backend.service.AdvancedShopServiceTest | getinfo_badSign_returnsMinusOne_andDoesNotHitRepo | unit | PASS | 0.001 |
| 135 | com.taxi.backend.service.AdvancedShopServiceTest | getinfo_blockedDriver_returnsMinusFive | unit | PASS | 0.001 |
| 136 | com.taxi.backend.service.AdvancedShopServiceTest | getinfo_missingIdentifier_returnsMinusEight | unit | PASS | 0.0 |
| 137 | com.taxi.backend.service.AdvancedShopServiceTest | getinfo_signed_stillVerifies_whenSignPresent | unit | PASS | 0.0 |
| 138 | com.taxi.backend.service.AdvancedShopServiceTest | getinfo_suspendedDriver_returnsMinusFive | unit | PASS | 0.0 |
| 139 | com.taxi.backend.service.AdvancedShopServiceTest | getinfo_unknownCode_returnsMinusFive | unit | PASS | 0.002 |
| 140 | com.taxi.backend.service.AdvancedShopServiceTest | getinfo_unsigned_matchingServiceId_returnsFio | unit | PASS | 0.002 |
| 141 | com.taxi.backend.service.AdvancedShopServiceTest | getinfo_unsigned_missingServiceId_returnsMinusOne | unit | PASS | 0.001 |
| 142 | com.taxi.backend.service.AdvancedShopServiceTest | getinfo_unsigned_secretUnset_returnsMinusOne_failClosed | unit | PASS | 0.0 |
| 143 | com.taxi.backend.service.AdvancedShopServiceTest | getinfo_unsigned_wrongServiceId_returnsMinusOne | unit | PASS | 0.004 |
| 144 | com.taxi.backend.service.AdvancedShopServiceTest | getinfo_usesEagerFetch_soLazyUserProxyNeverInitializedOutsideSession | unit | PASS | 0.103 |
| 145 | com.taxi.backend.service.AdvancedShopServiceTest | parseAmountTiyin_handlesDecimalSom | unit | PASS | 0.0 |
| 146 | com.taxi.backend.service.AdvancedShopServiceTest | parseAmountTiyin_rejectsGarbage | unit | PASS | 0.0 |
| 147 | com.taxi.backend.service.AdvancedShopServiceTest | prepare_badSign_returnsMinusOne_noLedgerWrite | unit | PASS | 0.001 |
| 148 | com.taxi.backend.service.AdvancedShopServiceTest | prepare_missingAmount_returnsMinusEight_andNoLedgerWrite | unit | PASS | 0.002 |
| 149 | com.taxi.backend.service.AdvancedShopServiceTest | prepare_unsigned_stillRejected_moneyPathStaysStrict | unit | PASS | 0.001 |
| 150 | com.taxi.backend.service.AdvancedShopServiceTest | prepare_validSign_activeDriver_returnsSuccessAndInsertsLedger | unit | PASS | 0.003 |
| 151 | com.taxi.backend.service.AdvancedShopServiceTest | signatureFormulaLocked_paramsValuesInInsertionOrder | unit | PASS | 0.013 |
| 152 | com.taxi.backend.service.AdvancedShopServiceTest | unknownAction_returnsMinusThree | unit | PASS | 0.002 |
| 153 | com.taxi.backend.service.AdvancedShopServiceTest | verifySignature_acceptsCorrectSign_rejectsTampered | unit | PASS | 0.0 |
| 154 | com.taxi.backend.service.AdvancedShopServiceTest | verifySignature_refusesWhenSecretMissing | unit | PASS | 0.0 |
| 155 | com.taxi.backend.service.AuthServiceContainmentTest | nonDriverCannotRegisterDriverProfile | auth-kyc | PASS | 0.139 |
| 156 | com.taxi.backend.service.AuthServiceContainmentTest | replayedDriverRegistrationIsRejectedBeforeMutation | auth-kyc | PASS | 0.018 |
| 157 | com.taxi.backend.service.AuthServiceContainmentTest | serviceRejectsPrivilegedRoleEvenWhenCalledOutsideController | auth-kyc | PASS | 0.0 |
| 158 | com.taxi.backend.service.AuthServiceTest | sendOtp_shouldCreateAndSaveOtp | auth-kyc | PASS | 0.004 |
| 159 | com.taxi.backend.service.AuthServiceTest | verifyOtp_testCodeBlockedInProduction | auth-kyc | PASS | 0.004 |
| 160 | com.taxi.backend.service.AuthServiceTest | verifyOtp_testCodeWorksInLocalProfile | auth-kyc | PASS | 0.007 |
| 161 | com.taxi.backend.service.AuthServiceTest | verifyOtp_withExpiredCode_shouldThrow | auth-kyc | PASS | 0.004 |
| 162 | com.taxi.backend.service.AuthServiceTest | verifyOtp_withValidCode_shouldReturnTokens | auth-kyc | PASS | 0.0 |
| 163 | com.taxi.backend.service.AuthServiceTest | verifyOtp_withWrongCode_shouldThrow | auth-kyc | PASS | 0.014 |
| 164 | com.taxi.backend.service.BannerImageTest | servePublicBannerImage_cannotReachDriverPhotos_evenWithEncodedTraversal(Path) | unit | PASS | 0.006 |
| 165 | com.taxi.backend.service.BannerImageTest | servePublicBannerImage_dotDotTraversal_rejectedBadRequest(Path) | unit | PASS | 0.0 |
| 166 | com.taxi.backend.service.BannerImageTest | servePublicBannerImage_existingFile_returns200WithBytes(Path) | unit | PASS | 0.006 |
| 167 | com.taxi.backend.service.BannerImageTest | servePublicBannerImage_missingFile_returns404(Path) | unit | PASS | 0.002 |
| 168 | com.taxi.backend.service.BannerImageTest | uploadBannerImage_fakeMagicBytes_rejected(Path) | unit | PASS | 0.02 |
| 169 | com.taxi.backend.service.BannerImageTest | uploadBannerImage_validJpeg_storesUnderBannersDirAndReturnsPublicUrl(Path) | unit | PASS | 0.0 |
| 170 | com.taxi.backend.service.BannerImageTest | uploadBannerImage_wrongExtension_rejected(Path) | unit | PASS | 0.003 |
| 171 | com.taxi.backend.service.BroadcastBalanceGateTest | generalBoardIsDisabledRegardlessOfBalance | wallet-ledger | PASS | 0.0 |
| 172 | com.taxi.backend.service.BroadcastBusyGuardTest | sharedBoardClaimIsAlwaysRejected | unit | PASS | 0.0 |
| 173 | com.taxi.backend.service.BroadcastClaimExclusionTest | noDriverCanBypassOfferOwnershipThroughLegacyClaimEndpoint | unit | PASS | 0.0 |
| 174 | com.taxi.backend.service.BroadcastCooldownTest | cooldownCannotBecomeAnAlternateSharedBoardAcceptancePath | unit | PASS | 0.0 |
| 175 | com.taxi.backend.service.BusyDriverGuardTest | acceptTrip_whenDriverBusy_rejected | unit | PASS | 0.0 |
| 176 | com.taxi.backend.service.BusyDriverGuardTest | acceptTrip_whenDriverFree_succeeds | unit | PASS | 0.016 |
| 177 | com.taxi.backend.service.BusyDriverGuardTest | getAvailableTrips_whenBusy_returnsEmpty | unit | PASS | 0.022 |
| 178 | com.taxi.backend.service.ChannelMessageServiceTest | sendToChannel_activeAudience_usesOnlineDrivers | unit | PASS | 0.002 |
| 179 | com.taxi.backend.service.ChannelMessageServiceTest | sendToChannel_driverTarget_missingId_throws | unit | PASS | 0.101 |
| 180 | com.taxi.backend.service.ChannelMessageServiceTest | sendToChannel_driverTarget_singleDriverFanout | unit | PASS | 0.0 |
| 181 | com.taxi.backend.service.ChannelMessageServiceTest | sendToChannel_driverTarget_unknownDriver_throws | unit | PASS | 0.003 |
| 182 | com.taxi.backend.service.ChannelMessageServiceTest | sendToChannel_fansOutToAllDrivers | unit | PASS | 0.003 |
| 183 | com.taxi.backend.service.ChannelMessageServiceTest | sendToChannel_legacy5argOverload_stillWorks | unit | PASS | 0.003 |
| 184 | com.taxi.backend.service.ChannelMessageServiceTest | sendToChannel_rejectsTexnikYordam | unit | PASS | 0.0 |
| 185 | com.taxi.backend.service.ChannelMessageServiceTest | unreadByChannel_fillsAllBroadcastChannels | unit | PASS | 0.003 |
| 186 | com.taxi.backend.service.ClickCatalogPaymentServiceTest | completeClaimsOnceAndCreditsSharedWalletExactlyOnce | payment | PASS | 0.001 |
| 187 | com.taxi.backend.service.ClickCatalogPaymentServiceTest | drainRejectsNewPrepareButSettlesOnlyAnExistingPreparedTransaction | payment | PASS | 0.017 |
| 188 | com.taxi.backend.service.ClickCatalogPaymentServiceTest | duplicatePrepareReturnsOriginalPrepareIdAndDistinctCatalogPaymentIsIndependent | payment | PASS | 0.18 |
| 189 | com.taxi.backend.service.ClickCatalogPaymentServiceTest | failedCompleteInvalidAmountAndMissingPreparedIntentNeverCredit | payment | PASS | 0.002 |
| 190 | com.taxi.backend.service.ClickCatalogPaymentServiceTest | offBlocksNewPrepareAndCompleteWithoutAnyWalletMutation | payment | PASS | 0.001 |
| 191 | com.taxi.backend.service.ClickCatalogPaymentServiceTest | prepareCreatesDurableCatalogIntentButNeverCredits | payment | PASS | 0.001 |
| 192 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | confirmationFailureRollsBackBeforeAnyWalletMutation | postgres-concurrency | PASS | 0.07 |
| 193 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | ledgerInsertFailureRollsBackClaimBalanceAndLedgerTogether | postgres-concurrency | PASS | 0.063 |
| 194 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | offAndDrainRespectDurableCatalogStateWithoutTouchingNewMoney | postgres-concurrency | PASS | 0.078 |
| 195 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | realSpringProxiesUseOneTransactionForPrepareCompleteAndWalletCredit | postgres-concurrency | PASS | 0.106 |
| 196 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | tenDistinctCatalogPaymentsShareOneDriverWithoutLosingLedgerSnapshots | postgres-concurrency | PASS | 0.299 |
| 197 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[1] | postgres-concurrency | PASS | 0.175 |
| 198 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[10] | postgres-concurrency | PASS | 0.138 |
| 199 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[11] | postgres-concurrency | PASS | 0.152 |
| 200 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[12] | postgres-concurrency | PASS | 0.139 |
| 201 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[13] | postgres-concurrency | PASS | 0.141 |
| 202 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[14] | postgres-concurrency | PASS | 0.13 |
| 203 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[15] | postgres-concurrency | PASS | 0.132 |
| 204 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[16] | postgres-concurrency | PASS | 0.124 |
| 205 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[17] | postgres-concurrency | PASS | 0.11 |
| 206 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[18] | postgres-concurrency | PASS | 0.12 |
| 207 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[19] | postgres-concurrency | PASS | 0.124 |
| 208 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[2] | postgres-concurrency | PASS | 0.211 |
| 209 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[20] | postgres-concurrency | PASS | 0.121 |
| 210 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[3] | postgres-concurrency | PASS | 0.196 |
| 211 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[4] | postgres-concurrency | PASS | 0.19 |
| 212 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[5] | postgres-concurrency | PASS | 0.185 |
| 213 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[6] | postgres-concurrency | PASS | 0.178 |
| 214 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[7] | postgres-concurrency | PASS | 0.158 |
| 215 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[8] | postgres-concurrency | PASS | 0.232 |
| 216 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices()[9] | postgres-concurrency | PASS | 0.164 |
| 217 | com.taxi.backend.service.ClickCatalogPostgresConcurrencyTest | uniqueLedgerConflictRollsBackConfirmationAndBalance | postgres-concurrency | PASS | 0.293 |
| 218 | com.taxi.backend.service.ClickGetInfoServiceTest | basicAuthFailsClosedWhenMissingInvalidOrNotConfigured | payment | PASS | 0.002 |
| 219 | com.taxi.backend.service.ClickGetInfoServiceTest | boundedRateWindowEvictsOldSourcesInsteadOfBecomingAGlobalBan | payment | PASS | 0.006 |
| 220 | com.taxi.backend.service.ClickGetInfoServiceTest | drainOffAndUnknownModesFailClosedBeforeAnyDriverLookup | payment | PASS | 0.002 |
| 221 | com.taxi.backend.service.ClickGetInfoServiceTest | endpointGateAndRateLimitRemainFailClosed | payment | PASS | 0.004 |
| 222 | com.taxi.backend.service.ClickGetInfoServiceTest | malformedUnknownInactiveWrongServiceAndWrongActionAreRejected | payment | PASS | 0.003 |
| 223 | com.taxi.backend.service.ClickGetInfoServiceTest | missingParamsAndAccountAreRejectedBeforeLookup | payment | PASS | 0.0 |
| 224 | com.taxi.backend.service.ClickGetInfoServiceTest | oneClickPeerCanResolveDifferentAccountsWithoutUsingThePerAccountBudget | payment | PASS | 0.008 |
| 225 | com.taxi.backend.service.ClickGetInfoServiceTest | temporaryBusinessFailureUsesOnlyTheConfirmedBusinessErrorSchema | payment | PASS | 0.002 |
| 226 | com.taxi.backend.service.ClickGetInfoServiceTest | unconfirmedAuthContractFailsClosedBeforeAnyDriverLookup | payment | PASS | 0.001 |
| 227 | com.taxi.backend.service.ClickGetInfoServiceTest | validAccountNormalizesAndReturnsOnlyFullNameWithoutMutation | payment | PASS | 0.003 |
| 228 | com.taxi.backend.service.ClickMerchantTransIdTypeTest | nearMissesNeverFallBackAcrossFlows | payment | PASS | 0.001 |
| 229 | com.taxi.backend.service.ClickMerchantTransIdTypeTest | onlyTheTwoDocumentedNonOverlappingFormsAreClassified | payment | PASS | 0.001 |
| 230 | com.taxi.backend.service.ClickPaymentTest | clickOrderStatus_onlyReturnsTheAuthenticatedDriversOrder | payment | PASS | 0.291 |
| 231 | com.taxi.backend.service.ClickPaymentTest | complete_amountMismatch_returnsMinus2_noCredit | payment | PASS | 0.008 |
| 232 | com.taxi.backend.service.ClickPaymentTest | complete_amountWithUnsupportedScale_returnsMinus2_noCredit | payment | PASS | 0.016 |
| 233 | com.taxi.backend.service.ClickPaymentTest | complete_anyNonZeroError_isCancelledAndNeverCredits | payment | PASS | 0.009 |
| 234 | com.taxi.backend.service.ClickPaymentTest | complete_emptyErrorNoteIsAcceptedWhenAllOtherFieldsAreValid | payment | PASS | 0.008 |
| 235 | com.taxi.backend.service.ClickPaymentTest | complete_failedPayment_isCancelledAndNeverCredits | payment | PASS | 0.0 |
| 236 | com.taxi.backend.service.ClickPaymentTest | complete_missingMerchantPrepareId_rejectedBeforeAnyStateMutation | payment | PASS | 0.002 |
| 237 | com.taxi.backend.service.ClickPaymentTest | complete_redisOutage_stillCreditsExactlyOnce_viaDbClaim | payment | PASS | 0.014 |
| 238 | com.taxi.backend.service.ClickPaymentTest | complete_replayedClickTransId_doesNotDoubleCredit | payment | PASS | 0.009 |
| 239 | com.taxi.backend.service.ClickPaymentTest | complete_tamperedSign_rejected_noCredit | payment | PASS | 0.009 |
| 240 | com.taxi.backend.service.ClickPaymentTest | complete_unknownOrder_returnsMinus5 | payment | PASS | 0.012 |
| 241 | com.taxi.backend.service.ClickPaymentTest | complete_validSign_accepted_andCredits | payment | PASS | 0.016 |
| 242 | com.taxi.backend.service.ClickPaymentTest | complete_withoutSuccessfulPrepare_rejectedAndNeverCredits | payment | PASS | 0.0 |
| 243 | com.taxi.backend.service.ClickPaymentTest | complete_wrongAction_rejectedBeforeAnyStateMutation | payment | PASS | 0.006 |
| 244 | com.taxi.backend.service.ClickPaymentTest | complete_wrongPrepareId_rejectedAndNeverCredits | payment | PASS | 0.012 |
| 245 | com.taxi.backend.service.ClickPaymentTest | complete_wrongServiceId_rejectedBeforeAnyStateMutation | payment | PASS | 0.007 |
| 246 | com.taxi.backend.service.ClickPaymentTest | createOrder_keepsTheProvenService105926PaymentLinkContract | payment | PASS | 0.014 |
| 247 | com.taxi.backend.service.ClickPaymentTest | createOrder_persistsDurableClickIntentBeforeReturningUrl | payment | PASS | 0.015 |
| 248 | com.taxi.backend.service.ClickPaymentTest | prepare_amountMismatch_rejected | payment | PASS | 0.005 |
| 249 | com.taxi.backend.service.ClickPaymentTest | prepare_duplicateRetry_isDeterministicAndIdempotent | payment | PASS | 0.009 |
| 250 | com.taxi.backend.service.ClickPaymentTest | prepare_missingRequiredClickPaydocId_rejected | payment | PASS | 0.006 |
| 251 | com.taxi.backend.service.ClickPaymentTest | prepare_tamperedSign_rejected | payment | PASS | 0.0 |
| 252 | com.taxi.backend.service.ClickPaymentTest | prepare_unknownOrder_returnsMinus5 | payment | PASS | 0.0 |
| 253 | com.taxi.backend.service.ClickPaymentTest | prepare_usesDurableOrderWhenRedisIsUnavailable | payment | PASS | 0.009 |
| 254 | com.taxi.backend.service.ClickPaymentTest | prepare_validSign_accepted | payment | PASS | 0.009 |
| 255 | com.taxi.backend.service.ClickPaymentTest | prepare_wrongAction_rejected | payment | PASS | 0.008 |
| 256 | com.taxi.backend.service.ClickPaymentTest | prepare_wrongServiceId_rejectedBeforeLedgerMutation | payment | PASS | 0.006 |
| 257 | com.taxi.backend.service.ClickPostgresConcurrencyTest | concurrentCompleteClaimsExactlyOneCredit | postgres-concurrency | PASS | 0.09 |
| 258 | com.taxi.backend.service.ClickPostgresConcurrencyTest | concurrentDistinctCreditsFormOneAuthoritativeLedgerChain | postgres-concurrency | PASS | 0.086 |
| 259 | com.taxi.backend.service.ClickPostgresConcurrencyTest | rollbackAfterBalanceMutationLeavesNoBalanceOrLedgerChange | postgres-concurrency | PASS | 0.074 |
| 260 | com.taxi.backend.service.ClickPostgresConcurrencyTest | rollbackAfterLedgerCreationLeavesNoBalanceOrLedgerChange | postgres-concurrency | PASS | 0.189 |
| 261 | com.taxi.backend.service.CommitSafeStatusTest | arrived_pushThrows_statusStillChanges | unit | PASS | 0.008 |
| 262 | com.taxi.backend.service.CommitSafeStatusTest | complete_broadcastThrows_stillCommitsCompleted | unit | PASS | 0.016 |
| 263 | com.taxi.backend.service.CommitSafeStatusTest | complete_normal_correctCommission | unit | PASS | 0.016 |
| 264 | com.taxi.backend.service.CommitSafeStatusTest | complete_nullTotalPrice_noNpe_commissionZero | unit | PASS | 0.01 |
| 265 | com.taxi.backend.service.CommitSafeStatusTest | complete_pushThrows_stillCommitsCompleted | unit | PASS | 0.0 |
| 266 | com.taxi.backend.service.CommitSafeStatusTest | started_broadcastThrows_statusStillChanges | unit | PASS | 0.025 |
| 267 | com.taxi.backend.service.CooldownTest | acceptAfterCooldownExpired_eligible | unit | PASS | 0.004 |
| 268 | com.taxi.backend.service.CooldownTest | acceptInCooldown_operatorOrder_allowed | unit | PASS | 0.004 |
| 269 | com.taxi.backend.service.CooldownTest | acceptInCooldown_passengerOrder_rejected | unit | PASS | 0.004 |
| 270 | com.taxi.backend.service.CooldownTest | decline_setsCooldown | unit | PASS | 0.003 |
| 271 | com.taxi.backend.service.CooldownTest | getAvailableTrips_busyOverridesOperatorBypass | unit | PASS | 0.003 |
| 272 | com.taxi.backend.service.CooldownTest | getAvailableTrips_inCooldown_mixed_onlyOperator | unit | PASS | 0.004 |
| 273 | com.taxi.backend.service.CooldownTest | getAvailableTrips_inCooldown_operatorShown | unit | PASS | 0.004 |
| 274 | com.taxi.backend.service.CooldownTest | getAvailableTrips_inCooldown_operatorTaxometerShown | unit | PASS | 0.003 |
| 275 | com.taxi.backend.service.CooldownTest | getAvailableTrips_inCooldown_passengerHidden | unit | PASS | 0.005 |
| 276 | com.taxi.backend.service.CooldownTest | getAvailableTrips_notInCooldown_bothShown | unit | PASS | 0.007 |
| 277 | com.taxi.backend.service.DeclineTripTest | decline_addsExclusion_withoutStatusOrStrikeChange | unit | PASS | 0.003 |
| 278 | com.taxi.backend.service.DeclineTripTest | decline_isIdempotent | unit | PASS | 0.003 |
| 279 | com.taxi.backend.service.DriverCancelRedispatchTest | cancelAfterStarted_isRejected | ao-dispatch | PASS | 0.0 |
| 280 | com.taxi.backend.service.DriverCancelRedispatchTest | driverCancel_redispatchesToOtherDrivers | ao-dispatch | PASS | 0.011 |
| 281 | com.taxi.backend.service.DriverCancelRedispatchTest | excludedDriverCannotAcceptRedispatchedTrip | ao-dispatch | PASS | 0.002 |
| 282 | com.taxi.backend.service.DriverCancelRedispatchTest | thirdCancellation_finalizesTrip | ao-dispatch | PASS | 0.0 |
| 283 | com.taxi.backend.service.DriverCodeGenerationTest | codeFormat_expandsBeyond4Digits | unit | PASS | 0.0 |
| 284 | com.taxi.backend.service.DriverCodeGenerationTest | codeGeneration_producesSequentialUniqueValues | unit | PASS | 0.0 |
| 285 | com.taxi.backend.service.DriverCodeGenerationTest | concurrentCodeGeneration_noDuplicates | unit | PASS | 0.017 |
| 286 | com.taxi.backend.service.DriverCodeGenerationTest | migrationLogic_assignsCodesWithoutGapsOrDuplicates | unit | PASS | 0.0 |
| 287 | com.taxi.backend.service.DriverDocumentsTest | get_returnsImageUrl_whenPhotoExists | auth-kyc | PASS | 0.016 |
| 288 | com.taxi.backend.service.DriverDocumentsTest | save_persistsPassportAndBirthDate | auth-kyc | PASS | 0.0 |
| 289 | com.taxi.backend.service.DriverLocationCacheTest | getNearbyDrivers_shouldFilterByRadius | location-privacy | PASS | 0.0 |
| 290 | com.taxi.backend.service.DriverLocationCacheTest | getNearbyDrivers_shouldSortByDistance | location-privacy | PASS | 0.001 |
| 291 | com.taxi.backend.service.DriverLocationCacheTest | haversineKm_knownDistance | location-privacy | PASS | 0.0 |
| 292 | com.taxi.backend.service.DriverLocationCacheTest | onlineOffline | location-privacy | PASS | 0.0 |
| 293 | com.taxi.backend.service.DriverLocationCacheTest | saveAndGetLocation | location-privacy | PASS | 0.0 |
| 294 | com.taxi.backend.service.DriverServiceFilterTest | allRequiredEnabled_accepts | unit | PASS | 0.001 |
| 295 | com.taxi.backend.service.DriverServiceFilterTest | caseInsensitiveAndTrimmed | unit | PASS | 0.014 |
| 296 | com.taxi.backend.service.DriverServiceFilterTest | missingOneRequired_rejects | unit | PASS | 0.0 |
| 297 | com.taxi.backend.service.DriverServiceFilterTest | noOrderServices_alwaysAccepts | unit | PASS | 0.0 |
| 298 | com.taxi.backend.service.DriverServiceFilterTest | parse_normalizes | unit | PASS | 0.0 |
| 299 | com.taxi.backend.service.DriverTariffGrantTest | accepts_honorsGrant | unit | PASS | 0.0 |
| 300 | com.taxi.backend.service.DriverTariffGrantTest | accepts_withoutGrant_rejectsKomfort | unit | PASS | 0.0 |
| 301 | com.taxi.backend.service.DriverTariffGrantTest | adminGrant_addsKomfort | unit | PASS | 0.0 |
| 302 | com.taxi.backend.service.DriverTariffGrantTest | carDefault_withoutGrant_noKomfort | unit | PASS | 0.0 |
| 303 | com.taxi.backend.service.DriverTariffGrantTest | grant_caseAndSpaceInsensitive_multiple | unit | PASS | 0.0 |
| 304 | com.taxi.backend.service.DriverTariffGrantTest | grant_doesNotBreakCarDefault | unit | PASS | 0.0 |
| 305 | com.taxi.backend.service.DriverTariffGrantTest | legacyEkonom_matchesStandartOrder | unit | PASS | 0.0 |
| 306 | com.taxi.backend.service.MatchingScoreTest | closeLowActivity_beatsFarHighActivity | unit | PASS | 0.101 |
| 307 | com.taxi.backend.service.MatchingScoreTest | exactScoreAndFreeSinceTie_usesStableDriverIdOrder | unit | PASS | 0.0 |
| 308 | com.taxi.backend.service.MatchingScoreTest | farHighActivity_beatsCloserLowActivity | unit | PASS | 0.0 |
| 309 | com.taxi.backend.service.MatchingScoreTest | tieWithinEpsilon_earliestFreeSinceWins | unit | PASS | 0.006 |
| 310 | com.taxi.backend.service.OnlineClearsCooldownTest | goOnline_clearsCooldown | unit | PASS | 0.01 |
| 311 | com.taxi.backend.service.OperatorAdminServiceTest | create_hashesViaAuthService_andNoHashInResponse | unit | PASS | 0.004 |
| 312 | com.taxi.backend.service.OperatorAdminServiceTest | delete_lastAdmin_blocked | unit | PASS | 0.002 |
| 313 | com.taxi.backend.service.OperatorAdminServiceTest | delete_self_blocked | unit | PASS | 0.0 |
| 314 | com.taxi.backend.service.OperatorAdminServiceTest | list_neverReturnsHash | unit | PASS | 0.0 |
| 315 | com.taxi.backend.service.OperatorAdminServiceTest | update_demoteAdmin_okWhenOthersExist | unit | PASS | 0.0 |
| 316 | com.taxi.backend.service.OperatorAdminServiceTest | update_demoteLastAdmin_blocked | unit | PASS | 0.012 |
| 317 | com.taxi.backend.service.OperatorCancelFreesDriverTest | cancel_waivesAccruedWaiting | unit | PASS | 0.014 |
| 318 | com.taxi.backend.service.OperatorCancelFreesDriverTest | cancelAccepted_freesDriver | unit | PASS | 0.0 |
| 319 | com.taxi.backend.service.OperatorCancelFreesDriverTest | cancelTerminal_rejected | unit | PASS | 0.002 |
| 320 | com.taxi.backend.service.OperatorDispatchIgnoresCooldownTest | operatorDispatch_includesCooldown | ao-dispatch | PASS | 0.004 |
| 321 | com.taxi.backend.service.OperatorDispatchIgnoresCooldownTest | passengerDispatch_excludesCooldown | ao-dispatch | PASS | 0.003 |
| 322 | com.taxi.backend.service.OperatorEditTripTest | editAccepted_recomputesPrice | unit | PASS | 0.006 |
| 323 | com.taxi.backend.service.OperatorEditTripTest | editDriverArrived_addressBlocked | unit | PASS | 0.002 |
| 324 | com.taxi.backend.service.OperatorEditTripTest | editStarted_destAllowed | unit | PASS | 0.007 |
| 325 | com.taxi.backend.service.OperatorEditTripTest | editStarted_pickupBlocked | unit | PASS | 0.125 |
| 326 | com.taxi.backend.service.OperatorEditTripTest | editTerminal_blocked | unit | PASS | 0.0 |
| 327 | com.taxi.backend.service.OperatorFareNightTest | createTrip_tashkentBoundaries_nightAdds3000 | unit | PASS | 0.02 |
| 328 | com.taxi.backend.service.OperatorFareNightTest | editTrip_usesCreationTimeInTashkent | unit | PASS | 0.004 |
| 329 | com.taxi.backend.service.OperatorReassignServiceTest | reassign_driverNotFound_rejected | unit | PASS | 0.003 |
| 330 | com.taxi.backend.service.OperatorReassignServiceTest | reassign_negativeBalanceB_rejected | unit | PASS | 0.003 |
| 331 | com.taxi.backend.service.OperatorReassignServiceTest | reassign_nonActiveB_rejected | unit | PASS | 0.003 |
| 332 | com.taxi.backend.service.OperatorReassignServiceTest | reassign_offlineB_rejected | unit | PASS | 0.006 |
| 333 | com.taxi.backend.service.OperatorReassignServiceTest | reassign_startedTrip_rejected | unit | PASS | 0.006 |
| 334 | com.taxi.backend.service.OperatorReassignServiceTest | reassignAccepted_freesAndDispatchesB | unit | PASS | 0.004 |
| 335 | com.taxi.backend.service.OperatorServicesTest | createFixedTrip_noServices_baseOnly | unit | PASS | 0.001 |
| 336 | com.taxi.backend.service.OperatorServicesTest | createFixedTrip_withRearLuggage_addsServiceToFare | unit | PASS | 0.003 |
| 337 | com.taxi.backend.service.OperatorServicesTest | createTaxometerTrip_withServices_addsToStartingFare | unit | PASS | 0.005 |
| 338 | com.taxi.backend.service.OperatorTripCreateGuardTest | dispatchRunsOnlyAfterTheTripTransactionCommits | unit | PASS | 0.008 |
| 339 | com.taxi.backend.service.OperatorTripCreateGuardTest | existingSearchingTrip_blocksSecondOperatorCreateBeforeDispatch | unit | PASS | 0.0 |
| 340 | com.taxi.backend.service.OperatorTripCreateIdempotencyServiceTest | firstClaim_createsOneTripAndMarksDurableRecordCompleted | unit | PASS | 0.0 |
| 341 | com.taxi.backend.service.OperatorTripCreateIdempotencyServiceTest | incompleteSameKeyIsNotRetriedThroughTheCreateOrDispatchPath | unit | PASS | 0.0 |
| 342 | com.taxi.backend.service.OperatorTripCreateIdempotencyServiceTest | missingOrNonCanonicalKeyIsRejectedBeforeAnyDatabaseMutation | unit | PASS | 0.251 |
| 343 | com.taxi.backend.service.OperatorTripCreateIdempotencyServiceTest | sameKeyAndSamePayload_returnsExistingTripWithoutSecondCreateOrDispatchPath | unit | PASS | 0.0 |
| 344 | com.taxi.backend.service.OperatorTripCreateIdempotencyServiceTest | sameKeyWithChangedPayload_returnsConflictBeforeTripCreate | unit | PASS | 0.017 |
| 345 | com.taxi.backend.service.OperatorTripPostgresConcurrencyTest | scheduledTripsRemainOutsideTheImmediateTripConstraint | postgres-concurrency | PASS | 0.085 |
| 346 | com.taxi.backend.service.OperatorTripPostgresConcurrencyTest | twentyDistinctKeysForOnePassengerCreateOneImmediateTripAndConflictTheRest | postgres-concurrency | PASS | 0.272 |
| 347 | com.taxi.backend.service.OperatorTripPostgresConcurrencyTest | twentyParallelSameKeyRequestsCreateExactlyOneTripAndNineteenReplays | postgres-concurrency | PASS | 0.274 |
| 348 | com.taxi.backend.service.OperatorTripPostgresConcurrencyTest | twoParallelSameKeyRequestsCreateOneTripAndOneReplay | postgres-concurrency | PASS | 0.081 |
| 349 | com.taxi.backend.service.OrderPushDeliveryOutcomeClassifierTest | usesOnlyTypedFirebaseCodesWithConservativeCategories | unit | PASS | 0.009 |
| 350 | com.taxi.backend.service.PassengerHistoryServiceTest | shouldReturnHistoryForKnownPassenger | unit | PASS | 0.001 |
| 351 | com.taxi.backend.service.PassengerHistoryServiceTest | shouldReturnNotFoundForUnknownPassenger | unit | PASS | 0.002 |
| 352 | com.taxi.backend.service.PaymentCatalogRoutingTest | invalidCatalogSignatureCannotReachCatalogStateMachine | payment | PASS | 0.001 |
| 353 | com.taxi.backend.service.PaymentCatalogRoutingTest | signedAppOrderReferenceNeverFallsIntoTheCatalogStateMachine | payment | PASS | 0.002 |
| 354 | com.taxi.backend.service.PaymentCatalogRoutingTest | signedCatalogCompleteUsesTheSameCanonicalCallbackContract | payment | PASS | 0.002 |
| 355 | com.taxi.backend.service.PaymentCatalogRoutingTest | signedCatalogPrepareUsesCanonicalCallbackAndNeverAdvancedShopService | payment | PASS | 0.149 |
| 356 | com.taxi.backend.service.PaymentLedgerSnapshotRegressionTest | clickCreditWritesAuthoritativeLedgerSnapshots | payment | PASS | 0.078 |
| 357 | com.taxi.backend.service.PhotoServicePersistentStorageTest | uploadWritesToConfiguredAbsolutePersistentDirectory | unit | PASS | 0.015 |
| 358 | com.taxi.backend.service.PromoCodeServiceTest | applyPromo_shouldUseAtomicIncrement | unit | PASS | 0.002 |
| 359 | com.taxi.backend.service.PromoCodeServiceTest | applyPromo_whenLimitReached_shouldReturnZero | unit | PASS | 0.001 |
| 360 | com.taxi.backend.service.PromoCodeServiceTest | validate_withBlankCode_shouldReturnInvalid | unit | PASS | 0.129 |
| 361 | com.taxi.backend.service.PromoCodeServiceTest | validate_withInvalidCode_shouldReturnInvalid | unit | PASS | 0.001 |
| 362 | com.taxi.backend.service.PromoCodeServiceTest | validate_withValidCode_shouldReturnDiscount | unit | PASS | 0.002 |
| 363 | com.taxi.backend.service.PushBalanceSkipTest | broadcastPush_skipsNegativeBalance_processesZero | wallet-ledger | PASS | 0.025 |
| 364 | com.taxi.backend.service.PushOrderDataOnlyTest | orderPushToExpoIsStrictlyDataOnly | unit | PASS | 0.092 |
| 365 | com.taxi.backend.service.PushServiceSkipTest | broadcastPush_skipsDriverMissingService | unit | PASS | 0.01 |
| 366 | com.taxi.backend.service.PushTypedOrderContractTest | directFcmDisabledNeverClaimsATypedDeliveryResult | unit | PASS | 0.185 |
| 367 | com.taxi.backend.service.PushTypedOrderContractTest | typedOrderPushUsesTheExistingDataOnlyBuilderWithBoundedTransportTtl | unit | PASS | 0.072 |
| 368 | com.taxi.backend.service.ReassignPushDataOnlyTest | reassignCreatesOneSpecificDurableOfferInsteadOfBuildingItsOwnPush | unit | PASS | 0.0 |
| 369 | com.taxi.backend.service.SelfieGateTest | goingOffline_neverBlockedBySelfieCheck | auth-kyc | PASS | 0.002 |
| 370 | com.taxi.backend.service.SelfieGateTest | hasSelfie_allowsGoingOnline | auth-kyc | PASS | 0.015 |
| 371 | com.taxi.backend.service.SelfieGateTest | noSelfie_blocksGoingOnline | auth-kyc | PASS | 0.007 |
| 372 | com.taxi.backend.service.ServiceEligibilityGateTest | accept_hasService_allowed | unit | PASS | 0.006 |
| 373 | com.taxi.backend.service.ServiceEligibilityGateTest | accept_missingService_rejected | unit | PASS | 0.006 |
| 374 | com.taxi.backend.service.ServiceEligibilityGateTest | available_andLogic_missingOne_empty | unit | PASS | 0.005 |
| 375 | com.taxi.backend.service.ServiceEligibilityGateTest | available_hasService_present | unit | PASS | 0.006 |
| 376 | com.taxi.backend.service.ServiceEligibilityGateTest | available_missingService_empty | unit | PASS | 0.004 |
| 377 | com.taxi.backend.service.ServiceEligibilityGateTest | available_noServices_present | unit | PASS | 0.004 |
| 378 | com.taxi.backend.service.ServiceEligibilityGateTest | board_hasService_present | unit | PASS | 0.004 |
| 379 | com.taxi.backend.service.ServiceEligibilityGateTest | board_missingService_empty | unit | PASS | 0.005 |
| 380 | com.taxi.backend.service.ServiceEligibilityGateTest | claim_hasService_allowed | unit | PASS | 0.004 |
| 381 | com.taxi.backend.service.ServiceEligibilityGateTest | claim_missingService_rejected | unit | PASS | 0.004 |
| 382 | com.taxi.backend.service.SmsInviteServiceTest | shouldNotSendDuplicateSms | unit | PASS | 0.013 |
| 383 | com.taxi.backend.service.SmsInviteServiceTest | shouldNotSendSmsForEmptyPhone | unit | PASS | 0.0 |
| 384 | com.taxi.backend.service.SmsInviteServiceTest | shouldSendSmsForCallTrip | unit | PASS | 0.0 |
| 385 | com.taxi.backend.service.SmsInviteServiceTest | smsErrorShouldNotBreakTrip | unit | PASS | 0.016 |
| 386 | com.taxi.backend.service.StatusTransitionGuardTest | backwardStartedToArrived_rejected | unit | PASS | 0.0 |
| 387 | com.taxi.backend.service.StatusTransitionGuardTest | completeAgainOnCompleted_rejected_noNewCommission | unit | PASS | 0.015 |
| 388 | com.taxi.backend.service.StatusTransitionGuardTest | completeStartedOnce_oneCommission | unit | PASS | 0.0 |
| 389 | com.taxi.backend.service.StatusTransitionGuardTest | forwardFlow_oneCommission | unit | PASS | 0.016 |
| 390 | com.taxi.backend.service.StatusTransitionGuardTest | pickupOnCompleted_rejected | unit | PASS | 0.0 |
| 391 | com.taxi.backend.service.StatusTransitionGuardTest | startOnCompleted_rejected | unit | PASS | 0.0 |
| 392 | com.taxi.backend.service.StrictBalanceGateTest | acceptTrip_negativeBalance_rejected | wallet-ledger | PASS | 0.005 |
| 393 | com.taxi.backend.service.StrictBalanceGateTest | acceptTrip_zeroBalance_allowed | wallet-ledger | PASS | 0.001 |
| 394 | com.taxi.backend.service.StrictBalanceGateTest | getAvailableTrips_negativeBalance_empty | wallet-ledger | PASS | 0.0 |
| 395 | com.taxi.backend.service.StrictBalanceGateTest | getAvailableTrips_zeroBalance_returnsOrders | wallet-ledger | PASS | 0.016 |
| 396 | com.taxi.backend.service.SurgePricingServiceTest | calculate_highDemandLowSupply_shouldIncreaseSurge | unit | PASS | 0.004 |
| 397 | com.taxi.backend.service.SurgePricingServiceTest | calculate_noDemand_shouldReturnNormalSurge | unit | PASS | 0.004 |
| 398 | com.taxi.backend.service.SurgePricingServiceTest | calculate_shouldHaveFactors | unit | PASS | 0.003 |
| 399 | com.taxi.backend.service.SurgePricingServiceTest | calculate_shouldNeverExceedMaxSurge | unit | PASS | 0.009 |
| 400 | com.taxi.backend.service.SurgePricingServiceTest | calculate_shouldNeverGoBelowMinSurge | unit | PASS | 0.002 |
| 401 | com.taxi.backend.service.SurgePricingServiceTest | calculate_surgeIsRoundedToOneDecimal | unit | PASS | 0.004 |
| 402 | com.taxi.backend.service.SurgePricingServiceTest | calculate_zeroDrivers_shouldNotThrowDivisionByZero | unit | PASS | 0.004 |
| 403 | com.taxi.backend.service.SurgePricingServiceTest | getDemandInfo_noDrivers_shouldReturnMaxWaitTime | unit | PASS | 0.003 |
| 404 | com.taxi.backend.service.SurgePricingServiceTest | getDemandInfo_shouldReturnAllFields | unit | PASS | 0.01 |
| 405 | com.taxi.backend.service.SurgeToggleTest | calculate_surgeDisabled_returnsNeutralAndSkipsEngine | unit | PASS | 0.112 |
| 406 | com.taxi.backend.service.SurgeToggleTest | calculate_surgeEnabled_delegatesToEngine | unit | PASS | 0.002 |
| 407 | com.taxi.backend.service.SurgeToggleTest | getDemandInfo_surgeDisabled_forcesMultiplierOne | unit | PASS | 0.002 |
| 408 | com.taxi.backend.service.SurgeToggleTest | getDemandInfo_surgeEnabled_keepsEngineMultiplier | unit | PASS | 0.001 |
| 409 | com.taxi.backend.service.SurgeToggleTest | toggle_flipsBehaviour | unit | PASS | 0.002 |
| 410 | com.taxi.backend.service.SystemSettingServiceTest | getBoolean_customDefault_whenMissing | unit | PASS | 0.008 |
| 411 | com.taxi.backend.service.SystemSettingServiceTest | isSurgeEnabled_noRow_defaultsFalse | unit | PASS | 0.123 |
| 412 | com.taxi.backend.service.SystemSettingServiceTest | isSurgeEnabled_rowFalse_returnsFalse | unit | PASS | 0.001 |
| 413 | com.taxi.backend.service.SystemSettingServiceTest | isSurgeEnabled_rowTrue_returnsTrue | unit | PASS | 0.002 |
| 414 | com.taxi.backend.service.SystemSettingServiceTest | setSurgeEnabled_false_updatesExistingRow | unit | PASS | 0.002 |
| 415 | com.taxi.backend.service.SystemSettingServiceTest | setSurgeEnabled_true_persistsTrue | unit | PASS | 0.003 |
| 416 | com.taxi.backend.service.TaxometerFareTest | commission_tenPercent_oneKm | unit | PASS | 0.001 |
| 417 | com.taxi.backend.service.TaxometerFareTest | commission_tenPercent_tenKm | unit | PASS | 0.0 |
| 418 | com.taxi.backend.service.TaxometerFareTest | fare_oneKm_returnsBaseAndKm | unit | PASS | 0.001 |
| 419 | com.taxi.backend.service.TaxometerFareTest | fare_tenKm_correctTiyin | unit | PASS | 0.001 |
| 420 | com.taxi.backend.service.TaxometerFareTest | fare_twoKm_correctTiyin | unit | PASS | 0.001 |
| 421 | com.taxi.backend.service.TaxometerFareTest | fare_zeroKm_returnsBaseOnly | unit | PASS | 0.001 |
| 422 | com.taxi.backend.service.TaxometerFareTest | fullTrip_accumulateThenFare | unit | PASS | 0.001 |
| 423 | com.taxi.backend.service.TaxometerFareTest | haversine_parkentToTashkent_reasonableDistance | unit | PASS | 0.001 |
| 424 | com.taxi.backend.service.TaxometerFareTest | haversine_samePoint_returnsZero | unit | PASS | 0.01 |
| 425 | com.taxi.backend.service.TaxometerFareTest | haversine_shortDistance_lessThanOneKm | unit | PASS | 0.001 |
| 426 | com.taxi.backend.service.TaxometerFinishIdempotentTest | finishTwice_chargedOnce_secondRejected | unit | PASS | 0.0 |
| 427 | com.taxi.backend.service.TaxometerLifecycleNightTest | nightSurchargeAppliedExactlyOnce_acrossBookingAndFinish | unit | PASS | 0.015 |
| 428 | com.taxi.backend.service.TaxometerNightFareTest | finish_nightByCreationTimeInTashkent_adds3000 | unit | PASS | 0.011 |
| 429 | com.taxi.backend.service.TaxometerNightFareTest | finish_tashkentBoundaries | unit | PASS | 0.0 |
| 430 | com.taxi.backend.service.TaxometerPauseResumeTest | pause_setsWaitingStarted | unit | PASS | 0.003 |
| 431 | com.taxi.backend.service.TaxometerPauseResumeTest | resume_after3min_accumulates120k | unit | PASS | 0.003 |
| 432 | com.taxi.backend.service.TaxometerPauseResumeTest | resume_whenNotPaused_noop | unit | PASS | 0.004 |
| 433 | com.taxi.backend.service.TaxometerPauseResumeTest | shortPause_underFree_noCharge | unit | PASS | 0.002 |
| 434 | com.taxi.backend.service.TaxometerPauseResumeTest | twoPauses_accumulate | unit | PASS | 0.002 |
| 435 | com.taxi.backend.service.TaxometerWaitingFeeTest | callTaxometerStart_closesWaitingWindow | unit | PASS | 0.0 |
| 436 | com.taxi.backend.service.TaxometerWaitingFeeTest | finishIncludesServicesFeeInFareAndCommission | unit | PASS | 0.0 |
| 437 | com.taxi.backend.service.TaxometerWaitingFeeTest | finishIncludesWaitingFeeInFareAndCommission | unit | PASS | 0.016 |
| 438 | com.taxi.backend.service.TripAssignmentUtilTest | clearsAll | unit | PASS | 0.0 |
| 439 | com.taxi.backend.service.TripCompletionPushFlagTest | flagFalse_doesNotGateDriverArrivedPush | unit | PASS | 0.0 |
| 440 | com.taxi.backend.service.TripCompletionPushFlagTest | flagFalse_neitherCompletionPushFires_otherLogicStillRuns | unit | PASS | 0.231 |
| 441 | com.taxi.backend.service.TripCompletionPushFlagTest | flagTrue_bothCompletionPushesFire | unit | PASS | 0.016 |
| 442 | com.taxi.backend.service.TripCompletionPushFlagTest | flagTrue_callSourceTrip_passengerBodyIsNonRating | unit | PASS | 0.0 |
| 443 | com.taxi.backend.service.TripDriverOfferFlywayBootstrapTest | flywayBootstrapsV49WithTypedTimingColumnsAndBigintForeignKeys | migration | PASS | 2.358 |
| 444 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[1] | postgres-concurrency | PASS | 0.082 |
| 445 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[10] | postgres-concurrency | PASS | 0.079 |
| 446 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[11] | postgres-concurrency | PASS | 0.079 |
| 447 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[12] | postgres-concurrency | PASS | 0.076 |
| 448 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[13] | postgres-concurrency | PASS | 0.073 |
| 449 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[14] | postgres-concurrency | PASS | 0.082 |
| 450 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[15] | postgres-concurrency | PASS | 0.082 |
| 451 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[16] | postgres-concurrency | PASS | 0.087 |
| 452 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[17] | postgres-concurrency | PASS | 0.091 |
| 453 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[18] | postgres-concurrency | PASS | 0.089 |
| 454 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[19] | postgres-concurrency | PASS | 0.103 |
| 455 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[2] | postgres-concurrency | PASS | 0.066 |
| 456 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[20] | postgres-concurrency | PASS | 0.075 |
| 457 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[3] | postgres-concurrency | PASS | 0.083 |
| 458 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[4] | postgres-concurrency | PASS | 0.09 |
| 459 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[5] | postgres-concurrency | PASS | 0.08 |
| 460 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[6] | postgres-concurrency | PASS | 0.077 |
| 461 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[7] | postgres-concurrency | PASS | 0.107 |
| 462 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[8] | postgres-concurrency | PASS | 0.083 |
| 463 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | parallelLiveOfferInsertsAdmitExactlyOneWinner()[9] | postgres-concurrency | PASS | 0.078 |
| 464 | com.taxi.backend.service.TripDriverOfferPostgresConcurrencyTest | terminalOfferAllowsOneNextGenerationButNeverReoffersSameDriver | postgres-concurrency | PASS | 0.297 |
| 465 | com.taxi.backend.service.TripEstimateNightTest | bookTrip_night_addsExactly3000 | unit | PASS | 0.01 |
| 466 | com.taxi.backend.service.TripEstimateNightTest | estimate_nightExposesFlags | unit | PASS | 0.0 |
| 467 | com.taxi.backend.service.TripEstimateNightTest | estimate_tashkent_0000_night | unit | PASS | 0.0 |
| 468 | com.taxi.backend.service.TripEstimateNightTest | estimate_tashkent_0559_night | unit | PASS | 0.004 |
| 469 | com.taxi.backend.service.TripEstimateNightTest | estimate_tashkent_0600_day | unit | PASS | 0.0 |
| 470 | com.taxi.backend.service.TripEstimateNightTest | estimate_tashkent_2359_day | unit | PASS | 0.008 |
| 471 | com.taxi.backend.service.TripEstimateNightTest | orderIsBaseThenNightThenSurge | unit | PASS | 0.015 |
| 472 | com.taxi.backend.service.TripEstimateNightTest | passengerCreatedTrip_entersSequentialDispatcherFacade | unit | PASS | 0.0 |
| 473 | com.taxi.backend.service.TripExpirySchedulerSequentialDispatchTest | recoveryWorker_neverBroadcastsAndOnlyAdvancesThroughFacade | ao-dispatch | PASS | 0.008 |
| 474 | com.taxi.backend.service.TripExpirySchedulerSequentialDispatchTest | scheduledTrip_transitionsToSearchingThenUsesSequentialDispatcher | ao-dispatch | PASS | 0.0 |
| 475 | com.taxi.backend.service.TripNotificationHelperBusyTest | busyRankOneIsSkippedAndOnlyNextEligibleDriverGetsOffer | unit | PASS | 0.144 |
| 476 | com.taxi.backend.service.TripNotificationHelperSequentialDispatchRegressionTest | currentOwnerRejects_rankTwoBecomesTheOnlyNextOffer | ao-dispatch | PASS | 0.0 |
| 477 | com.taxi.backend.service.TripNotificationHelperSequentialDispatchRegressionTest | expiredOffer_advancesOnceToNextRankedDriver | ao-dispatch | PASS | 0.015 |
| 478 | com.taxi.backend.service.TripNotificationHelperSequentialDispatchRegressionTest | fewerThanThreeCandidates_doesNotUseOnlineFallback | ao-dispatch | PASS | 0.005 |
| 479 | com.taxi.backend.service.TripNotificationHelperSequentialDispatchRegressionTest | nonOwnerCannotAcknowledgeOrAcceptCurrentOffer | ao-dispatch | PASS | 0.0 |
| 480 | com.taxi.backend.service.TripNotificationHelperSequentialDispatchRegressionTest | restartRecoveryRetriesOnlyPersistedPendingOfferOwners | ao-dispatch | PASS | 0.0 |
| 481 | com.taxi.backend.service.TripNotificationHelperSequentialDispatchRegressionTest | sixRankedEligibleDrivers_onlyRankOneReceivesInitialOffer | ao-dispatch | PASS | 0.02 |
| 482 | com.taxi.backend.service.TripNotifyServiceFilterTest | serviceIneligibleRankOneIsSkippedAndNextEligibleDriverOwnsOffer | unit | PASS | 0.005 |
| 483 | com.taxi.backend.service.TripOfferDeliveryServiceTest | acceptedTripAtDeliveryTimeNeverSendsStaleOrder | ao-dispatch | PASS | 0.002 |
| 484 | com.taxi.backend.service.TripOfferDeliveryServiceTest | activeOfferDeliversMatchingWsAndDataOnlyPushToItsSingleOwner | ao-dispatch | PASS | 0.004 |
| 485 | com.taxi.backend.service.TripOfferDeliveryServiceTest | cancelledTripAtDeliveryTimeNeverSendsStaleOrder | ao-dispatch | PASS | 0.0 |
| 486 | com.taxi.backend.service.TripOfferDeliveryTimingTest | firstDeliveryPersistsAndPublishesOneMatchingFixedSafeDeadline | ao-dispatch | PASS | 0.009 |
| 487 | com.taxi.backend.service.TripOfferDeliveryTimingTest | unknownResultPreservesCurrentOwnerAndNeverCreatesAnotherOffer | ao-dispatch | PASS | 0.0 |
| 488 | com.taxi.backend.service.TripOfferDeliveryTimingTest | unregisteredDelegatesOnlyWhenTheFailedRecipientIsStillCurrent | ao-dispatch | PASS | 0.288 |
| 489 | com.taxi.backend.service.TripOfferPermanentFailureTransitionTest | tokenChangeDuringFlightCannotCloseOrAdvanceTheCurrentOwner | ao-dispatch | PASS | 0.016 |
| 490 | com.taxi.backend.service.TripOfferPermanentFailureTransitionTest | unregisteredTransitionsExactlyOnceAndQueuesOneNextGeneration | ao-dispatch | PASS | 0.0 |
| 491 | com.taxi.backend.service.TripOrderAckTest | blankNotifiedIds_rejected | ao-dispatch | PASS | 0.0 |
| 492 | com.taxi.backend.service.TripOrderAckTest | nonNotifiedDriver_rejected | ao-dispatch | PASS | 0.0 |
| 493 | com.taxi.backend.service.TripOrderAckTest | notifiedDriver_firstAck_stampsAndSaves | ao-dispatch | PASS | 0.0 |
| 494 | com.taxi.backend.service.TripOrderAckTest | secondAck_isIdempotent | ao-dispatch | PASS | 0.0 |
| 495 | com.taxi.backend.service.TripOrderAckTest | unknownTrip_gracefulFalse | ao-dispatch | PASS | 0.0 |
| 496 | com.taxi.backend.service.TripOwnershipGuardTest | arrive_onDriverNullTrip_throwsBusinessError | unit | PASS | 0.0 |
| 497 | com.taxi.backend.service.TripOwnershipGuardTest | arrive_onOwnAcceptedTrip_succeeds | unit | PASS | 0.009 |
| 498 | com.taxi.backend.service.TripOwnershipGuardTest | complete_onDriverNullTrip_throwsBusinessError | unit | PASS | 0.0 |
| 499 | com.taxi.backend.service.TripOwnershipGuardTest | start_onDriverNullTrip_throwsBusinessError | unit | PASS | 0.0 |
| 500 | com.taxi.backend.service.TripOwnershipGuardTest | waitingCalls_onDriverNullTrip_throwBusinessError | unit | PASS | 0.016 |
| 501 | com.taxi.backend.service.TripServiceRatingTest | shouldRejectRatingForCallTrip | unit | PASS | 0.0 |
| 502 | com.taxi.backend.service.WaitingFeeTest | arrival_setsArrivedAtAndStartsWaiting | unit | PASS | 0.006 |
| 503 | com.taxi.backend.service.WaitingFeeTest | completionCommissionIncludesServices | unit | PASS | 0.011 |
| 504 | com.taxi.backend.service.WaitingFeeTest | directStartWithoutWaiting_feeZeroNoCrash | unit | PASS | 0.016 |
| 505 | com.taxi.backend.service.WaitingFeeTest | start150sAfterArrival_fee900somIncludedInFareAndCommission | unit | PASS | 0.005 |
| 506 | com.taxi.backend.service.WaitingFeeTest | start45sAfterArrival_feeIsZero | unit | PASS | 0.0 |

Machine-readable receipt: `docs/security-containment-wave-0-skipped-test-receipt-2026-07-17.json`.

No production database, provider, payment callback, OTP, KYC, PII, balance, location, signup, deployment, or source mutation was performed.
