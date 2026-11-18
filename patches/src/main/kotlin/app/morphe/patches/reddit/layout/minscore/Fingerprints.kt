/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.minscore

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.newInstance

private object CellGroupMapperParentFingerprint : Fingerprint(
    strings = listOf("error parsing cell group ")
)

/**
 * Maps a GraphQL feed edge (cell group) to a feed element. Returns null for cell groups it cannot parse.
 */
internal object CellGroupMapperFingerprint : Fingerprint(
    classFingerprint = CellGroupMapperParentFingerprint,
    returnType = "L",
    parameters = listOf("L", "L", "L"),
    strings = listOf("Instance ID cannot be blank")
)

internal object CellGroupToStringFingerprint : Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    strings = listOf("CellGroupFragment(adPayload=")
)

internal object CellToStringFingerprint : Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    strings = listOf(
        ", actionCellFragment=",
        ", metadataCellFragment=",
        ", pinnedPostsHeaderCellFragment="
    )
)

internal object ActionCellToStringFingerprint : Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    strings = listOf("ActionCellFragment(id=")
)

internal object MetadataCellToStringFingerprint : Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    strings = listOf("MetadataCellFragment(id=")
)

internal object EdgeFragmentContextToStringFingerprint : Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    strings = listOf("EdgeFragmentContext(dataSourceType=")
)

internal object FetchSubredditParamsToStringFingerprint : Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    strings = listOf("FetchSubredditParams(after=")
)

/**
 * Fetches a page of a subreddit feed and maps each cell group with [CellGroupMapperFingerprint].
 */
internal object FetchSubredditFeedFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        newInstance($$"Lcom/reddit/screens/listing/compose/usecase/FetchSubredditFeedUseCase$fetchSubredditFeed$1;")
    )
)
