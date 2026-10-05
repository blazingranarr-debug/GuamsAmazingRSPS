package org.rsmod.content.other.commands

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import kotlin.math.max
import kotlin.math.min
import org.rsmod.annotations.InternalApi
import org.rsmod.api.invtx.invClear
import org.rsmod.api.player.output.MiscOutput
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.type.symbols.name.NameMapping
import org.rsmod.api.utils.system.SafeServiceExit
import org.rsmod.content.interfaces.gameframe.worldmap.FullscreenWorldMap
import org.rsmod.content.other.commands.godmode.AdminGodMode
import org.rsmod.content.other.commands.godmode.toggleGodMode
import org.rsmod.content.other.commands.instance.AdminInstances
import org.rsmod.content.other.commands.portal.AdminPortals
import org.rsmod.content.other.commands.ui.AdminToolUi
import org.rsmod.game.GameUpdate
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.game.type.spot.SpotanimTypeList
import org.rsmod.game.type.stat.StatType
import org.rsmod.game.type.stat.StatTypeList
import org.rsmod.game.type.varbit.VarBitTypeList
import org.rsmod.game.type.varp.VarpTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.loc.LocLayerConstants

class AdminCommands
@Inject
internal constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val playerList: PlayerList,
    private val tools: AdminTools,
    private val fullscreenWorldMap: FullscreenWorldMap,
    private val instances: AdminInstances,
    private val portals: AdminPortals,
    private val godModes: AdminGodMode,
    private val adminToolUi: AdminToolUi,
    private val statTypes: StatTypeList,
    private val seqTypes: SeqTypeList,
    private val spotTypes: SpotanimTypeList,
    private val locTypes: LocTypeList,
    private val npcTypes: NpcTypeList,
    private val varpTypes: VarpTypeList,
    private val varBitTypes: VarBitTypeList,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val names: NameMapping,
    private val update: GameUpdate,
) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        onCommand("master", "Max out all stats", ::master)
        onCommand("reset", "Reset all stats", ::reset)
        onCommand("mypos", "Get current coordinates", ::mypos)
        onCommand("tele", "Teleport to coordgrid", ::tele) {
            invalidArgs = "Use as ::tele level mx mz lx lz (ex: 0 50 50 0 0)"
        }
        onCommand("telezone", "Teleport to zone key", ::teleZone) {
            invalidArgs = "Use as ::telezone zoneX zoneY level (ex: 400 400 0)"
        }
        onCommand("anim", "Play animation", ::anim)
        onCommand("spot", "Play spotanim", ::spotanim) {
            invalidArgs = "Use as ::spot spotanimDebugNameOrId (ex: fx_emote_party01_active)"
        }
        onCommand("locadd", "Spawn loc", ::locAdd) {
            invalidArgs = "Use as ::locadd duration locDebugNameOrId (ex: 100 bookcase)"
        }
        onCommand("locdel", "Remove loc", ::locDel) { invalidArgs = "Use as ::locdel duration" }
        onCommand("npcadd", "Spawn npc", ::npcAdd) {
            invalidArgs = "Use as ::npcadd duration npcDebugNameOrId (ex: 100 prison_pete)"
        }
        onCommand("invadd", "Spawn obj into inv", ::invAdd)
        onCommand("item", "Spawn obj into inv", ::invAdd) {
            invalidArgs = "Use as ::item objDebugNameOrId [count] (ex: ::item coins 1000)"
        }
        onCommand("findnpc", "List where an npc can be found", ::findNpc) {
            invalidArgs = "Use as ::findnpc npcNameOrId (ex: ::findnpc hans)"
        }
        onCommand("telenpc", "Teleport to an npc", ::teleNpc) {
            invalidArgs = "Use as ::telenpc npcNameOrId [index] (ex: ::telenpc man 2)"
        }
        onCommand("npc", "Spawn a permanent npc", ::npcSpawn) {
            invalidArgs = "Use as ::npc npcDebugNameOrId (ex: ::npc goblin)"
        }
        onCommand("delnpc", "Delete the nearest npc", ::npcDelete) {
            invalidArgs = "Use as ::delnpc [radius] (ex: ::delnpc 3)"
        }
        onCommand("loc", "Spawn a permanent loc", ::locSpawn) {
            invalidArgs = "Use as ::loc locDebugNameOrId [angle] [shape] (ex: ::loc bookcase)"
        }
        onCommand("delloc", "Delete the nearest loc", ::locDelete) {
            invalidArgs = "Use as ::delloc [radius] (ex: ::delloc 1)"
        }
        onCommand("adminwand", "Spawn the admin wand into inv", ::adminWand)
        onCommand("worldmap", "Open the fullscreen teleport world map", ::worldMap)
        onCommand("instances", "List admin instances", ::listInstances)
        onCommand("godmode", "Toggle godmode (unkillable, 99 stats)", ::godMode)
        onCommand("admintool", "Open the admin tool window", ::adminTool)
        onCommand("invclear", "Remove all objs from inv", ::invClear)
        onCommand("varp", "Set varp value", ::setVarp) {
            invalidArgs = "Use as ::varp debugNameOrId value (ex: option_run 1)"
        }
        onCommand("varbit", "Set varbit value", ::setVarBit) {
            invalidArgs = "Use as ::varbit debugNameOrId value (ex: emote_hotline_bling 1)"
        }
        onCommand("reboot", "Reboots the game world, applying packed changes", ::reboot)
        onCommand("slowreboot", "Reboots the game world, with a timer", ::slowReboot)
    }

    private fun master(cheat: Cheat) = with(cheat) { player.setStatLevels(level = 99) }

    private fun reset(cheat: Cheat) = with(cheat) { player.setStatLevels(level = 1) }

    private fun mypos(cheat: Cheat) =
        with(cheat) {
            player.mes("${player.coords}:")
            player.mes("  ${ZoneKey.from(player.coords)} - ${ZoneGrid.from(player.coords)}")
            player.mes(
                "  ${MapSquareKey.from(player.coords)} - ${MapSquareGrid.from(player.coords)}"
            )
            player.mes("  BuildArea(${player.buildArea})")
        }

    private fun tele(cheat: Cheat) =
        with(cheat) {
            val args = if (args.size == 1) args[0].split(",") else args
            val level = args[0].toInt()
            val mx = args[1].toInt()
            val mz = args[2].toInt()
            val lx = args.getOrNull(3)?.toInt() ?: 0
            val lz = args.getOrNull(4)?.toInt() ?: 0
            val coords = CoordGrid(level, mx, mz, lx, lz)
            protectedAccess.launch(player) {
                player.mes("Teleported to $coords.")
                telejump(coords)
            }
        }

    private fun teleZone(cheat: Cheat) =
        with(cheat) {
            val args = if (args.size == 1) args[0].split(",") else args
            val zoneX = args[0].toInt()
            val zoneZ = args[1].toInt()
            val level = args[2].toInt()
            val coords = ZoneKey(zoneX, zoneZ, level).toCoords()
            protectedAccess.launch(player) {
                player.mes("Teleported to $coords.")
                telejump(coords)
            }
        }

    private fun anim(cheat: Cheat) =
        with(cheat) {
            val resolvedName = resolveTypeName(args.asTypeName(), names.seqs)
            val typeId = resolveArgTypeId(resolvedName, names.seqs)
            if (typeId == null) {
                player.mes("There is no seq mapped to: '$resolvedName'")
                return
            }
            val type = seqTypes[typeId]
            if (type == null) {
                player.mes("That seq does not exist: $typeId")
                return
            }
            player.anim(type)
            player.mes("Anim: '${type.internalName}' (priority=${type.priority})")
            logger.debug { "Anim: $type" }
        }

    private fun spotanim(cheat: Cheat) =
        with(cheat) {
            val (typeName, heightArg) = args.asTypeNameAndNumber(defaultNumber = 0)
            val resolvedName = resolveTypeName(typeName, names.spotanims)
            val typeId = resolveArgTypeId(resolvedName, names.spotanims)
            if (typeId == null) {
                player.mes("There is no spotanim mapped to: '$resolvedName'")
                return
            }
            val type = spotTypes[typeId]
            if (type == null) {
                player.mes("That spotanim does not exist: $typeId")
                return
            }
            val height = min(heightArg.toInt(), Short.MAX_VALUE.toInt())
            player.spotanim(type, delay = 0, height = height, slot = 0)
            player.mes("Spotanim: '${type.internalName}' (height=$height)")
            logger.debug { "Spotanim: $type" }
        }

    private fun locAdd(cheat: Cheat) =
        with(cheat) {
            val resolvedName = resolveTypeName(args[1], names.locs)
            val typeId = resolveArgTypeId(resolvedName, names.locs)
            if (typeId == null) {
                player.mes("There is no loc mapped to name: '$resolvedName'")
                return
            }
            val type = locTypes[typeId]
            if (type == null) {
                player.mes("That loc does not exist: $typeId")
                return
            }
            val duration = args[0].toInt()
            val angle = args.getOrNull(2)?.toInt() ?: LocAngle.West.id
            val shape = args.getOrNull(3)?.toInt() ?: LocShape.CentrepieceStraight.id
            val layer = LocLayerConstants.of(shape)
            val loc = LocInfo(layer, player.coords, LocEntity(type.id, shape, angle))
            locRepo.add(loc, duration)
            player.mes("Spawned loc '${type.internalName}' (duration: $duration cycles)")
            logger.debug { "Spawned loc: loc=$loc, type=$type" }
        }

    private fun locDel(cheat: Cheat) =
        with(cheat) {
            val zone = ZoneKey.from(player.coords)
            val locs = locRepo.findAll(zone).filter { it.coords == player.coords }.toList()
            if (locs.isEmpty()) {
                player.mes("No loc found on ${player.coords}")
                return
            }
            val duration = args[0].toInt()
            val shape = args.getOrNull(1)?.toIntOrNull() ?: LocShape.CentrepieceStraight.id
            val loc = locs.firstOrNull { it.shapeId == shape }
            if (loc == null) {
                player.mes("No loc with shape `${LocShape[shape]}` found on ${player.coords}")
                return
            }
            val type = locTypes[loc]
            locRepo.del(loc, duration)
            player.mes("Deleted loc `${type.internalName}` (duration: $duration cycles)")
            logger.debug { "Deleted loc: loc=$loc, type=$type" }
        }

    private fun npcAdd(cheat: Cheat) =
        with(cheat) {
            val resolvedName = resolveTypeName(args[1], names.npcs)
            val typeId = resolveArgTypeId(resolvedName, names.npcs)
            if (typeId == null) {
                player.mes("There is no npc mapped to name: '$resolvedName'")
                return
            }
            val type = npcTypes[typeId]
            if (type == null) {
                player.mes("That npc does not exist: $typeId")
                return
            }
            val duration = args[0].toInt()
            val npc = Npc(type, player.coords)
            npc.mode = NpcMode.None
            npcRepo.add(npc, duration)
            player.mes("Spawned npc `${type.internalName}` (duration: $duration cycles)")
        }

    private fun invAdd(cheat: Cheat) =
        with(cheat) {
            val (typeName, countArg) = args.asTypeNameAndNumber(defaultNumber = 1)
            val count = countArg.toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            tools.spawnItem(player, typeName, count)
        }

    private fun findNpc(cheat: Cheat) = with(cheat) { tools.listNpcs(player, args.asTypeName()) }

    private fun teleNpc(cheat: Cheat) {
        with(cheat) {
            val hasIndex = args.size > 1 && args.last().toIntOrNull() != null
            val query = (if (hasIndex) args.dropLast(1) else args).asTypeName()
            val index = if (hasIndex) args.last().toInt() else 1
            val coords = tools.resolveNpcTeleport(player, query, index) ?: return
            protectedAccess.launch(player) { telejump(coords) }
        }
    }

    private fun npcSpawn(cheat: Cheat) = with(cheat) { tools.spawnNpc(player, args.asTypeName()) }

    private fun npcDelete(cheat: Cheat) =
        with(cheat) {
            val radius = args.getOrNull(0)?.toIntOrNull() ?: AdminTools.DEFAULT_DELETE_RADIUS
            tools.deleteNearestNpc(player, radius)
        }

    private fun locSpawn(cheat: Cheat) =
        with(cheat) {
            val trailingNumbers = args.drop(1).takeLastWhile { it.toIntOrNull() != null }.take(2)
            val nameArgs = args.dropLast(trailingNumbers.size)
            val angle = trailingNumbers.getOrNull(0)?.toInt() ?: LocAngle.West.id
            val shape = trailingNumbers.getOrNull(1)?.toInt() ?: LocShape.CentrepieceStraight.id
            tools.spawnLoc(player, nameArgs.asTypeName(), angle, shape)
        }

    private fun locDelete(cheat: Cheat) =
        with(cheat) {
            val radius = args.getOrNull(0)?.toIntOrNull() ?: AdminTools.DEFAULT_DELETE_RADIUS
            tools.deleteNearestLoc(player, radius)
        }

    private fun adminWand(cheat: Cheat) = with(cheat) { tools.spawnAdminWand(player) }

    private fun worldMap(cheat: Cheat) = with(cheat) { fullscreenWorldMap.open(player) }

    private fun godMode(cheat: Cheat) = with(cheat) { toggleGodMode(player, godModes) }

    private fun adminTool(cheat: Cheat) {
        protectedAccess.launch(cheat.player) { with(adminToolUi) { open() } }
    }

    private fun listInstances(cheat: Cheat) =
        with(cheat) {
            val all = instances.all
            if (all.isEmpty()) {
                player.mes("There are no admin instances.")
                return
            }
            player.mes("Admin instances (${all.size}):")
            for (instance in all) {
                val record = instance.record
                player.mes(
                    "  #${record.id} '${record.name}' by ${record.createdBy}: " +
                        "${record.npcs.size} npc(s), ${record.locs.size} object(s), " +
                        "${record.deletedLocs.size} removed, " +
                        "${portals.all.count { it.record.destinationInstanceId == record.id }} " +
                        "portal(s) leading here"
                )
            }
        }

    private fun invClear(cheat: Cheat) = with(cheat) { player.invClear(player.inv) }

    private fun setVarp(cheat: Cheat) =
        with(cheat) {
            val resolvedName = resolveTypeName(args[0], names.varps)
            val typeId = resolveArgTypeId(resolvedName, names.varps)
            if (typeId == null) {
                player.mes("There is no varp mapped to name: '$resolvedName'")
                return
            }
            val type = varpTypes[typeId]
            if (type == null) {
                player.mes("That varp does not exist: $typeId")
                return
            }
            val value = args[1].toInt()
            player.vars.backing[type.id] = value
            player.resyncVar(type)
            player.mes("Set varp '${type.internalName}' to value: ${player.vars[type]}")
        }

    private fun setVarBit(cheat: Cheat) =
        with(cheat) {
            val resolvedName = resolveTypeName(args[0], names.varbits)
            val typeId = resolveArgTypeId(resolvedName, names.varbits)
            if (typeId == null) {
                player.mes("There is no varbit mapped to name: '$resolvedName'")
                return
            }
            val type = varBitTypes[typeId]
            if (type == null) {
                player.mes("That varbit does not exist: $typeId")
                return
            }
            val value = args[1].toInt()
            VarPlayerIntMapSetter.set(player, type, value)
            player.mes("Set varbit '${type.internalName}' to value: ${player.vars[type]}")
        }

    @OptIn(InternalApi::class)
    private fun Player.setStatLevels(level: Int) {
        val xp = PlayerSkillXPTable.getXPFromLevel(level)
        for (stat in statTypes.values) {
            val baseLevel = statMap.getBaseLevel(stat)
            val targetLevel = max(stat.minLevel, level)
            if (baseLevel > targetLevel) {
                statRevert(stat, targetLevel, xp)
                continue
            }
            val xpDelta = xp - statMap.getXP(stat)
            statMap.setCurrentLevel(stat, targetLevel.toByte())
            statAdvance(stat, xpDelta.toDouble(), rate = 1.0)
        }
    }

    // There is, by design, no helper function to decrease stat xp, as xp reduction is not a
    // standard operation in normal gameplay.
    @OptIn(InternalApi::class)
    private fun Player.statRevert(stat: StatType, targetLevel: Int, targetXp: Int) {
        statMap.setCurrentLevel(stat, statMap.getBaseLevel(stat))
        val levelDelta = stat(stat) - targetLevel
        require(levelDelta > 0) { "This function can only be used to reduce stat levels." }
        statMap.setXP(stat, targetXp)
        statMap.setBaseLevel(stat, targetLevel.toByte())
        statSub(stat, constant = levelDelta, percent = 0)
        appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(this)
        PlayerInterfaceUpdates.updateCombatLevel(this)
    }

    private fun reboot(cheat: Cheat) {
        logger.info { "Reboot initiated by '${cheat.player.username}'." }
        SafeServiceExit.terminate()
    }

    private fun slowReboot(cheat: Cheat) =
        with(cheat) {
            val cycles = min(args[0].toInt(), 65535)
            if (cycles <= 0) {
                update.clear()
                return@with
            }
            update.startCountdown(cycles)
            for (p in playerList) {
                MiscOutput.updateRebootTimer(p, cycles)
            }
        }

    private fun List<String>.asTypeNameAndNumber(defaultNumber: Number): Pair<String, String> =
        if (size > 1 && last().toLongOrNull() != null) {
            dropLast(1).joinToString("_") to last()
        } else {
            joinToString("_") to defaultNumber.toString()
        }

    private fun List<String>.asTypeName(): String = joinToString("_")
}
