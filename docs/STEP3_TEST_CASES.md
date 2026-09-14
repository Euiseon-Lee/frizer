# STEP 3 Java 테스트 실행 목록

기준: 2026-09-14 마감 검증. 최종 UI 변경을 포함한 test bootJar 실행 XML에서 생성했다. 최신 범위와 제한은 [작업 정리](SESSION_SUMMARY.md)를 따른다.

## com.euiseon.friger.inventory.FoodMergeIntegrationTest

실행 17 / 실패 0 / 오류 0 / 건너뜀 0

| ID | 실행 이름 | 결과 |
| --- | --- | --- |
| JAVA-001 | sameNamesRemainSeparateUntilExplicitMerge() | 통과 |
| JAVA-002 | staleItemEditAfterMergeIsRejected() | 통과 |
| JAVA-003 | staleConfirmationAndSelfMergeAreRejected() | 통과 |
| JAVA-004 | previewDoesNotWriteAndPostSupportsSafeRetry() | 통과 |
| JAVA-005 | mergeHistoryUsesCombinedLimitAndStableTieOrder() | 통과 |
| JAVA-006 | mergeHistoryEscapesNamesAndMissingCurrentTargetFallsBackToList() | 통과 |
| JAVA-007 | chainedMergesResolveAllLinksToCurrentFood() | 통과 |
| JAVA-008 | mergeIsDisabledUntilAnotherFoodExists() | 통과 |
| JAVA-009 | renamedItemsExplainCurrentNameWithoutInventingMerge() | 통과 |
| JAVA-010 | previousMergeReceiptsBecomeVisibleWithoutBackfill() | 통과 |
| JAVA-011 | inlinePreviewKeepsChoicesEscapesNamesAndRejectsStalePost() | 통과 |
| JAVA-012 | simultaneousRetriesApplyOnlyOnce() | 통과 |
| JAVA-013 | receiptFailureRollsBackTransferAndDeletion() | 통과 |
| JAVA-014 | mergePreservesItemsHistoryAndTargetDefaults() | 통과 |
| JAVA-015 | v7PreservesExistingIdsAndDoesNotCombineMatchingNames() | 통과 |
| JAVA-016 | sharedNameEditInvalidatesOtherItemFormsAndMergePreview() | 통과 |
| JAVA-017 | mergeAppearsOnceInHistoryAndHomeWithoutRewritingItemHistory() | 통과 |

## com.euiseon.friger.inventory.InventoryIntegrationTest

실행 95 / 실패 0 / 오류 0 / 건너뜀 0

