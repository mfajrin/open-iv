package com.example.core

import java.io.File

object SampleWorkspaceGenerator {

    fun ensureSandboxInitialized(workspaceRoot: File, forceReset: Boolean = false): File {
        val sandboxDir = File(workspaceRoot, "gtav_sandbox")
        val marker = File(sandboxDir, ".initialized_v1")
        if (marker.exists() && !forceReset) {
            return sandboxDir
        }

        if (forceReset && sandboxDir.exists()) {
            sandboxDir.deleteRecursively()
        }

        val updateDir = File(sandboxDir, "update").apply { mkdirs() }
        val modsDir = File(sandboxDir, "mods").apply { mkdirs() }
        val scriptsDir = File(sandboxDir, "scripts").apply { mkdirs() }

        // 1. Build real update/update.rpf
        val handlingMeta = buildDefaultHandlingMeta()
        val dlcListXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <SMandatoryPacksData>
              <Paths>
                <Item>platform:/dlcPacks/mpbeach/</Item>
                <Item>platform:/dlcPacks/mpheist/</Item>
                <Item>platform:/dlcPacks/mpluxe/</Item>
                <Item>platform:/dlcPacks/mpimportexport/</Item>
                <Item>dlcpacks:/ls_customs_pack/</Item>
              </Paths>
            </SMandatoryPacksData>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val vehiclesMeta = """
            <?xml version="1.0" encoding="UTF-8"?>
            <CVehicleModelInfo__InitDataList>
              <residentTxd>vehshare</residentTxd>
              <InitDatas>
                <Item>
                  <modelName>adder</modelName>
                  <txdName>adder</txdName>
                  <handlingId>ADDER</handlingId>
                  <gameName>ADDER</gameName>
                  <vehicleMakeName>TRUFFADE</vehicleMakeName>
                  <vehicleClass>VC_SUPER</vehicleClass>
                  <flags>FLAG_SPORTS FLAG_NO_RESPRAY</flags>
                </Item>
                <Item>
                  <modelName>sultanrs</modelName>
                  <txdName>sultanrs</txdName>
                  <handlingId>SULTANRS</handlingId>
                  <gameName>SULTANRS</gameName>
                  <vehicleMakeName>KARIN</vehicleMakeName>
                  <vehicleClass>VC_SUPER</vehicleClass>
                  <flags>FLAG_SPORTS FLAG_HAS_LIVERY</flags>
                </Item>
              </InitDatas>
            </CVehicleModelInfo__InitDataList>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val gameConfigXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <CGameConfig>
              <PoolSizes>
                <PoolName>Building</PoolName>
                <PoolSize value="48000" />
                <PoolName>Vehicle</PoolName>
                <PoolSize value="300" />
                <PoolName>Ped</PoolName>
                <PoolSize value="256" />
              </PoolSizes>
              <BuildVersion>1.0.3274.0</BuildVersion>
            </CGameConfig>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val vehShareDds = DdsAndYtdDecoder.createProceduralDds(128, 128, 0xFF00E676.toInt())
        val vehShareYtd = DdsAndYtdDecoder.createRsc7YtdFromDds(vehShareDds)

        val updateTree = MutableRpfItem.Folder("").apply {
            children.add(
                MutableRpfItem.Folder("common").apply {
                    children.add(
                        MutableRpfItem.Folder("data").apply {
                            children.add(MutableRpfItem.Blob("handling.meta", handlingMeta))
                            children.add(MutableRpfItem.Blob("dlclist.xml", dlcListXml))
                            children.add(MutableRpfItem.Blob("gameconfig.xml", gameConfigXml))
                            children.add(
                                MutableRpfItem.Folder("levels").apply {
                                    children.add(
                                        MutableRpfItem.Folder("gta5").apply {
                                            children.add(MutableRpfItem.Blob("vehicles.meta", vehiclesMeta))
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
            children.add(
                MutableRpfItem.Folder("x64").apply {
                    children.add(
                        MutableRpfItem.Folder("textures").apply {
                            children.add(
                                MutableRpfItem.Blob(
                                    name = "vehshare.ytd",
                                    data = vehShareYtd,
                                    isResource = true,
                                    systemFlags = 0x00010002,
                                    graphicsFlags = 0x00040005,
                                    compressBinary = false
                                )
                            )
                        }
                    )
                }
            )
            children.add(
                MutableRpfItem.Blob(
                    "readme_workspace.txt",
                    "OpenV Studio GTA V Sandbox Archive\nFormat: RPF7 (OPEN)\nAnda dapat mengaktifkan Edit Mode untuk mengedit handling.meta, mengganti tekstur, atau memasang paket .OIV.".toByteArray(Charsets.UTF_8)
                )
            )
        }

        File(updateDir, "update.rpf").writeBytes(Rpf7Engine.buildRpf7Bytes(updateTree))

        // 2. Build real x64a.rpf with DDS texture + RSC7 drawable stub
        val hudDds = DdsAndYtdDecoder.createProceduralDds(128, 128, 0xFF29B6F6.toInt())
        val hudMapDds = DdsAndYtdDecoder.createProceduralDds(96, 96, 0xFFFFB300.toInt())
        val ydrStub = DdsAndYtdDecoder.createRsc7YtdFromDds(hudDds)

        val x64aTree = MutableRpfItem.Folder("").apply {
            children.add(
                MutableRpfItem.Folder("textures").apply {
                    children.add(MutableRpfItem.Blob("minimap_radar.dds", hudDds, compressBinary = false))
                    children.add(MutableRpfItem.Blob("hud_reticle.dds", hudMapDds, compressBinary = false))
                }
            )
            children.add(
                MutableRpfItem.Folder("models").apply {
                    children.add(
                        MutableRpfItem.Blob(
                            name = "prop_ls_vault.ydr",
                            data = ydrStub,
                            isResource = true,
                            systemFlags = 0x00020001,
                            graphicsFlags = 0x00080003,
                            compressBinary = false
                        )
                    )
                }
            )
            children.add(
                MutableRpfItem.Blob(
                    "version.txt",
                    "GTA V x64a Archive Container\nBuild 3274\n".toByteArray(Charsets.UTF_8)
                )
            )
        }
        File(sandboxDir, "x64a.rpf").writeBytes(Rpf7Engine.buildRpf7Bytes(x64aTree))

        // 3. Build a real .oiv Mod Package in mods/LS_Sport_Handling.oiv
        val moddedHandling = buildSportModHandlingMeta()
        val assemblyXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package version="2.1" id="com.openv.ls_sport_handling" target="Five">
              <metadata>
                <name>LS Sport &amp; Drift Handling Pack</name>
                <version>
                  <major>1</major>
                  <minor>4</minor>
                </version>
                <author>
                  <displayName>Los Santos Tuning Lab</displayName>
                </author>
                <description>Meningkatkan responsivitas akselerasi, traksi tikungan, dan rem untuk Truffade Adder &amp; Karin Sultan RS di update.rpf.</description>
                <largeDescription>Paket OIV ini mengganti common/data/handling.meta di dalam update/update.rpf dengan kurva torsi sport dan pembagian AWD 35/65.</largeDescription>
              </metadata>
              <colors>
                <headerBackground useBlackTextColor="false">#FF00E676</headerBackground>
              </colors>
              <content>
                <archive path="update/update.rpf" type="RPF7">
                  <replace source="content/handling.meta">common/data/handling.meta</replace>
                </archive>
              </content>
            </package>
        """.trimIndent()

        val oivBytes = OivPackageEngine.buildOivArchive(
            assemblyXml = assemblyXml,
            contentFiles = mapOf(
                "content/handling.meta" to moddedHandling
            )
        )
        File(modsDir, "LS_Sport_Handling.oiv").writeBytes(oivBytes)

        // 4. Plain script config file
        File(scriptsDir, "ScriptHookV.ini").writeText(
            """
            [ScriptHookV]
            EnableConsole=true
            ReloadKey=F4
            MaxScripts=16
            """.trimIndent()
        )

        marker.writeText("ok")
        return sandboxDir
    }

