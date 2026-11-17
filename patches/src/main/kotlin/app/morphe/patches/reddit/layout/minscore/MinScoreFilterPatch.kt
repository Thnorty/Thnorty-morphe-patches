/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.minscore

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.reddit.misc.settings.settingsPatch
import app.morphe.patches.reddit.misc.version.versionCheckPatch
import app.morphe.patches.reddit.shared.Constants.COMPATIBILITY_REDDIT
import app.morphe.util.setExtensionIsPatchIncluded
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.Reference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/reddit/patches/MinScoreFilterPatch;"
private const val FEED_EDGE_INTERFACE =
    $$"Lapp/morphe/extension/reddit/patches/MinScoreFilterPatch$FeedEdgeAccess;"
private const val CELL_GROUP_INTERFACE =
    $$"Lapp/morphe/extension/reddit/patches/MinScoreFilterPatch$CellGroupAccess;"
private const val CELL_INTERFACE =
    $$"Lapp/morphe/extension/reddit/patches/MinScoreFilterPatch$CellAccess;"
private const val ACTION_CELL_INTERFACE =
    $$"Lapp/morphe/extension/reddit/patches/MinScoreFilterPatch$ActionCellAccess;"
private const val METADATA_CELL_INTERFACE =
    $$"Lapp/morphe/extension/reddit/patches/MinScoreFilterPatch$MetadataCellAccess;"
private const val FEED_CONTEXT_INTERFACE =
    $$"Lapp/morphe/extension/reddit/patches/MinScoreFilterPatch$FeedContextAccess;"
private const val SUBREDDIT_FEED_PARAMS_INTERFACE =
    $$"Lapp/morphe/extension/reddit/patches/MinScoreFilterPatch$SubredditFeedParamsAccess;"

/**
 * The single field of this class with the given type.
 */
private fun ClassDef.fieldOfType(type: String): FieldReference =
    fields.singleOrNull { it.type == type }
        ?: throw PatchException("Expected exactly one field of type $type in ${this.type}")

/**
 * The reference of the instruction, if it has one of this type.
 * Uses dexlib2 directly, since the patcher's helper moved between patcher versions.
 */
private inline fun <reified T : Reference> Instruction.reference(): T? =
    (this as? ReferenceInstruction)?.reference as? T

private fun registerWidth(type: CharSequence): Int = if (type.toString() in setOf("J", "D")) 2 else 1

/**
 * The field assigned from constructor parameter [index] (0 based, excluding 'this').
 * GraphQL fragment classes assign each constructor parameter to a field, in schema order.
 */
private fun ClassDef.fieldFromConstructorParameter(index: Int, expectedType: String): FieldReference {
    // Prefer the primary constructor over Kotlin's default arguments constructor (extra mask parameters).
    val constructor = methods.filter { method ->
        method.name == "<init>" &&
                method.parameterTypes.size > index &&
                method.parameterTypes.none { it == "Lkotlin/jvm/internal/DefaultConstructorMarker;" }
    }.minByOrNull { it.parameterTypes.size }
        ?: throw PatchException("No constructor with parameter $index in $type")

    val implementation = constructor.implementation!!
    val parameterRegisterCount = 1 + constructor.parameterTypes.sumOf(::registerWidth)
    val thisRegister = implementation.registerCount - parameterRegisterCount
    val parameterRegister = thisRegister + 1 + constructor.parameterTypes.take(index).sumOf(::registerWidth)

    // Registers above v15 are first copied with 'move-object/from16' since 'iput' only addresses v0-v15.
    val thisRegisters = mutableSetOf(thisRegister)
    val parameterRegisters = mutableSetOf(parameterRegister)

    val field = implementation.instructions.firstNotNullOfOrNull { instruction ->
        if (instruction !is TwoRegisterInstruction) return@firstNotNullOfOrNull null
        val opcodeName = instruction.opcode.name

        when {
            opcodeName.startsWith("move") && !opcodeName.startsWith("move-result") -> {
                thisRegisters.remove(instruction.registerA)
                parameterRegisters.remove(instruction.registerA)
                if (instruction.registerB in thisRegisters) thisRegisters.add(instruction.registerA)
                if (instruction.registerB in parameterRegisters) parameterRegisters.add(instruction.registerA)
                null
            }
            opcodeName.startsWith("iput") && instruction.registerA in parameterRegisters &&
                    instruction.registerB in thisRegisters -> instruction.reference<FieldReference>()
            else -> null
        }
    } ?: throw PatchException("Constructor parameter $index is not assigned to a field in $type")

    if (field.type != expectedType) {
        throw PatchException("Constructor parameter $index of $type has type ${field.type}, expected $expectedType")
    }
    return field
}

private fun MutableClass.addGetter(methodName: String, field: FieldReference, returnType: String) {
    val (getOpcode, returnOpcode) = when (returnType) {
        "I" -> "iget" to "return"
        "Z" -> "iget-boolean" to "return"
        else -> "iget-object" to "return-object"
    }
    methods.add(
        ImmutableMethod(
            type,
            methodName,
            listOf(),
            returnType,
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null,
            null,
            MutableMethodImplementation(2),
        ).toMutable().apply {
            addInstructions(0, "$getOpcode v0, p0, $field\n$returnOpcode v0")
        }
    )
}