| ID | 실행 이름 | 결과 |
| --- | --- | --- |
| JAVA-018 | exportChocoHomeStatesForBrowserVerification() | 통과 |
| JAVA-019 | malformedEnumAndDatesBecomeFormErrors() | 통과 |
| JAVA-020 | fieldAndBusinessErrorsAreReportedTogetherWithoutDuplicates() | 통과 |
| JAVA-021 | editFormLoadsAllFieldsAndUsesDetailForCancel() | 통과 |
| JAVA-022 | singleMultilineChangeKeepsBeforeAndAfterSummary() | 통과 |
| JAVA-023 | actualHistoryInsertFailureRollsBackFoodInsert() | 통과 |
| JAVA-024 | addPurchaseUsesSharedIdentityAndPreservesOriginal() | 통과 |
| JAVA-025 | existingFreezerDateIsPreservedAndMissingTypeDefaultsToHomeFrozen() | 통과 |
| JAVA-026 | sourceMemoLengthIsValidatedAndInputPreserved() | 통과 |
| JAVA-027 | homeSeparatesExpiredTodayAndUnknownDatesAndFiltersStorage() | 통과 |
| JAVA-028 | historyFieldsSeparateLabelsAndKeepMultilineChanges() | 통과 |
| JAVA-029 | [1] date= | 통과 |
| JAVA-030 | [2] date=2026-09-12 | 통과 |
| JAVA-031 | [3] date=2026-09-13 | 통과 |
| JAVA-032 | [4] date=2026-09-14 | 통과 |
| JAVA-033 | unspecifiedSourceCanBeRestoredAfterExplicitEtc() | 통과 |
| JAVA-034 | [1] unit= | 통과 |
| JAVA-035 | [2] unit=    | 통과 |
| JAVA-036 | [3] unit=가나다라마바사아자차카 | 통과 |
| JAVA-037 | [1] amount=0 | 통과 |
| JAVA-038 | [2] amount=-1 | 통과 |
| JAVA-039 | [3] amount=0.001 | 통과 |
| JAVA-040 | [4] amount=6.343345 | 통과 |
| JAVA-041 | [5] amount=1000000000 | 통과 |
| JAVA-042 | [6] amount=1.2345 | 통과 |
| JAVA-043 | [7] amount=반 봉지 | 통과 |
| JAVA-044 | [8] amount=NaN | 통과 |
| JAVA-045 | [1] count=0 | 통과 |
| JAVA-046 | [2] count=1 | 통과 |
| JAVA-047 | [3] count=3 | 통과 |
| JAVA-048 | [4] count=4 | 통과 |
| JAVA-049 | [5] count=100 | 통과 |
| JAVA-050 | v5PreservesExistingFoodAndHistoryExactly() | 통과 |
| JAVA-051 | customUnitIsEscapedAndLegacyTextStillRenders() | 통과 |
| JAVA-052 | [1] sellBy=2026-09-20, useBy=null | 통과 |
| JAVA-053 | [2] sellBy=null, useBy=2026-09-21 | 통과 |
| JAVA-054 | [3] sellBy=2026-09-20, useBy=2026-09-21 | 통과 |
| JAVA-055 | [4] sellBy=null, useBy=null | 통과 |
| JAVA-056 | quantityIsRequiredButCapacityIsOptional() | 통과 |
| JAVA-057 | sourceMemoIsIgnoredUnlessOtherSourceWasExplicitlySelected() | 통과 |
| JAVA-058 | capacityAndParentsSourceRoundTripToDetail() | 통과 |
| JAVA-059 | warningPresentationHandlesDuplicateNamesTodayAndUnknownDates() | 통과 |
| JAVA-060 | missingStorageUsesNewMessageWithoutRedundantHelp() | 통과 |
| JAVA-061 | pastSellByDoesNotBecomeExpiredUseBy() | 통과 |
| JAVA-062 | registrationPersistsFoodAndSingleCreateHistoryAndRedirects() | 통과 |
| JAVA-063 | addPurchaseValidationKeepsSelectionAndDoesNotWrite() | 통과 |
| JAVA-064 | [1] opened=2026-01-31, today=2026-04-30, expected=false | 통과 |
| JAVA-065 | [2] opened=2026-01-31, today=2026-05-01, expected=true | 통과 |
| JAVA-066 | [3] opened=2025-11-30, today=2026-02-28, expected=false | 통과 |
| JAVA-067 | [4] opened=2025-11-30, today=2026-03-01, expected=true | 통과 |
| JAVA-068 | [5] opened=2023-11-30, today=2024-02-29, expected=false | 통과 |
| JAVA-069 | [6] opened=2023-11-30, today=2024-03-01, expected=true | 통과 |
| JAVA-070 | futureDatesAreRejectedByServerAndInputIsPreserved() | 통과 |
| JAVA-071 | terminalAndMissingFoodCannotBeEdited() | 통과 |
| JAVA-072 | legacyDeliveryMigratesWithoutLosingInventoryOrInventingQuantity() | 통과 |
| JAVA-073 | [1] sellBy=2026-09-01, useBy=2026-09-20, warningCount=0 | 통과 |
| JAVA-074 | [2] sellBy=2026-09-01, useBy=2026-09-13, warningCount=0 | 통과 |
| JAVA-075 | [3] sellBy=2026-09-01, useBy=2026-09-12, warningCount=1 | 통과 |
| JAVA-076 | [4] sellBy=2026-09-01, useBy=null, warningCount=1 | 통과 |
| JAVA-077 | [5] sellBy=null, useBy=2026-09-12, warningCount=1 | 통과 |
| JAVA-078 | [6] sellBy=2026-09-20, useBy=null, warningCount=0 | 통과 |
| JAVA-079 | [7] sellBy=null, useBy=null, warningCount=0 | 통과 |
| JAVA-080 | newCommercialFreezingUsesSeoulToday() | 통과 |
| JAVA-081 | registrationHistoryPreservesInitialFieldsAfterCurrentFoodChanges() | 통과 |
| JAVA-082 | invalidSellByReturnsAnErrorWithoutLosingUseBy() | 통과 |
| JAVA-083 | leftoverDefaultsToFreezerAndUnknownDateStaysUnknown() | 통과 |
| JAVA-084 | legacyQuantityRequiresExplicitConfirmationAndUpdatesWithoutGuessing() | 통과 |
| JAVA-085 | serviceRejectsInvalidNameAndMissingStorageWithoutWrites() | 통과 |
| JAVA-086 | [1] opened=null, sellBy=null, useBy=null, warnings=0 | 통과 |
| JAVA-087 | [2] opened=2026-06-14, sellBy=null, useBy=null, warnings=0 | 통과 |
| JAVA-088 | [3] opened=2026-06-13, sellBy=null, useBy=null, warnings=0 | 통과 |
| JAVA-089 | [4] opened=2026-06-12, sellBy=null, useBy=null, warnings=1 | 통과 |
| JAVA-090 | [5] opened=2026-06-12, sellBy=2026-09-01, useBy=2026-09-20, warnings=1 | 통과 |
| JAVA-091 | [6] opened=2026-06-12, sellBy=2026-09-01, useBy=2026-09-12, warnings=2 | 통과 |
| JAVA-092 | [7] opened=2026-06-12, sellBy=2026-09-01, useBy=null, warnings=2 | 통과 |
| JAVA-093 | deliveryStorageDefaultStillWorksWithOtherValidationErrors() | 통과 |
| JAVA-094 | unchangedQuantityDoesNotProduceAQuantityChange() | 통과 |
| JAVA-095 | blankRegistrationShowsRequiredErrorsOnlyInline() | 통과 |
| JAVA-096 | [1] amount=0.50, unit=봉지, display=0.5봉지 | 통과 |
| JAVA-097 | [2] amount=0.01, unit=g, display=0.01g | 통과 |
| JAVA-098 | [3] amount=999999999.99, unit=mL, display=999999999.99mL | 통과 |
| JAVA-099 | [4] amount=2.00, unit=팩, display=2팩 | 통과 |
| JAVA-100 | expiredFoodIsAllowedWithCautionAndNamesAreEscaped() | 통과 |
| JAVA-101 | updatePersistsChangesAndFullEscapedHistory() | 통과 |
| JAVA-102 | homeShowsFiveLatestCompactHistoryRows() | 통과 |
| JAVA-103 | overviewCoversAllDateCombinationsWithoutContradictoryEmptyCard() | 통과 |
| JAVA-104 | invalidAndStaleEditsPreserveDataAndSubmittedInputs() | 통과 |
| JAVA-105 | otherSourceMemoIsStoredSeparatelyAndEscapedInDetail() | 통과 |
| JAVA-106 | unchangedSaveDoesNotWriteOrDisplayModifiedTimestamp() | 통과 |
| JAVA-107 | addPurchaseRejectsDuplicateAndMissingTarget() | 통과 |
| JAVA-108 | selectingTodayOverridesAnObsoleteFrozenDate() | 통과 |
| JAVA-109 | pagesRenderWithChocoNavigation() | 통과 |
| JAVA-110 | explicitLocationWinsAndNonFreezerClearsFreezingFields() | 통과 |
| JAVA-111 | editHistoryFailureRollsBackFoodAndDates() | 통과 |
| JAVA-112 | listContainsOnlyActiveItemsInStableNewestOrder() | 통과 |