    private fun buildDefaultHandlingMeta(): ByteArray = """
        <?xml version="1.0" encoding="UTF-8"?>
        <CHandlingDataMgr>
          <HandlingData>
            <Item type="CHandlingData">
              <handlingName>ADDER</handlingName>
              <fMass value="1800.000000" />
              <fInitialDragCoeff value="8.500000" />
              <fDriveBiasFront value="0.200000" />
              <nInitialDriveGears value="6" />
              <fInitialDriveForce value="0.340000" />
              <fInitialDriveMaxFlatVel value="160.000000" />
              <fBrakeForce value="0.900000" />
              <fTractionCurveMax value="2.450000" />
              <fTractionCurveMin value="2.100000" />
              <fSuspensionForce value="2.200000" />
            </Item>
            <Item type="CHandlingData">
              <handlingName>SULTANRS</handlingName>
              <fMass value="1350.000000" />
              <fInitialDragCoeff value="7.800000" />
              <fDriveBiasFront value="0.300000" />
              <nInitialDriveGears value="6" />
              <fInitialDriveForce value="0.310000" />
              <fInitialDriveMaxFlatVel value="148.000000" />
              <fBrakeForce value="0.850000" />
              <fTractionCurveMax value="2.350000" />
              <fTractionCurveMin value="1.950000" />
              <fSuspensionForce value="2.100000" />
            </Item>
          </HandlingData>
        </CHandlingDataMgr>
    """.trimIndent().toByteArray(Charsets.UTF_8)

    private fun buildSportModHandlingMeta(): ByteArray = """
        <?xml version="1.0" encoding="UTF-8"?>
        <CHandlingDataMgr>
          <HandlingData>
            <Item type="CHandlingData">
              <handlingName>ADDER</handlingName>
              <fMass value="1620.000000" />
              <fInitialDragCoeff value="6.200000" />
              <fDriveBiasFront value="0.350000" />
              <nInitialDriveGears value="7" />
              <fInitialDriveForce value="0.460000" />
              <fInitialDriveMaxFlatVel value="185.000000" />
              <fBrakeForce value="1.250000" />
              <fTractionCurveMax value="2.850000" />
              <fTractionCurveMin value="2.400000" />
              <fSuspensionForce value="2.500000" />
            </Item>
            <Item type="CHandlingData">
              <handlingName>SULTANRS</handlingName>
              <fMass value="1240.000000" />
              <fInitialDragCoeff value="6.500000" />
              <fDriveBiasFront value="0.350000" />
              <nInitialDriveGears value="6" />
              <fInitialDriveForce value="0.410000" />
              <fInitialDriveMaxFlatVel value="172.000000" />
              <fBrakeForce value="1.150000" />
              <fTractionCurveMax value="2.750000" />
              <fTractionCurveMin value="2.300000" />
              <fSuspensionForce value="2.400000" />
            </Item>
          </HandlingData>
        </CHandlingDataMgr>
    """.trimIndent().toByteArray(Charsets.UTF_8)
}