@Suppress("unused")
val minScoreFilterPatch = bytecodePatch(
    name = "Hide low score posts",
    description = "Adds an option to hide posts in subreddit feeds with a score below a minimum set per subreddit."
) {
    compatibleWith(COMPATIBILITY_REDDIT)

    dependsOn(settingsPatch, versionCheckPatch)

    execute {
        val mapperMethod = CellGroupMapperFingerprint.method
        val cellGroupClass = CellGroupToStringFingerprint.classDef
        val feedEdgeClass = mutableClassDefBy(mapperMethod.parameterTypes.first().toString())

        // region Accessors for the obfuscated GraphQL classes.

        feedEdgeClass.interfaces.add(FEED_EDGE_INTERFACE)
        feedEdgeClass.addGetter(
            "patch_getCellGroup",
            feedEdgeClass.fieldOfType(cellGroupClass.type),
            "Ljava/lang/Object;"
        )

        cellGroupClass.interfaces.add(CELL_GROUP_INTERFACE)
        cellGroupClass.addGetter(
            "patch_getAdPayload",
            cellGroupClass.fieldFromConstructorParameter(
                0,
                cellGroupClass.methods.first { it.name == "<init>" }.parameterTypes.first().toString()
            ),
            "Ljava/lang/Object;"
        )
        cellGroupClass.addGetter(
            "patch_getCells",
            cellGroupClass.fieldOfType("Ljava/util/List;"),
            "Ljava/util/List;"
        )

        val actionCellClass = ActionCellToStringFingerprint.classDef
        val metadataCellClass = MetadataCellToStringFingerprint.classDef

        CellToStringFingerprint.classDef.apply {
            interfaces.add(CELL_INTERFACE)
            addGetter("patch_getActionCell", fieldOfType(actionCellClass.type), "Ljava/lang/Object;")
            addGetter("patch_getMetadataCell", fieldOfType(metadataCellClass.type), "Ljava/lang/Object;")
        }

        // ActionCellFragment(id, isScoreHidden, isModeratable, commentCount, score, ...)
        actionCellClass.apply {
            interfaces.add(ACTION_CELL_INTERFACE)
            addGetter("patch_getScore", fieldFromConstructorParameter(4, "I"), "I")
            addGetter("patch_isScoreHidden", fieldFromConstructorParameter(1, "Z"), "Z")
        }

        // MetadataCellFragment(id, publisherAttribution, createdAt, authorName, color, detailsString,
        // detailsLink, iconPath, ...,
        // statusIndicators (index 15), ...)
        metadataCellClass.apply {
            interfaces.add(METADATA_CELL_INTERFACE)
            addGetter("patch_getDetailsString", fieldFromConstructorParameter(5, "Ljava/lang/String;"), "Ljava/lang/String;")
            addGetter("patch_getIconPath", fieldFromConstructorParameter(7, "Ljava/lang/String;"), "Ljava/lang/String;")
            addGetter("patch_getStatusIndicators", fieldFromConstructorParameter(15, "Ljava/util/ArrayList;"), "Ljava/util/List;")
        }

        // FetchSubredditParams(after, subredditName, ...)
        val subredditFeedParamsClass = FetchSubredditParamsToStringFingerprint.classDef
        subredditFeedParamsClass.apply {
            interfaces.add(SUBREDDIT_FEED_PARAMS_INTERFACE)
            addGetter("patch_getSubredditName", fieldFromConstructorParameter(1, "Ljava/lang/String;"), "Ljava/lang/String;")
        }

        // endregion

        // region Posts in a subreddit feed show the author instead of the subreddit.
        // Make the subreddit of the feed available while its cell groups are mapped.

        // EdgeFragmentContext(dataSourceType, fetchedAt, feedType, feedLayout)
        val edgeFragmentContextClass = EdgeFragmentContextToStringFingerprint.classDef
        edgeFragmentContextClass.apply {
            interfaces.add(FEED_CONTEXT_INTERFACE)
            addGetter("patch_getFeedType", fieldFromConstructorParameter(2, "Lcom/reddit/feeds/data/FeedType;"), "Ljava/lang/Object;")
        }
        val edgeFragmentContextType = edgeFragmentContextClass.type
        FetchSubredditFeedFingerprint.method.apply {
            val mapIndex = instructions.indexOfFirst { instruction ->
                instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                        instruction.reference<MethodReference>()?.parameterTypes?.lastOrNull()
                            ?.toString() == edgeFragmentContextType
            }
            if (mapIndex < 0) throw PatchException("Could not find cell group mapping call")

            val paramsRegister = (mapIndex - 1 downTo 0).firstNotNullOfOrNull { index ->
                val instruction = instructions[index]
                if (instruction.opcode == Opcode.IGET_OBJECT &&
                    instruction.reference<FieldReference>()?.definingClass == subredditFeedParamsClass.type
                ) {
                    (instruction as TwoRegisterInstruction).registerB
                } else {
                    null
                }
            } ?: throw PatchException("Could not find subreddit feed params register")

            // Clear after the move-result of the mapping call.
            addInstructions(
                mapIndex + 2,
                "invoke-static { }, $EXTENSION_CLASS->clearSubredditFeed()V"
            )
            addInstructions(
                mapIndex,
                "invoke-static/range { v$paramsRegister .. v$paramsRegister }, " +
                        "$EXTENSION_CLASS->setSubredditFeed(Ljava/lang/Object;)V"
            )
        }

        // endregion

        mapperMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static/range { p1 .. p1 }, $EXTENSION_CLASS->recordSeenSubreddits(Ljava/lang/Object;)V
                invoke-static/range { p3 .. p3 }, $EXTENSION_CLASS->isFilteredFeed(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :show
                invoke-static/range { p1 .. p1 }, $EXTENSION_CLASS->shouldHide(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :show
                const/4 v0, 0x0
                return-object v0
                :show
                nop
            """
        )

        setExtensionIsPatchIncluded(EXTENSION_CLASS)
    }
}