## com.euiseon.friger.inventory.Step3ScenarioIntegrationTest

실행 180 / 실패 0 / 오류 0 / 건너뜀 0

| ID | 실행 이름 | 결과 |
| --- | --- | --- |
| JAVA-113 | registrationPostRetryReturnsSuccessWithoutAnotherFoodOrHistory() | 통과 |
| JAVA-114 | repetition 1 of 3 | 통과 |
| JAVA-115 | repetition 2 of 3 | 통과 |
| JAVA-116 | repetition 3 of 3 | 통과 |
| JAVA-117 | repetition 1 of 3 | 통과 |
| JAVA-118 | repetition 2 of 3 | 통과 |
| JAVA-119 | repetition 3 of 3 | 통과 |
| JAVA-120 | [1] mode=bogus | 통과 |
| JAVA-121 | [2] mode=EXISTING | 통과 |
| JAVA-122 | [3] mode= new  | 통과 |
| JAVA-123 | [1] amount=0 | 통과 |
| JAVA-124 | [2] amount=-1 | 통과 |
| JAVA-125 | [3] amount=0.001 | 통과 |
| JAVA-126 | [4] amount=1.234 | 통과 |
| JAVA-127 | [5] amount=1000000000 | 통과 |
| JAVA-128 | [6] amount=NaN | 통과 |
| JAVA-129 | [7] amount=1e999 | 통과 |
| JAVA-130 | [8] amount= | 통과 |
| JAVA-131 | [1] mode=new, field=quantityAmount | 통과 |
| JAVA-132 | [2] mode=new, field=expiredAt | 통과 |
| JAVA-133 | [3] mode=new, field=sellByAt | 통과 |
| JAVA-134 | [4] mode=new, field=purchasedAt | 통과 |
| JAVA-135 | [5] mode=new, field=openedAt | 통과 |
| JAVA-136 | [6] mode=new, field=frozenAt | 통과 |
| JAVA-137 | [7] mode=new, field=sourceType | 통과 |
| JAVA-138 | [8] mode=new, field=storageType | 통과 |
| JAVA-139 | [9] mode=new, field=freezeType | 통과 |
| JAVA-140 | [10] mode=existing, field=quantityAmount | 통과 |
| JAVA-141 | [11] mode=existing, field=expiredAt | 통과 |
| JAVA-142 | [12] mode=existing, field=sellByAt | 통과 |
| JAVA-143 | [13] mode=existing, field=purchasedAt | 통과 |
| JAVA-144 | [14] mode=existing, field=openedAt | 통과 |
| JAVA-145 | [15] mode=existing, field=frozenAt | 통과 |
| JAVA-146 | [16] mode=existing, field=sourceType | 통과 |
| JAVA-147 | [17] mode=existing, field=storageType | 통과 |
| JAVA-148 | [18] mode=existing, field=freezeType | 통과 |
| JAVA-149 | [19] mode=edit, field=quantityAmount | 통과 |
| JAVA-150 | [20] mode=edit, field=expiredAt | 통과 |
| JAVA-151 | [21] mode=edit, field=sellByAt | 통과 |
| JAVA-152 | [22] mode=edit, field=purchasedAt | 통과 |
| JAVA-153 | [23] mode=edit, field=openedAt | 통과 |
| JAVA-154 | [24] mode=edit, field=frozenAt | 통과 |
| JAVA-155 | [25] mode=edit, field=sourceType | 통과 |
| JAVA-156 | [26] mode=edit, field=storageType | 통과 |
| JAVA-157 | [27] mode=edit, field=freezeType | 통과 |
| JAVA-158 | [1] source=null, storage=null, today=false | 통과 |
| JAVA-159 | [2] source=null, storage=null, today=true | 통과 |
| JAVA-160 | [3] source=null, storage=FRIDGE, today=false | 통과 |
| JAVA-161 | [4] source=null, storage=FRIDGE, today=true | 통과 |
| JAVA-162 | [5] source=null, storage=ROOM, today=false | 통과 |
| JAVA-163 | [6] source=null, storage=ROOM, today=true | 통과 |
| JAVA-164 | [7] source=null, storage=FREEZER, today=false | 통과 |
| JAVA-165 | [8] source=null, storage=FREEZER, today=true | 통과 |
| JAVA-166 | [9] source=PURCHASE, storage=null, today=false | 통과 |
| JAVA-167 | [10] source=PURCHASE, storage=null, today=true | 통과 |
| JAVA-168 | [11] source=PURCHASE, storage=FRIDGE, today=false | 통과 |
| JAVA-169 | [12] source=PURCHASE, storage=FRIDGE, today=true | 통과 |
| JAVA-170 | [13] source=PURCHASE, storage=ROOM, today=false | 통과 |
| JAVA-171 | [14] source=PURCHASE, storage=ROOM, today=true | 통과 |
| JAVA-172 | [15] source=PURCHASE, storage=FREEZER, today=false | 통과 |
| JAVA-173 | [16] source=PURCHASE, storage=FREEZER, today=true | 통과 |
| JAVA-174 | [17] source=DELIVERY_LEFTOVER, storage=null, today=false | 통과 |
| JAVA-175 | [18] source=DELIVERY_LEFTOVER, storage=null, today=true | 통과 |
| JAVA-176 | [19] source=DELIVERY_LEFTOVER, storage=FRIDGE, today=false | 통과 |
| JAVA-177 | [20] source=DELIVERY_LEFTOVER, storage=FRIDGE, today=true | 통과 |
| JAVA-178 | [21] source=DELIVERY_LEFTOVER, storage=ROOM, today=false | 통과 |
| JAVA-179 | [22] source=DELIVERY_LEFTOVER, storage=ROOM, today=true | 통과 |
| JAVA-180 | [23] source=DELIVERY_LEFTOVER, storage=FREEZER, today=false | 통과 |
| JAVA-181 | [24] source=DELIVERY_LEFTOVER, storage=FREEZER, today=true | 통과 |
| JAVA-182 | [25] source=COOKED, storage=null, today=false | 통과 |
| JAVA-183 | [26] source=COOKED, storage=null, today=true | 통과 |
| JAVA-184 | [27] source=COOKED, storage=FRIDGE, today=false | 통과 |
| JAVA-185 | [28] source=COOKED, storage=FRIDGE, today=true | 통과 |
| JAVA-186 | [29] source=COOKED, storage=ROOM, today=false | 통과 |
| JAVA-187 | [30] source=COOKED, storage=ROOM, today=true | 통과 |
| JAVA-188 | [31] source=COOKED, storage=FREEZER, today=false | 통과 |
| JAVA-189 | [32] source=COOKED, storage=FREEZER, today=true | 통과 |
| JAVA-190 | [33] source=PARENTS, storage=null, today=false | 통과 |
| JAVA-191 | [34] source=PARENTS, storage=null, today=true | 통과 |
| JAVA-192 | [35] source=PARENTS, storage=FRIDGE, today=false | 통과 |
| JAVA-193 | [36] source=PARENTS, storage=FRIDGE, today=true | 통과 |
| JAVA-194 | [37] source=PARENTS, storage=ROOM, today=false | 통과 |
| JAVA-195 | [38] source=PARENTS, storage=ROOM, today=true | 통과 |
| JAVA-196 | [39] source=PARENTS, storage=FREEZER, today=false | 통과 |
| JAVA-197 | [40] source=PARENTS, storage=FREEZER, today=true | 통과 |
| JAVA-198 | [41] source=ETC, storage=null, today=false | 통과 |
| JAVA-199 | [42] source=ETC, storage=null, today=true | 통과 |
| JAVA-200 | [43] source=ETC, storage=FRIDGE, today=false | 통과 |
| JAVA-201 | [44] source=ETC, storage=FRIDGE, today=true | 통과 |
| JAVA-202 | [45] source=ETC, storage=ROOM, today=false | 통과 |
| JAVA-203 | [46] source=ETC, storage=ROOM, today=true | 통과 |
| JAVA-204 | [47] source=ETC, storage=FREEZER, today=false | 통과 |
| JAVA-205 | [48] source=ETC, storage=FREEZER, today=true | 통과 |
| JAVA-206 | registrationValidationErrorPreservesTokenForCorrectedSubmission() | 통과 |
| JAVA-207 | chainedMergeRetryDoesNotRecreateDeletedFoods() | 통과 |
| JAVA-208 | v8PreservesExplicitEtcAndRejectsMemoWithoutSource() | 통과 |
| JAVA-209 | additionalHistoryFailureRollsBackAllTables() | 통과 |
| JAVA-210 | [1] field=targetId | 통과 |
| JAVA-211 | [2] field=sourceVersion | 통과 |
| JAVA-212 | [3] field=targetVersion | 통과 |
| JAVA-213 | [4] field=requestId | 통과 |
| JAVA-214 | newRegistrationWithDifferentTokensKeepsSameNamedFoodsSeparate() | 통과 |
| JAVA-215 | repetition 1 of 3 | 통과 |
| JAVA-216 | repetition 2 of 3 | 통과 |
| JAVA-217 | repetition 3 of 3 | 통과 |
| JAVA-218 | [1] addToTarget=false | 통과 |
| JAVA-219 | [2] addToTarget=true | 통과 |
| JAVA-220 | registrationCompletionFailureRollsBackFoodHistoryAndClaim() | 통과 |
| JAVA-221 | [1] url=/foods/-1, status=404 | 통과 |
| JAVA-222 | [2] url=/foods/nope, status=400 | 통과 |
| JAVA-223 | [3] url=/foods/-1/merge, status=404 | 통과 |
| JAVA-224 | [4] url=/inventory/-1, status=404 | 통과 |
| JAVA-225 | [5] url=/inventory/nope, status=400 | 통과 |
| JAVA-226 | [6] url=/inventory/-1/edit, status=404 | 통과 |
| JAVA-227 | [7] url=/inventory/new?masterId=-1, status=404 | 통과 |
| JAVA-228 | [8] url=/inventory/new?masterId=nope, status=400 | 통과 |
| JAVA-229 | [9] url=/inventory?storage=NOPE, status=400 | 통과 |
| JAVA-230 | mergeRejectsMissingMastersNullTokenAndChangedRequestContent() | 통과 |
| JAVA-231 | sharedIdentityEditFailureRollsBackSiblingTimestampsAndMaster() | 통과 |
| JAVA-232 | sequentialConflictsRequireReselectionAndFreshVersions() | 통과 |
| JAVA-233 | [1] mode=new, field=foodName, length=99, allowed=true | 통과 |
| JAVA-234 | [2] mode=new, field=foodName, length=100, allowed=true | 통과 |
| JAVA-235 | [3] mode=new, field=foodName, length=101, allowed=false | 통과 |
| JAVA-236 | [4] mode=new, field=category, length=49, allowed=true | 통과 |
| JAVA-237 | [5] mode=new, field=category, length=50, allowed=true | 통과 |
| JAVA-238 | [6] mode=new, field=category, length=51, allowed=false | 통과 |
| JAVA-239 | [7] mode=new, field=memo, length=499, allowed=true | 통과 |
| JAVA-240 | [8] mode=new, field=memo, length=500, allowed=true | 통과 |
| JAVA-241 | [9] mode=new, field=memo, length=501, allowed=false | 통과 |
| JAVA-242 | [10] mode=new, field=capacityText, length=49, allowed=true | 통과 |
| JAVA-243 | [11] mode=new, field=capacityText, length=50, allowed=true | 통과 |
| JAVA-244 | [12] mode=new, field=capacityText, length=51, allowed=false | 통과 |
| JAVA-245 | [13] mode=new, field=sourceMemo, length=199, allowed=true | 통과 |
| JAVA-246 | [14] mode=new, field=sourceMemo, length=200, allowed=true | 통과 |
| JAVA-247 | [15] mode=new, field=sourceMemo, length=201, allowed=false | 통과 |
| JAVA-248 | [16] mode=new, field=quantityUnit, length=9, allowed=true | 통과 |
| JAVA-249 | [17] mode=new, field=quantityUnit, length=10, allowed=true | 통과 |
| JAVA-250 | [18] mode=new, field=quantityUnit, length=11, allowed=false | 통과 |
| JAVA-251 | [19] mode=existing, field=foodName, length=99, allowed=true | 통과 |
| JAVA-252 | [20] mode=existing, field=foodName, length=100, allowed=true | 통과 |
| JAVA-253 | [21] mode=existing, field=foodName, length=101, allowed=false | 통과 |
| JAVA-254 | [22] mode=existing, field=category, length=49, allowed=true | 통과 |
| JAVA-255 | [23] mode=existing, field=category, length=50, allowed=true | 통과 |
| JAVA-256 | [24] mode=existing, field=category, length=51, allowed=false | 통과 |
| JAVA-257 | [25] mode=existing, field=memo, length=499, allowed=true | 통과 |
| JAVA-258 | [26] mode=existing, field=memo, length=500, allowed=true | 통과 |
| JAVA-259 | [27] mode=existing, field=memo, length=501, allowed=false | 통과 |
| JAVA-260 | [28] mode=existing, field=capacityText, length=49, allowed=true | 통과 |
| JAVA-261 | [29] mode=existing, field=capacityText, length=50, allowed=true | 통과 |
| JAVA-262 | [30] mode=existing, field=capacityText, length=51, allowed=false | 통과 |
| JAVA-263 | [31] mode=existing, field=sourceMemo, length=199, allowed=true | 통과 |
| JAVA-264 | [32] mode=existing, field=sourceMemo, length=200, allowed=true | 통과 |
| JAVA-265 | [33] mode=existing, field=sourceMemo, length=201, allowed=false | 통과 |
| JAVA-266 | [34] mode=existing, field=quantityUnit, length=9, allowed=true | 통과 |
| JAVA-267 | [35] mode=existing, field=quantityUnit, length=10, allowed=true | 통과 |
| JAVA-268 | [36] mode=existing, field=quantityUnit, length=11, allowed=false | 통과 |
| JAVA-269 | sameRegistrationTokenWithDifferentContentIsRejected() | 통과 |
| JAVA-270 | registrationFailureRollsBackClaimAndAllowsSameTokenRetry() | 통과 |
| JAVA-271 | originalRegistrationRetryAfterEditAndMergeDoesNotRestoreOldState() | 통과 |
| JAVA-272 | repetition 1 of 3 | 통과 |
| JAVA-273 | repetition 2 of 3 | 통과 |
| JAVA-274 | repetition 3 of 3 | 통과 |
| JAVA-275 | repetition 1 of 3 | 통과 |
| JAVA-276 | repetition 2 of 3 | 통과 |
| JAVA-277 | repetition 3 of 3 | 통과 |
| JAVA-278 | competingMergesIntoSameTargetRejectStaleLoser() | 통과 |
| JAVA-279 | [1] editTarget=false | 통과 |
| JAVA-280 | [2] editTarget=true | 통과 |
| JAVA-281 | mixedUnitsLocationsTerminalStatesAndLegacyQuantitiesSurviveMerge() | 통과 |
| JAVA-282 | registrationWithoutTokenCannotWriteAndFormProvidesRecoveryToken() | 통과 |
| JAVA-283 | [1] field=purchasedAt, mode=new | 통과 |
| JAVA-284 | [2] field=openedAt, mode=new | 통과 |
| JAVA-285 | [3] field=frozenAt, mode=new | 통과 |
| JAVA-286 | [4] field=purchasedAt, mode=existing | 통과 |
| JAVA-287 | [5] field=openedAt, mode=existing | 통과 |
| JAVA-288 | [6] field=frozenAt, mode=existing | 통과 |
| JAVA-289 | [7] field=purchasedAt, mode=edit | 통과 |
| JAVA-290 | [8] field=openedAt, mode=edit | 통과 |
| JAVA-291 | [9] field=frozenAt, mode=edit | 통과 |
| JAVA-292 | newRegistrationPagesHaveIndependentTokens() | 통과 |

## com.euiseon.friger.smoke.DatabaseSmokeTest

실행 97 / 실패 0 / 오류 0 / 건너뜀 0

| ID | 실행 이름 | 결과 |
| --- | --- | --- |
| JAVA-293 | contextPostgresMigrationAndXmlMapperAreReady() | 통과 |
| JAVA-294 | rejectsUnknownSourceType() | 통과 |
| JAVA-295 | rejectsOrphanHistory() | 통과 |
| JAVA-296 | [1] source=PURCHASE | 통과 |
| JAVA-297 | [2] source=DELIVERY_LEFTOVER | 통과 |
| JAVA-298 | [3] source=COOKED | 통과 |
| JAVA-299 | [4] source=PARENTS | 통과 |
| JAVA-300 | [5] source=ETC | 통과 |
| JAVA-301 | foreignKeyPreventsDeletingAnItemWithHistory() | 통과 |
| JAVA-302 | requiredIndexesArePresent() | 통과 |
| JAVA-303 | FRIDGE/NONE/date=null/status=ACTIVE: allowed=true | 통과 |
| JAVA-304 | FRIDGE/NONE/date=null/status=CONSUMED: allowed=true | 통과 |
| JAVA-305 | FRIDGE/NONE/date=null/status=DISCARDED: allowed=true | 통과 |
| JAVA-306 | FRIDGE/NONE/date=2026-09-01/status=ACTIVE: allowed=false | 통과 |
| JAVA-307 | FRIDGE/NONE/date=2026-09-01/status=CONSUMED: allowed=false | 통과 |
| JAVA-308 | FRIDGE/NONE/date=2026-09-01/status=DISCARDED: allowed=false | 통과 |
| JAVA-309 | FRIDGE/HOME_FROZEN/date=null/status=ACTIVE: allowed=false | 통과 |
| JAVA-310 | FRIDGE/HOME_FROZEN/date=null/status=CONSUMED: allowed=false | 통과 |
| JAVA-311 | FRIDGE/HOME_FROZEN/date=null/status=DISCARDED: allowed=false | 통과 |
| JAVA-312 | FRIDGE/HOME_FROZEN/date=2026-09-01/status=ACTIVE: allowed=false | 통과 |
| JAVA-313 | FRIDGE/HOME_FROZEN/date=2026-09-01/status=CONSUMED: allowed=false | 통과 |
| JAVA-314 | FRIDGE/HOME_FROZEN/date=2026-09-01/status=DISCARDED: allowed=false | 통과 |
| JAVA-315 | FRIDGE/COMMERCIAL_FROZEN/date=null/status=ACTIVE: allowed=false | 통과 |
| JAVA-316 | FRIDGE/COMMERCIAL_FROZEN/date=null/status=CONSUMED: allowed=false | 통과 |
| JAVA-317 | FRIDGE/COMMERCIAL_FROZEN/date=null/status=DISCARDED: allowed=false | 통과 |
| JAVA-318 | FRIDGE/COMMERCIAL_FROZEN/date=2026-09-01/status=ACTIVE: allowed=false | 통과 |
| JAVA-319 | FRIDGE/COMMERCIAL_FROZEN/date=2026-09-01/status=CONSUMED: allowed=false | 통과 |
| JAVA-320 | FRIDGE/COMMERCIAL_FROZEN/date=2026-09-01/status=DISCARDED: allowed=false | 통과 |
| JAVA-321 | FREEZER/NONE/date=null/status=ACTIVE: allowed=false | 통과 |
| JAVA-322 | FREEZER/NONE/date=null/status=CONSUMED: allowed=false | 통과 |
| JAVA-323 | FREEZER/NONE/date=null/status=DISCARDED: allowed=false | 통과 |
| JAVA-324 | FREEZER/NONE/date=2026-09-01/status=ACTIVE: allowed=false | 통과 |
| JAVA-325 | FREEZER/NONE/date=2026-09-01/status=CONSUMED: allowed=false | 통과 |
| JAVA-326 | FREEZER/NONE/date=2026-09-01/status=DISCARDED: allowed=false | 통과 |
| JAVA-327 | FREEZER/HOME_FROZEN/date=null/status=ACTIVE: allowed=true | 통과 |
| JAVA-328 | FREEZER/HOME_FROZEN/date=null/status=CONSUMED: allowed=true | 통과 |
| JAVA-329 | FREEZER/HOME_FROZEN/date=null/status=DISCARDED: allowed=true | 통과 |
| JAVA-330 | FREEZER/HOME_FROZEN/date=2026-09-01/status=ACTIVE: allowed=true | 통과 |
| JAVA-331 | FREEZER/HOME_FROZEN/date=2026-09-01/status=CONSUMED: allowed=true | 통과 |
| JAVA-332 | FREEZER/HOME_FROZEN/date=2026-09-01/status=DISCARDED: allowed=true | 통과 |
| JAVA-333 | FREEZER/COMMERCIAL_FROZEN/date=null/status=ACTIVE: allowed=true | 통과 |
| JAVA-334 | FREEZER/COMMERCIAL_FROZEN/date=null/status=CONSUMED: allowed=true | 통과 |
| JAVA-335 | FREEZER/COMMERCIAL_FROZEN/date=null/status=DISCARDED: allowed=true | 통과 |
| JAVA-336 | FREEZER/COMMERCIAL_FROZEN/date=2026-09-01/status=ACTIVE: allowed=true | 통과 |
| JAVA-337 | FREEZER/COMMERCIAL_FROZEN/date=2026-09-01/status=CONSUMED: allowed=true | 통과 |
| JAVA-338 | FREEZER/COMMERCIAL_FROZEN/date=2026-09-01/status=DISCARDED: allowed=true | 통과 |
| JAVA-339 | ROOM/NONE/date=null/status=ACTIVE: allowed=true | 통과 |
| JAVA-340 | ROOM/NONE/date=null/status=CONSUMED: allowed=true | 통과 |
| JAVA-341 | ROOM/NONE/date=null/status=DISCARDED: allowed=true | 통과 |
| JAVA-342 | ROOM/NONE/date=2026-09-01/status=ACTIVE: allowed=false | 통과 |
| JAVA-343 | ROOM/NONE/date=2026-09-01/status=CONSUMED: allowed=false | 통과 |
| JAVA-344 | ROOM/NONE/date=2026-09-01/status=DISCARDED: allowed=false | 통과 |
| JAVA-345 | ROOM/HOME_FROZEN/date=null/status=ACTIVE: allowed=false | 통과 |
| JAVA-346 | ROOM/HOME_FROZEN/date=null/status=CONSUMED: allowed=false | 통과 |
| JAVA-347 | ROOM/HOME_FROZEN/date=null/status=DISCARDED: allowed=false | 통과 |
| JAVA-348 | ROOM/HOME_FROZEN/date=2026-09-01/status=ACTIVE: allowed=false | 통과 |
| JAVA-349 | ROOM/HOME_FROZEN/date=2026-09-01/status=CONSUMED: allowed=false | 통과 |
| JAVA-350 | ROOM/HOME_FROZEN/date=2026-09-01/status=DISCARDED: allowed=false | 통과 |
| JAVA-351 | ROOM/COMMERCIAL_FROZEN/date=null/status=ACTIVE: allowed=false | 통과 |
| JAVA-352 | ROOM/COMMERCIAL_FROZEN/date=null/status=CONSUMED: allowed=false | 통과 |
| JAVA-353 | ROOM/COMMERCIAL_FROZEN/date=null/status=DISCARDED: allowed=false | 통과 |
| JAVA-354 | ROOM/COMMERCIAL_FROZEN/date=2026-09-01/status=ACTIVE: allowed=false | 통과 |
| JAVA-355 | ROOM/COMMERCIAL_FROZEN/date=2026-09-01/status=CONSUMED: allowed=false | 통과 |
| JAVA-356 | ROOM/COMMERCIAL_FROZEN/date=2026-09-01/status=DISCARDED: allowed=false | 통과 |
| JAVA-357 | [1] name=Food, storage=INVALID, freeze=NONE, status=ACTIVE | 통과 |
| JAVA-358 | [2] name=Food, storage=FRIDGE, freeze=INVALID, status=ACTIVE | 통과 |
| JAVA-359 | [3] name=Food, storage=FRIDGE, freeze=NONE, status=INVALID | 통과 |
| JAVA-360 | [4] name=null, storage=FRIDGE, freeze=NONE, status=ACTIVE | 통과 |
| JAVA-361 | [5] name=Food, storage=null, freeze=NONE, status=ACTIVE | 통과 |
| JAVA-362 | [6] name=Food, storage=FRIDGE, freeze=null, status=ACTIVE | 통과 |
| JAVA-363 | [7] name=Food, storage=FRIDGE, freeze=NONE, status=null | 통과 |
| JAVA-364 | [8] name=, storage=FRIDGE, freeze=NONE, status=ACTIVE | 통과 |
| JAVA-365 | [1] action=INVALID, previous=null, next=FRIDGE | 통과 |
| JAVA-366 | [2] action=CREATE, previous=FRIDGE, next=FRIDGE | 통과 |
| JAVA-367 | [3] action=CREATE, previous=null, next=null | 통과 |
| JAVA-368 | [4] action=CREATE, previous=null, next=INVALID | 통과 |
| JAVA-369 | [5] action=MOVE, previous=INVALID, next=FRIDGE | 통과 |
| JAVA-370 | [6] action=MOVE, previous=FRIDGE, next=FRIDGE | 통과 |
| JAVA-371 | [7] action=MOVE, previous=FRIDGE, next=FREEZER | 통과 |
| JAVA-372 | [8] action=MOVE, previous=null, next=FRIDGE | 통과 |
| JAVA-373 | [9] action=FREEZE, previous=FREEZER, next=FREEZER | 통과 |
| JAVA-374 | [10] action=FREEZE, previous=null, next=FREEZER | 통과 |
| JAVA-375 | [11] action=CONSUME, previous=null, next=FRIDGE | 통과 |
| JAVA-376 | [12] action=DISCARD, previous=FRIDGE, next=ROOM | 통과 |
| JAVA-377 | [13] action=null, previous=null, next=FRIDGE | 통과 |
| JAVA-378 | [1] action=CREATE, previous=null, next=FRIDGE | 통과 |
| JAVA-379 | [2] action=CREATE, previous=null, next=FREEZER | 통과 |
| JAVA-380 | [3] action=CREATE, previous=null, next=ROOM | 통과 |
| JAVA-381 | [4] action=FREEZE, previous=FRIDGE, next=FREEZER | 통과 |
| JAVA-382 | [5] action=FREEZE, previous=ROOM, next=FREEZER | 통과 |
| JAVA-383 | [6] action=MOVE, previous=FREEZER, next=FRIDGE | 통과 |
| JAVA-384 | [7] action=MOVE, previous=FREEZER, next=ROOM | 통과 |
| JAVA-385 | [8] action=MOVE, previous=FRIDGE, next=ROOM | 통과 |
| JAVA-386 | [9] action=MOVE, previous=ROOM, next=FRIDGE | 통과 |
| JAVA-387 | [10] action=CONSUME, previous=FREEZER, next=FREEZER | 통과 |
| JAVA-388 | [11] action=DISCARD, previous=FRIDGE, next=FRIDGE | 통과 |
| JAVA-389 | duplicateNamesAndNullableOptionalFieldsAreAllowedWithDefaults() | 통과 |
